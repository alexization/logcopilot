package com.logcopilot.ingest;

import com.logcopilot.common.error.NotImplementedException;
import com.logcopilot.common.error.ValidationException;
import com.logcopilot.ingest.domain.EventDeduplicationPolicy;
import com.logcopilot.ingest.domain.IngestAcceptedResult;
import com.logcopilot.ingest.domain.IngestEventsCommand;
import com.logcopilot.ingest.domain.IngestRequestValidator;
import com.logcopilot.incident.IncidentService;
import com.logcopilot.project.ProjectService;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class IngestService {

	private final ProjectService projectService;
	private final IngestIdempotencyStore idempotencyStore;
	private final IngestRequestValidator ingestRequestValidator;
	private final EventDeduplicationPolicy eventDeduplicationPolicy;
	private final IncidentService incidentService;

	public IngestService(
		ProjectService projectService,
		IngestIdempotencyStore idempotencyStore,
		IngestRequestValidator ingestRequestValidator,
		EventDeduplicationPolicy eventDeduplicationPolicy,
		IncidentService incidentService
	) {
		this.projectService = projectService;
		this.idempotencyStore = idempotencyStore;
		this.ingestRequestValidator = ingestRequestValidator;
		this.eventDeduplicationPolicy = eventDeduplicationPolicy;
		this.incidentService = incidentService;
	}

	public IngestAcceptedResult ingestEvents(String idempotencyKey, IngestEventsCommand request) {
		return idempotencyStore.computeIfAbsent(pushIdempotencyKey(idempotencyKey), ignored -> ingestIntoCanonicalPipeline(request));
	}

	public IngestAcceptedResult ingestPulledEvents(IngestEventsCommand request) {
		String idempotencyKey = pullIdempotencyKey(request);
		return idempotencyStore.computeIfAbsent(idempotencyKey, ignored -> ingestIntoCanonicalPipeline(request));
	}

	public IngestAcceptedResult ingestOtlpLogs(String idempotencyKey, byte[] payload) {
		throw new NotImplementedException("OTLP ingest endpoint is reserved in MVP");
	}

	private IngestAcceptedResult ingestIntoCanonicalPipeline(IngestEventsCommand request) {
		boolean projectExists = projectService.existsById(request.projectId());
		ingestRequestValidator.validate(request, projectExists);
		int receivedEvents = request.events().size();
		int deduplicatedEvents = eventDeduplicationPolicy.countDeduplicatedEvents(request.events());
		incidentService.recordIngestedEvents(request.projectId(), request.events());

		return new IngestAcceptedResult(
			true,
			UUID.randomUUID().toString(),
			receivedEvents,
			deduplicatedEvents
		);
	}

	private String pullIdempotencyKey(IngestEventsCommand request) {
		if (request == null || request.projectId() == null || request.projectId().trim().isEmpty()) {
			throw new ValidationException("project_id is required for pulled ingest");
		}
		if (request.batchId() == null || request.batchId().trim().isEmpty()) {
			throw new ValidationException("batch_id is required for pulled ingest");
		}

		String projectId = request.projectId().trim();
		String batchId = request.batchId().trim();
		return "pull:" + projectId + ":" + batchId;
	}

	private String pushIdempotencyKey(String idempotencyKey) {
		return "push:" + idempotencyKey;
	}
}
