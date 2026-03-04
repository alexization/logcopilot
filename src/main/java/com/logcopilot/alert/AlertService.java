package com.logcopilot.alert;

import com.logcopilot.common.error.NotFoundException;
import com.logcopilot.common.error.ValidationException;
import com.logcopilot.project.ProjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AlertService {

	private static final int DEFAULT_LIMIT = 50;
	private static final int MAX_LIMIT = 200;
	private static final double DEFAULT_MIN_CONFIDENCE = 0.45d;
	private static final int DEFAULT_MAX_AUDIT_LOGS_PER_PROJECT = 5_000;
	private static final Duration DEFAULT_ALERT_COOLDOWN = Duration.ofMinutes(5);
	private static final int DEFAULT_ALERT_RATE_BUDGET = 30;
	private static final Duration DEFAULT_ALERT_RATE_WINDOW = Duration.ofHours(1);
	private static final boolean DEFAULT_QUIET_HOURS_ENABLED = false;
	private static final LocalTime DEFAULT_QUIET_HOURS_START = LocalTime.of(23, 0);
	private static final LocalTime DEFAULT_QUIET_HOURS_END = LocalTime.of(7, 0);
	private static final ZoneId DEFAULT_QUIET_HOURS_ZONE = ZoneId.of("UTC");
	private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

	private final ProjectService projectService;
	private final int maxAuditLogsPerProject;
	private final AlertStormPolicy alertStormPolicy;
	private final Clock clock;
	private final Map<String, Map<String, AlertChannelState>> channelsByProject = new HashMap<>();
	private final Map<String, List<AuditLogState>> auditLogsByProject = new HashMap<>();
	private final Map<String, Instant> lastDispatchAtByProject = new HashMap<>();
	private final Map<String, DispatchBudgetState> dispatchBudgetByProject = new HashMap<>();

	public AlertService(ProjectService projectService) {
		this(projectService, DEFAULT_MAX_AUDIT_LOGS_PER_PROJECT);
	}

	AlertService(ProjectService projectService, int maxAuditLogsPerProject) {
		this(
			projectService,
			maxAuditLogsPerProject,
			new AlertStormPolicy(
				DEFAULT_ALERT_COOLDOWN,
				DEFAULT_ALERT_RATE_BUDGET,
				DEFAULT_ALERT_RATE_WINDOW,
				DEFAULT_QUIET_HOURS_ENABLED,
				DEFAULT_QUIET_HOURS_START,
				DEFAULT_QUIET_HOURS_END,
				DEFAULT_QUIET_HOURS_ZONE
			),
			Clock.systemUTC()
		);
	}

	@Autowired
	public AlertService(
		ProjectService projectService,
		@Value("${logcopilot.alert.storm.cooldown:PT5M}") Duration cooldown,
		@Value("${logcopilot.alert.storm.rate-budget:30}") int rateBudget,
		@Value("${logcopilot.alert.storm.rate-window:PT1H}") Duration rateWindow,
		@Value("${logcopilot.alert.storm.quiet-hours.enabled:false}") boolean quietHoursEnabled,
		@Value("${logcopilot.alert.storm.quiet-hours.start:23:00}") String quietHoursStart,
		@Value("${logcopilot.alert.storm.quiet-hours.end:07:00}") String quietHoursEnd,
		@Value("${logcopilot.alert.storm.quiet-hours.zone:UTC}") String quietHoursZone
	) {
		this(
			projectService,
			DEFAULT_MAX_AUDIT_LOGS_PER_PROJECT,
			new AlertStormPolicy(
				cooldown,
				rateBudget,
				rateWindow,
				quietHoursEnabled,
				LocalTime.parse(quietHoursStart),
				LocalTime.parse(quietHoursEnd),
				ZoneId.of(quietHoursZone)
			),
			Clock.systemUTC()
		);
	}

	AlertService(
		ProjectService projectService,
		int maxAuditLogsPerProject,
		AlertStormPolicy alertStormPolicy,
		Clock clock
	) {
		if (maxAuditLogsPerProject < 1) {
			throw new IllegalArgumentException("maxAuditLogsPerProject must be >= 1");
		}
		if (alertStormPolicy == null) {
			throw new IllegalArgumentException("alertStormPolicy must not be null");
		}
		if (clock == null) {
			throw new IllegalArgumentException("clock must not be null");
		}
		this.projectService = projectService;
		this.maxAuditLogsPerProject = maxAuditLogsPerProject;
		this.alertStormPolicy = alertStormPolicy;
		this.clock = clock;
	}

	public synchronized ConfigureResult configureSlack(
		String projectId,
		String actorToken,
		ConfigureSlackCommand command
	) {
		requireProjectForWrite(projectId);
		if (command == null) {
			throw new ValidationException("Request body must not be null");
		}

		String webhookUrl = validateWebhookUrl(command.webhookUrl());
		String channel = requireNonBlank(command.channel(), "channel must not be blank");
		double minConfidence = normalizeMinConfidence(command.minConfidence());

		ConfigureResult result = upsertChannel(
			projectId,
			"slack",
			new SlackConfig(webhookUrl, channel, minConfidence)
		);

		appendAuditLog(
			projectId,
			actorToken,
			"alert.slack.configured",
			"alert_channel",
			result.channel().id(),
			Map.of(
				"type", "slack",
				"channel", channel,
				"min_confidence", minConfidence,
				"created", result.created()
			)
		);

		return result;
	}

	public synchronized ConfigureResult configureEmail(
		String projectId,
		String actorToken,
		ConfigureEmailCommand command
	) {
		requireProjectForWrite(projectId);
		if (command == null) {
			throw new ValidationException("Request body must not be null");
		}

		String from = validateEmail(command.from(), "from must be a valid email");
		List<String> recipients = validateRecipients(command.recipients());
		SmtpConfig smtp = validateSmtp(command.smtp());
		double minConfidence = normalizeMinConfidence(command.minConfidence());

		ConfigureResult result = upsertChannel(
			projectId,
			"email",
			new EmailConfig(from, recipients, smtp, minConfidence)
		);

		appendAuditLog(
			projectId,
			actorToken,
			"alert.email.configured",
			"alert_channel",
			result.channel().id(),
			Map.of(
				"type", "email",
				"from", maskEmailForAudit(from),
				"recipients_count", recipients.size(),
				"smtp_host", smtp.host(),
				"smtp_port", smtp.port(),
				"starttls", smtp.starttls(),
				"min_confidence", minConfidence,
				"created", result.created()
			)
		);

		return result;
	}

	public synchronized AlertDispatchResult dispatchIncidentAlert(
		String projectId,
		DispatchIncidentAlertCommand command
	) {
		requireProjectForRead(projectId);
		DispatchIncidentAlertCommand safeCommand = validateDispatchCommand(command);
		Instant now = clock.instant();
		String scopeKey = dispatchScopeKey(projectId, safeCommand.service());

		if (!hasEnabledChannel(projectId)) {
			return suppressDispatch(projectId, safeCommand, "no_channel_configured", now);
		}
		if (isQuietHours(now)) {
			return suppressDispatch(projectId, safeCommand, "quiet_hours", now);
		}
		if (isInCooldown(scopeKey, now)) {
			return suppressDispatch(projectId, safeCommand, "cooldown", now);
		}
		if (isRateBudgetExceeded(scopeKey, now)) {
			return suppressDispatch(projectId, safeCommand, "rate_budget_exceeded", now);
		}

		recordSuccessfulDispatch(scopeKey, now);
		appendAuditLog(
			projectId,
			safeCommand.actorToken(),
			"alert.dispatched",
			"incident",
			safeCommand.incidentId(),
			dispatchMetadata(safeCommand, "dispatched")
		);
		return new AlertDispatchResult(true, "dispatched", now);
	}

	public synchronized void recordDispatchFailure(
		String projectId,
		DispatchIncidentAlertCommand command,
		String reason
	) {
		if (projectId == null || projectId.isBlank() || !projectService.existsById(projectId)) {
			return;
		}
		DispatchIncidentAlertCommand safeCommand = normalizeDispatchCommandForFailure(command);
		appendAuditLog(
			projectId,
			safeCommand.actorToken(),
			"alert.dispatch.failed",
			"incident",
			safeCommand.incidentId(),
			dispatchMetadata(safeCommand, normalizeFailureReason(reason))
		);
	}

	public synchronized AuditLogListResult listAuditLogs(String projectId, AuditLogQuery query) {
		requireProjectForRead(projectId);
		AuditLogQuery safeQuery = query == null
			? new AuditLogQuery(null, null, null, null)
			: query;

		int limit = normalizeLimit(safeQuery.limit());
		int offset = normalizeCursor(safeQuery.cursor());
		String action = normalizeOptional(safeQuery.action());
		String actor = normalizeOptional(safeQuery.actor());

		List<AuditLogState> original = auditLogsByProject.getOrDefault(projectId, List.of());
		List<AuditLogState> ordered = new ArrayList<>(original);
		Collections.reverse(ordered);

		List<AuditLogState> filtered = ordered.stream()
			.filter(log -> action == null || log.action().equals(action))
			.filter(log -> actor == null || log.actor().equals(actor))
			.toList();

		int start = Math.min(offset, filtered.size());
		int end = Math.min(start + limit, filtered.size());
		List<AuditLog> data = filtered.subList(start, end).stream()
			.map(state -> new AuditLog(
				state.id(),
				state.actor(),
				state.action(),
				state.resourceType(),
				state.resourceId(),
				state.createdAt(),
				state.metadata()
			))
			.toList();

		String nextCursor = end < filtered.size() ? String.valueOf(end) : null;
		return new AuditLogListResult(data, UUID.randomUUID().toString(), nextCursor);
	}

	private DispatchIncidentAlertCommand validateDispatchCommand(DispatchIncidentAlertCommand command) {
		if (command == null) {
			throw new ValidationException("dispatch command must not be null");
		}
		String incidentId = requireNonBlank(command.incidentId(), "incident_id must not be blank");
		String service = requireNonBlank(command.service(), "service must not be blank");
		Integer severityScore = command.severityScore();
		if (severityScore == null || severityScore < 0 || severityScore > 100) {
			throw new ValidationException("severity_score must be between 0 and 100");
		}
		String actorToken = normalizeOptional(command.actorToken());
		return new DispatchIncidentAlertCommand(
			incidentId,
			service,
			severityScore,
			actorToken == null ? "system" : actorToken
		);
	}

	private boolean hasEnabledChannel(String projectId) {
		Map<String, AlertChannelState> channels = channelsByProject.getOrDefault(projectId, Map.of());
		return channels.values().stream().anyMatch(AlertChannelState::enabled);
	}

	private DispatchIncidentAlertCommand normalizeDispatchCommandForFailure(DispatchIncidentAlertCommand command) {
		if (command == null) {
			return new DispatchIncidentAlertCommand("unknown", "unknown", 0, "system");
		}
		String incidentId = normalizeOptional(command.incidentId());
		String service = normalizeOptional(command.service());
		Integer severityScore = command.severityScore();
		String actorToken = normalizeOptional(command.actorToken());
		return new DispatchIncidentAlertCommand(
			incidentId == null ? "unknown" : incidentId,
			service == null ? "unknown" : service,
			severityScore == null ? 0 : severityScore,
			actorToken == null ? "system" : actorToken
		);
	}

	private boolean isQuietHours(Instant now) {
		if (!alertStormPolicy.quietHoursEnabled()) {
			return false;
		}

		LocalTime nowTime = now.atZone(alertStormPolicy.quietHoursZone()).toLocalTime();
		LocalTime start = alertStormPolicy.quietHoursStart();
		LocalTime end = alertStormPolicy.quietHoursEnd();
		if (start.equals(end)) {
			return true;
		}
		if (start.isBefore(end)) {
			return !nowTime.isBefore(start) && nowTime.isBefore(end);
		}
		return !nowTime.isBefore(start) || nowTime.isBefore(end);
	}

	private boolean isInCooldown(String scopeKey, Instant now) {
		Instant lastDispatchAt = lastDispatchAtByProject.get(scopeKey);
		if (lastDispatchAt == null) {
			return false;
		}
		Duration cooldown = alertStormPolicy.cooldown();
		if (cooldown.isZero() || cooldown.isNegative()) {
			return false;
		}
		return now.isBefore(lastDispatchAt.plus(cooldown));
	}

	private boolean isRateBudgetExceeded(String scopeKey, Instant now) {
		DispatchBudgetState budget = refreshBudget(scopeKey, now);
		return budget.sentCount() >= alertStormPolicy.rateBudget();
	}

	private void recordSuccessfulDispatch(String scopeKey, Instant now) {
		lastDispatchAtByProject.put(scopeKey, now);
		DispatchBudgetState budget = refreshBudget(scopeKey, now);
		dispatchBudgetByProject.put(scopeKey, new DispatchBudgetState(budget.windowStart(), budget.sentCount() + 1));
	}

	private DispatchBudgetState refreshBudget(String scopeKey, Instant now) {
		DispatchBudgetState budget = dispatchBudgetByProject.get(scopeKey);
		if (budget == null || !now.isBefore(budget.windowStart().plus(alertStormPolicy.rateWindow()))) {
			budget = new DispatchBudgetState(now, 0);
			dispatchBudgetByProject.put(scopeKey, budget);
		}
		return budget;
	}

	private AlertDispatchResult suppressDispatch(
		String projectId,
		DispatchIncidentAlertCommand command,
		String reason,
		Instant at
	) {
		appendAuditLog(
			projectId,
			command.actorToken(),
			"alert.dispatch.suppressed",
			"incident",
			command.incidentId(),
			dispatchMetadata(command, reason)
		);
		return new AlertDispatchResult(false, reason, at);
	}

	private Map<String, Object> dispatchMetadata(DispatchIncidentAlertCommand command, String reason) {
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("reason", reason);
		metadata.put("incident_id", command.incidentId());
		metadata.put("service", command.service());
		metadata.put("severity_score", command.severityScore());
		metadata.put("cooldown_seconds", alertStormPolicy.cooldown().toSeconds());
		metadata.put("rate_budget", alertStormPolicy.rateBudget());
		metadata.put("rate_window_seconds", alertStormPolicy.rateWindow().toSeconds());
		metadata.put("quiet_hours_enabled", alertStormPolicy.quietHoursEnabled());
		metadata.put("quiet_hours_start", alertStormPolicy.quietHoursStart().toString());
		metadata.put("quiet_hours_end", alertStormPolicy.quietHoursEnd().toString());
		metadata.put("quiet_hours_zone", alertStormPolicy.quietHoursZone().toString());
		return Map.copyOf(metadata);
	}

	private String dispatchScopeKey(String projectId, String service) {
		return projectId + "|" + service.toLowerCase(Locale.ROOT);
	}

	private String normalizeFailureReason(String reason) {
		String normalized = normalizeOptional(reason);
		return normalized == null ? "dispatch_failed" : normalized;
	}

	private ConfigureResult upsertChannel(String projectId, String type, Object config) {
		Map<String, AlertChannelState> channels = channelsByProject.computeIfAbsent(
			projectId,
			ignored -> new LinkedHashMap<>()
		);
		AlertChannelState existing = channels.get(type);

		boolean created = existing == null;
		String id = created ? UUID.randomUUID().toString() : existing.id();
		Instant updatedAt = Instant.now();

		AlertChannelState updated = new AlertChannelState(id, type, true, updatedAt, config);
		channels.put(type, updated);
		return new ConfigureResult(created, new AlertChannel(id, type, true, updatedAt));
	}

	private void appendAuditLog(
		String projectId,
		String actorToken,
		String action,
		String resourceType,
		String resourceId,
		Map<String, Object> metadata
	) {
		List<AuditLogState> logs = auditLogsByProject.computeIfAbsent(projectId, ignored -> new ArrayList<>());
		logs.add(new AuditLogState(
			UUID.randomUUID().toString(),
			maskActor(actorToken),
			action,
			resourceType,
			resourceId,
			Instant.now(),
			Map.copyOf(metadata)
		));
		while (logs.size() > maxAuditLogsPerProject) {
			logs.remove(0);
		}
	}

	private void requireProjectForWrite(String projectId) {
		if (projectId == null || projectId.isBlank() || !projectService.existsById(projectId)) {
			throw new ValidationException("Project not found");
		}
	}

	private void requireProjectForRead(String projectId) {
		if (projectId == null || projectId.isBlank() || !projectService.existsById(projectId)) {
			throw new NotFoundException("Project not found");
		}
	}

	private String validateWebhookUrl(String webhookUrl) {
		String normalized = requireNonBlank(webhookUrl, "webhook_url must be a valid URI");
		try {
			URI uri = new URI(normalized);
			if (!uri.isAbsolute() || uri.getHost() == null) {
				throw new ValidationException("webhook_url must be a valid URI");
			}
			String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
			if (!"http".equals(scheme) && !"https".equals(scheme)) {
				throw new ValidationException("webhook_url must be a valid URI");
			}
		} catch (URISyntaxException exception) {
			throw new ValidationException("webhook_url must be a valid URI");
		}
		return normalized;
	}

	private String validateEmail(String email, String message) {
		String normalized = requireNonBlank(email, message);
		if (!EMAIL_PATTERN.matcher(normalized).matches()) {
			throw new ValidationException(message);
		}
		return normalized;
	}

	private List<String> validateRecipients(List<String> recipients) {
		if (recipients == null || recipients.isEmpty()) {
			throw new ValidationException("recipients must contain at least 1 email");
		}

		List<String> normalized = new ArrayList<>(recipients.size());
		for (int index = 0; index < recipients.size(); index++) {
			normalized.add(validateEmail(
				recipients.get(index),
				"recipients[%d] must be a valid email".formatted(index)
			));
		}
		return List.copyOf(normalized);
	}

	private SmtpConfig validateSmtp(SmtpCommand smtp) {
		if (smtp == null) {
			throw new ValidationException("smtp must not be null");
		}

		String host = requireNonBlank(smtp.host(), "smtp.host must not be blank");
		Integer port = smtp.port();
		if (port == null || port < 1 || port > 65535) {
			throw new ValidationException("smtp.port must be between 1 and 65535");
		}

		String username = requireNonBlank(smtp.username(), "smtp.username must not be blank");
		String password = requireNonBlank(smtp.password(), "smtp.password must not be blank");
		boolean starttls = smtp.starttls() == null || smtp.starttls();
		return new SmtpConfig(host, port, username, password, starttls);
	}

	private double normalizeMinConfidence(Double minConfidence) {
		double normalized = minConfidence == null ? DEFAULT_MIN_CONFIDENCE : minConfidence;
		if (Double.isNaN(normalized) || normalized < 0 || normalized > 1) {
			throw new ValidationException("min_confidence must be between 0 and 1");
		}
		return normalized;
	}

	private int normalizeLimit(Integer limit) {
		int normalized = limit == null ? DEFAULT_LIMIT : limit;
		if (normalized < 1 || normalized > MAX_LIMIT) {
			throw new ValidationException("limit must be between 1 and 200");
		}
		return normalized;
	}

	private int normalizeCursor(String cursor) {
		String normalized = normalizeOptional(cursor);
		if (normalized == null) {
			return 0;
		}
		try {
			int parsed = Integer.parseInt(normalized);
			if (parsed < 0) {
				throw new ValidationException("cursor must be a non-negative integer");
			}
			return parsed;
		} catch (NumberFormatException exception) {
			throw new ValidationException("cursor must be a non-negative integer");
		}
	}

	private String maskActor(String actorToken) {
		String normalized = normalizeOptional(actorToken);
		if (normalized == null) {
			return "token:anonymous";
		}
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] bytes = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
			String hex = HexFormat.of().formatHex(bytes);
			return "token:" + hex.substring(0, 12);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is not available", exception);
		}
	}

	private String maskEmailForAudit(String email) {
		String normalized = normalizeOptional(email);
		if (normalized == null) {
			return "domain:unknown";
		}

		int atIndex = normalized.lastIndexOf('@');
		if (atIndex < 0 || atIndex == normalized.length() - 1) {
			return "domain:unknown";
		}
		return "domain:" + normalized.substring(atIndex + 1).toLowerCase(Locale.ROOT);
	}

	private String requireNonBlank(String value, String message) {
		String normalized = normalizeOptional(value);
		if (normalized == null) {
			throw new ValidationException(message);
		}
		return normalized;
	}

	private String normalizeOptional(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	public record ConfigureSlackCommand(
		String webhookUrl,
		String channel,
		Double minConfidence
	) {
	}

	public record ConfigureEmailCommand(
		String from,
		List<String> recipients,
		SmtpCommand smtp,
		Double minConfidence
	) {
	}

	public record SmtpCommand(
		String host,
		Integer port,
		String username,
		String password,
		Boolean starttls
	) {
	}

	public record ConfigureResult(
		boolean created,
		AlertChannel channel
	) {
	}

	public record DispatchIncidentAlertCommand(
		String incidentId,
		String service,
		Integer severityScore,
		String actorToken
	) {
	}

	public record AlertDispatchResult(
		boolean dispatched,
		String reason,
		Instant decidedAt
	) {
	}

	public record AlertStormPolicy(
		Duration cooldown,
		int rateBudget,
		Duration rateWindow,
		boolean quietHoursEnabled,
		LocalTime quietHoursStart,
		LocalTime quietHoursEnd,
		ZoneId quietHoursZone
	) {
		public AlertStormPolicy {
			if (cooldown == null || cooldown.isNegative()) {
				throw new IllegalArgumentException("cooldown must be zero or positive");
			}
			if (rateBudget < 1) {
				throw new IllegalArgumentException("rateBudget must be >= 1");
			}
			if (rateWindow == null || rateWindow.isZero() || rateWindow.isNegative()) {
				throw new IllegalArgumentException("rateWindow must be positive");
			}
			if (quietHoursStart == null || quietHoursEnd == null || quietHoursZone == null) {
				throw new IllegalArgumentException("quiet-hours fields must not be null");
			}
		}
	}

	public record AlertChannel(
		String id,
		String type,
		boolean enabled,
		Instant updatedAt
	) {
	}

	public record AuditLogQuery(
		String action,
		String actor,
		String cursor,
		Integer limit
	) {
	}

	public record AuditLogListResult(
		List<AuditLog> data,
		String requestId,
		String nextCursor
	) {
	}

	public record AuditLog(
		String id,
		String actor,
		String action,
		String resourceType,
		String resourceId,
		Instant createdAt,
		Map<String, Object> metadata
	) {
	}

	private record AlertChannelState(
		String id,
		String type,
		boolean enabled,
		Instant updatedAt,
		Object config
	) {
	}

	private record SlackConfig(
		String webhookUrl,
		String channel,
		double minConfidence
	) {
	}

	private record EmailConfig(
		String from,
		List<String> recipients,
		SmtpConfig smtp,
		double minConfidence
	) {
	}

	private record SmtpConfig(
		String host,
		int port,
		String username,
		String password,
		boolean starttls
	) {
	}

	private record AuditLogState(
		String id,
		String actor,
		String action,
		String resourceType,
		String resourceId,
		Instant createdAt,
		Map<String, Object> metadata
	) {
	}

	private record DispatchBudgetState(
		Instant windowStart,
		int sentCount
	) {
	}
}
