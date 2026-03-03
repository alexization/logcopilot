package com.logcopilot.incident;

import com.logcopilot.common.error.ConflictException;
import com.logcopilot.common.error.NotFoundException;
import com.logcopilot.ingest.domain.CanonicalLogEvent;
import com.logcopilot.incident.domain.AnalysisReport;
import com.logcopilot.incident.domain.Hypothesis;
import com.logcopilot.incident.domain.IncidentDetail;
import com.logcopilot.incident.domain.IncidentListResult;
import com.logcopilot.incident.domain.IncidentStatus;
import com.logcopilot.incident.domain.IncidentSummary;
import com.logcopilot.incident.domain.ReanalyzeAcceptedResult;
import org.springframework.stereotype.Service;

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

	private static final int DEFAULT_LIMIT = 50;
	private static final int MAX_LIMIT = 200;

	private final Map<String, IncidentState> incidentsById = new LinkedHashMap<>();
	private final Map<String, List<String>> incidentIdsByProject = new HashMap<>();

	public synchronized void recordIngestedEvents(String projectId, List<CanonicalLogEvent> events) {
		if (projectId == null || projectId.isBlank() || events == null || events.isEmpty()) {
			return;
		}

		Map<String, List<CanonicalLogEvent>> eventsByService = new LinkedHashMap<>();
		for (CanonicalLogEvent event : events) {
			if (event == null || event.service() == null || event.service().isBlank()) {
				continue;
			}
			eventsByService.computeIfAbsent(event.service(), ignored -> new ArrayList<>()).add(event);
		}

		for (Map.Entry<String, List<CanonicalLogEvent>> entry : eventsByService.entrySet()) {
			String service = entry.getKey();
			List<CanonicalLogEvent> serviceEvents = entry.getValue();
			Instant firstSeen = minTimestamp(serviceEvents);
			Instant lastSeen = maxTimestamp(serviceEvents);
			int severityScore = maxSeverityScore(serviceEvents);
			AnalysisReport report = defaultReport(service, serviceEvents);
			IncidentState state = new IncidentState(
				UUID.randomUUID().toString(),
				projectId,
				IncidentStatus.OPEN,
				service,
				severityScore,
				serviceEvents.size(),
				firstSeen,
				lastSeen,
				report
			);
			incidentsById.put(state.id, state);
			incidentIdsByProject.computeIfAbsent(projectId, ignored -> new ArrayList<>()).add(state.id);
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

		state.status = IncidentStatus.INVESTIGATING;
		state.report = reanalyzedReport(state, reason);
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

	private Instant minTimestamp(List<CanonicalLogEvent> events) {
		Instant min = null;
		for (CanonicalLogEvent event : events) {
			Instant timestamp = parseTimestamp(event.timestamp());
			if (min == null || timestamp.isBefore(min)) {
				min = timestamp;
			}
		}
		return min == null ? Instant.now() : min;
	}

	private Instant maxTimestamp(List<CanonicalLogEvent> events) {
		Instant max = null;
		for (CanonicalLogEvent event : events) {
			Instant timestamp = parseTimestamp(event.timestamp());
			if (max == null || timestamp.isAfter(max)) {
				max = timestamp;
			}
		}
		return max == null ? Instant.now() : max;
	}

	private Instant parseTimestamp(String timestamp) {
		if (timestamp == null || timestamp.isBlank()) {
			return Instant.now();
		}
		try {
			return Instant.parse(timestamp);
		} catch (DateTimeParseException ignored) {
			return Instant.now();
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

	private AnalysisReport reanalyzedReport(IncidentState state, String reason) {
		String normalizedReason = reason == null || reason.isBlank()
			? "manual trigger"
			: reason.trim();

		return new AnalysisReport(
			"Reanalysis started for incident " + state.id,
			List.of(new Hypothesis(
				"Incident requires deeper inspection",
				0.6,
				List.of("Reanalysis reason: " + normalizedReason)
			)),
			List.of("Collect additional logs around " + state.service),
			List.of("Reanalysis job queued; final report will be updated asynchronously")
		);
	}

	private static class IncidentState {
		private final String id;
		private final String projectId;
		private IncidentStatus status;
		private final String service;
		private final int severityScore;
		private final int eventCount;
		private final Instant firstSeen;
		private final Instant lastSeen;
		private AnalysisReport report;

		private IncidentState(
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
			this.id = id;
			this.projectId = projectId;
			this.status = status;
			this.service = service;
			this.severityScore = severityScore;
			this.eventCount = eventCount;
			this.firstSeen = firstSeen;
			this.lastSeen = lastSeen;
			this.report = report;
		}
	}
}
