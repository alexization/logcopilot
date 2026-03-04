package com.logcopilot.incident;

import com.logcopilot.alert.AlertService;
import com.logcopilot.common.error.ConflictException;
import com.logcopilot.common.error.NotFoundException;
import com.logcopilot.common.persistence.StateSnapshotRepository;
import com.logcopilot.ingest.domain.CanonicalLogEvent;
import com.logcopilot.incident.analyzer.IncidentAnalyzer;
import com.logcopilot.incident.analyzer.IncidentReanalyzeCommand;
import com.logcopilot.incident.domain.AnalysisReport;
import com.logcopilot.incident.domain.Hypothesis;
import com.logcopilot.incident.domain.IncidentDetail;
import com.logcopilot.incident.domain.IncidentListResult;
import com.logcopilot.incident.domain.IncidentStatus;
import com.logcopilot.incident.domain.IncidentSummary;
import com.logcopilot.incident.domain.ReanalyzeAcceptedResult;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class IncidentService {

	private static final String SNAPSHOT_SCOPE = "incident-service";
	private static final Logger logger = LoggerFactory.getLogger(IncidentService.class);
	private static final int DEFAULT_LIMIT = 50;
	private static final int MAX_LIMIT = 200;

	private final IncidentAnalyzer incidentAnalyzer;
	private final AlertService alertService;
	private final StateSnapshotRepository stateSnapshotRepository;
	private final Map<String, IncidentState> incidentsById = new LinkedHashMap<>();
	private final Map<String, List<String>> incidentIdsByProject = new HashMap<>();

	@Autowired
	public IncidentService(
		IncidentAnalyzer incidentAnalyzer,
		AlertService alertService,
		ObjectProvider<StateSnapshotRepository> stateSnapshotRepositoryProvider
	) {
		this(incidentAnalyzer, alertService, stateSnapshotRepositoryProvider.getIfAvailable());
	}

	IncidentService(IncidentAnalyzer incidentAnalyzer, AlertService alertService) {
		this(incidentAnalyzer, alertService, (StateSnapshotRepository) null);
	}

	IncidentService(IncidentAnalyzer incidentAnalyzer) {
		this(incidentAnalyzer, null, (StateSnapshotRepository) null);
	}

	IncidentService(
		IncidentAnalyzer incidentAnalyzer,
		AlertService alertService,
		StateSnapshotRepository stateSnapshotRepository
	) {
		this.incidentAnalyzer = incidentAnalyzer;
		this.alertService = alertService;
		this.stateSnapshotRepository = stateSnapshotRepository;
		restoreState();
	}

	public void recordIngestedEvents(String projectId, List<CanonicalLogEvent> events) {
		if (projectId == null || projectId.isBlank() || events == null || events.isEmpty()) {
			return;
		}

		Map<String, List<CanonicalLogEvent>> eventsByService = new LinkedHashMap<>();
		for (CanonicalLogEvent event : events) {
			if (event == null) {
				continue;
			}
			String normalizedService = normalizeService(event.service());
			if (normalizedService == null) {
				continue;
			}
			eventsByService.computeIfAbsent(normalizedService, ignored -> new ArrayList<>()).add(event);
		}

		List<IncidentState> createdIncidents = new ArrayList<>();
		for (Map.Entry<String, List<CanonicalLogEvent>> entry : eventsByService.entrySet()) {
			String service = entry.getKey();
			List<CanonicalLogEvent> serviceEvents = entry.getValue();
			TimestampRange timestampRange = computeTimestampRange(serviceEvents);
			int severityScore = maxSeverityScore(serviceEvents);
			AnalysisReport report = defaultReport(service, serviceEvents);
			IncidentState state = new IncidentState(
				UUID.randomUUID().toString(),
				projectId,
				IncidentStatus.OPEN,
				service,
				severityScore,
				serviceEvents.size(),
				timestampRange.firstSeen(),
				timestampRange.lastSeen(),
				report
			);
			createdIncidents.add(state);
		}

		synchronized (this) {
			for (IncidentState state : createdIncidents) {
				incidentsById.put(state.id, state);
				incidentIdsByProject.computeIfAbsent(projectId, ignored -> new ArrayList<>()).add(state.id);
			}
			persistState();
		}

		for (IncidentState state : createdIncidents) {
			dispatchAlert(projectId, state);
		}
	}

	private void dispatchAlert(String projectId, IncidentState state) {
		if (alertService == null) {
			return;
		}
		AlertService.DispatchIncidentAlertCommand command = new AlertService.DispatchIncidentAlertCommand(
			state.id,
			state.service,
			state.severityScore,
			"system:incident"
		);
		try {
			alertService.dispatchIncidentAlert(projectId, command);
		} catch (RuntimeException exception) {
			logger.warn("Failed to dispatch incident alert: incidentId={}", state.id, exception);
			alertService.recordDispatchFailure(projectId, command, exception.getMessage());
		}
	}

	public synchronized IncidentListResult list(
		String projectId,
		String status,
		String service,
		String cursor,
		Integer limit
	) {
		List<String> incidentIds = incidentIdsByProject.getOrDefault(projectId, List.of());
		List<IncidentState> filtered = new ArrayList<>();

		for (String incidentId : incidentIds) {
			IncidentState state = incidentsById.get(incidentId);
			if (state == null) {
				continue;
			}
			if (status != null && !status.isBlank() && !state.status.value().equals(status)) {
				continue;
			}
			if (service != null && !service.isBlank() && !state.service.equalsIgnoreCase(service)) {
				continue;
			}
			filtered.add(state);
		}

		int offset = parseOffset(cursor);
		int validatedLimit = normalizeLimit(limit);
		int from = Math.min(offset, filtered.size());
		int to = Math.min(from + validatedLimit, filtered.size());

		List<IncidentSummary> data = filtered.subList(from, to).stream()
			.map(this::toSummary)
			.toList();
		String nextCursor = to < filtered.size() ? String.valueOf(to) : null;
		return new IncidentListResult(data, nextCursor);
	}

	public synchronized IncidentDetail getIncident(String incidentId) {
		IncidentState state = incidentsById.get(incidentId);
		if (state == null) {
			throw new NotFoundException("Incident not found");
		}
		return toDetail(state);
	}

	public synchronized ReanalyzeAcceptedResult reanalyzeIncident(String incidentId, String reason) {
		IncidentState state = incidentsById.get(incidentId);
		if (state == null) {
			throw new NotFoundException("Incident not found");
		}
		if (state.status == IncidentStatus.INVESTIGATING) {
			throw new ConflictException("Incident reanalysis already in progress");
		}

		IncidentState updated = new IncidentState(
			state.id,
			state.projectId,
			IncidentStatus.INVESTIGATING,
			state.service,
			state.severityScore,
			state.eventCount,
			state.firstSeen,
			state.lastSeen,
			incidentAnalyzer.analyze(new IncidentReanalyzeCommand(
				state.id,
				state.projectId,
				state.service,
				reason,
				state.report
			))
		);
		incidentsById.put(incidentId, updated);
		persistState();
		return new ReanalyzeAcceptedResult(true, UUID.randomUUID().toString());
	}

	private IncidentSummary toSummary(IncidentState state) {
		return new IncidentSummary(
			state.id,
			state.projectId,
			state.status,
			state.service,
			state.severityScore,
			state.eventCount,
			state.firstSeen,
			state.lastSeen
		);
	}

	private IncidentDetail toDetail(IncidentState state) {
		return new IncidentDetail(
			state.id,
			state.projectId,
			state.status,
			state.service,
			state.severityScore,
			state.eventCount,
			state.firstSeen,
			state.lastSeen,
			state.report
		);
	}

	private int normalizeLimit(Integer limit) {
		if (limit == null) {
			return DEFAULT_LIMIT;
		}
		if (limit < 1) {
			return 1;
		}
		return Math.min(limit, MAX_LIMIT);
	}

	private int parseOffset(String cursor) {
		if (cursor == null || cursor.isBlank()) {
			return 0;
		}

		try {
			return Math.max(Integer.parseInt(cursor), 0);
		} catch (NumberFormatException ignored) {
			return 0;
		}
	}

	private String normalizeService(String service) {
		if (service == null) {
			return null;
		}
		String trimmed = service.trim();
		if (trimmed.isEmpty()) {
			return null;
		}
		return trimmed.toLowerCase(Locale.ROOT);
	}

	private TimestampRange computeTimestampRange(List<CanonicalLogEvent> events) {
		Instant min = null;
		Instant max = null;

		for (CanonicalLogEvent event : events) {
			Instant timestamp = parseTimestamp(event.timestamp());
			if (timestamp == null) {
				continue;
			}
			if (min == null || timestamp.isBefore(min)) {
				min = timestamp;
			}
			if (max == null || timestamp.isAfter(max)) {
				max = timestamp;
			}
		}

		if (min == null || max == null) {
			Instant now = Instant.now();
			return new TimestampRange(now, now);
		}
		return new TimestampRange(min, max);
	}

	private Instant parseTimestamp(String timestamp) {
		if (timestamp == null || timestamp.isBlank()) {
			return null;
		}
		try {
			return Instant.parse(timestamp);
		} catch (DateTimeParseException exception) {
			logger.warn("Failed to parse timestamp: {}", timestamp, exception);
			return null;
		}
	}

	private int maxSeverityScore(List<CanonicalLogEvent> events) {
		int max = 0;
		for (CanonicalLogEvent event : events) {
			max = Math.max(max, severityScore(event.severity()));
		}
		return max;
	}

	private int severityScore(String severity) {
		if (severity == null || severity.isBlank()) {
			return 0;
		}

		return switch (severity.toLowerCase(Locale.ROOT)) {
			case "debug" -> 10;
			case "info" -> 20;
			case "warn" -> 50;
			case "error" -> 80;
			case "fatal" -> 100;
			default -> 0;
		};
	}

	private AnalysisReport defaultReport(String service, List<CanonicalLogEvent> events) {
		List<String> evidence = events.stream()
			.map(CanonicalLogEvent::message)
			.filter(message -> message != null && !message.isBlank())
			.limit(3)
			.toList();
		if (evidence.isEmpty()) {
			evidence = List.of("No explicit message evidence captured");
		}

		return new AnalysisReport(
			"Potential incident detected for service " + service,
			List.of(new Hypothesis(
				"Error pattern observed in recent logs",
				0.55,
				evidence
			)),
			List.of(
				"Review recent deployments for service " + service,
				"Check downstream dependency health"
			),
			List.of("Rule-based baseline report; LLM analysis not applied yet")
		);
	}

	private record TimestampRange(Instant firstSeen, Instant lastSeen) {
	}

	record IncidentState(
		String id,
		String projectId,
		IncidentStatus status,
		String service,
		int severityScore,
		int eventCount,
		Instant firstSeen,
		Instant lastSeen,
		AnalysisReport report
	) {
	}

	private void restoreState() {
		if (stateSnapshotRepository == null) {
			return;
		}
		stateSnapshotRepository.load(SNAPSHOT_SCOPE, IncidentSnapshot.class)
			.ifPresent(snapshot -> {
				incidentsById.clear();
				incidentIdsByProject.clear();
				if (snapshot.incidentsById() != null) {
					incidentsById.putAll(snapshot.incidentsById());
				}
				if (snapshot.incidentIdsByProject() != null) {
					snapshot.incidentIdsByProject().forEach((projectId, incidentIds) -> incidentIdsByProject.put(
						projectId,
						incidentIds == null ? new ArrayList<>() : new ArrayList<>(incidentIds)
					));
				}
			});
	}

	private void persistState() {
		if (stateSnapshotRepository == null) {
			return;
		}
		Map<String, List<String>> copiedIncidentIdsByProject = new HashMap<>();
		incidentIdsByProject.forEach((projectId, incidentIds) ->
			copiedIncidentIdsByProject.put(projectId, new ArrayList<>(incidentIds))
		);
		stateSnapshotRepository.save(
			SNAPSHOT_SCOPE,
			new IncidentSnapshot(new LinkedHashMap<>(incidentsById), copiedIncidentIdsByProject)
		);
	}

	record IncidentSnapshot(
		Map<String, IncidentState> incidentsById,
		Map<String, List<String>> incidentIdsByProject
	) {
	}
}
