package com.logcopilot.alert;

import com.logcopilot.common.error.NotFoundException;
import com.logcopilot.common.error.ValidationException;
import com.logcopilot.project.ProjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
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
	private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

	private final ProjectService projectService;
	private final int maxAuditLogsPerProject;
	private final Map<String, Map<String, AlertChannelState>> channelsByProject = new HashMap<>();
	private final Map<String, List<AuditLogState>> auditLogsByProject = new HashMap<>();

	@Autowired
	public AlertService(ProjectService projectService) {
		this(projectService, DEFAULT_MAX_AUDIT_LOGS_PER_PROJECT);
	}

	AlertService(ProjectService projectService, int maxAuditLogsPerProject) {
		if (maxAuditLogsPerProject < 1) {
			throw new IllegalArgumentException("maxAuditLogsPerProject must be >= 1");
		}
		this.projectService = projectService;
		this.maxAuditLogsPerProject = maxAuditLogsPerProject;
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
		String resourceId,
		Map<String, Object> metadata
	) {
		List<AuditLogState> logs = auditLogsByProject.computeIfAbsent(projectId, ignored -> new ArrayList<>());
		logs.add(new AuditLogState(
			UUID.randomUUID().toString(),
			maskActor(actorToken),
			action,
			"alert_channel",
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
}
