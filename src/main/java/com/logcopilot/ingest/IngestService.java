package com.logcopilot.ingest;

import com.logcopilot.common.error.NotImplementedException;
import com.logcopilot.ingest.domain.EventDeduplicationPolicy;
import com.logcopilot.ingest.domain.IngestAcceptedResult;
import com.logcopilot.ingest.domain.IngestEventsCommand;
import com.logcopilot.ingest.domain.IngestRequestValidator;
import com.logcopilot.project.ProjectService;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class IngestService {

	private final ProjectService projectService;
	private final IngestIdempotencyStore idempotencyStore;
	private final IngestRequestValidator ingestRequestValidator;
	private final EventDeduplicationPolicy eventDeduplicationPolicy;

	public IngestService(
		ProjectService projectService,
		IngestIdempotencyStore idempotencyStore,
		IngestRequestValidator ingestRequestValidator,
		EventDeduplicationPolicy eventDeduplicationPolicy
	) {
		this.projectService = projectService;
		this.idempotencyStore = idempotencyStore;
		this.ingestRequestValidator = ingestRequestValidator;
		this.eventDeduplicationPolicy = eventDeduplicationPolicy;
	}

	public synchronized IngestAcceptedResult ingestEvents(String idempotencyKey, IngestEventsCommand request) {
		IngestAcceptedResult existing = idempotencyStore.find(idempotencyKey).orElse(null);
		if (existing != null) {
			return existing;
		}

		boolean projectExists = projectService.existsById(request.projectId());
		ingestRequestValidator.validate(request, projectExists);
		int receivedEvents = request.events().size();
		int deduplicatedEvents = eventDeduplicationPolicy.countDeduplicatedEvents(request.events());

		IngestAcceptedResult accepted = new IngestAcceptedResult(
			true,
			UUID.randomUUID().toString(),
			receivedEvents,
			deduplicatedEvents
		);
		idempotencyStore.save(idempotencyKey, accepted);
		return accepted;
	}

	public IngestAcceptedResult ingestOtlpLogs(String idempotencyKey, byte[] payload) {
		throw new NotImplementedException("OTLP ingest endpoint is reserved in MVP");
	}
}
