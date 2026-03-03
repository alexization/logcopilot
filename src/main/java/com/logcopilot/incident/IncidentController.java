package com.logcopilot.incident;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.logcopilot.common.auth.BearerTokenValidator;
import com.logcopilot.common.error.NotFoundException;
import com.logcopilot.incident.domain.IncidentDetail;
import com.logcopilot.incident.domain.IncidentListResult;
import com.logcopilot.incident.domain.IncidentSummary;
import com.logcopilot.incident.domain.ReanalyzeAcceptedResult;
import com.logcopilot.project.ProjectService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1")
public class IncidentController {

	private final IncidentService incidentService;
	private final ProjectService projectService;
	private final BearerTokenValidator bearerTokenValidator;

	public IncidentController(
		IncidentService incidentService,
		ProjectService projectService,
		BearerTokenValidator bearerTokenValidator
	) {
		this.incidentService = incidentService;
		this.projectService = projectService;
		this.bearerTokenValidator = bearerTokenValidator;
	}

	@GetMapping("/projects/{project_id}/incidents")
	public IncidentListResponse listIncidents(
		@RequestHeader(value = "Authorization", required = false) String authorization,
		@PathVariable("project_id") String projectId,
		@RequestParam(value = "status", required = false) String status,
		@RequestParam(value = "service", required = false) String service,
		@RequestParam(value = "cursor", required = false) String cursor,
		@RequestParam(value = "limit", required = false) Integer limit
	) {
		bearerTokenValidator.validate(authorization);
		if (!projectService.existsById(projectId)) {
			throw new NotFoundException("Project not found");
		}

		IncidentListResult result = incidentService.list(projectId, status, service, cursor, limit);
		return new IncidentListResponse(
			result.data(),
			new ApiMeta(UUID.randomUUID().toString(), result.nextCursor())
		);
	}

	@GetMapping("/incidents/{incident_id}")
	public IncidentDetailResponse getIncident(
		@RequestHeader(value = "Authorization", required = false) String authorization,
		@PathVariable("incident_id") String incidentId
	) {
		bearerTokenValidator.validate(authorization);
		IncidentDetail detail = incidentService.getIncident(incidentId);
		return new IncidentDetailResponse(detail);
	}

	@PostMapping("/incidents/{incident_id}/reanalyze")
	public ResponseEntity<ReanalyzeResponse> reanalyzeIncident(
		@RequestHeader(value = "Authorization", required = false) String authorization,
		@PathVariable("incident_id") String incidentId,
		@RequestBody(required = false) ReanalyzeRequest request
	) {
		bearerTokenValidator.validate(authorization);
		ReanalyzeAcceptedResult accepted = incidentService.reanalyzeIncident(
			incidentId,
			request == null ? null : request.reason()
		);
		return ResponseEntity.accepted().body(new ReanalyzeResponse(accepted));
	}

	public record ReanalyzeRequest(String reason) {
	}

	public record IncidentListResponse(
		List<IncidentSummary> data,
		ApiMeta meta
	) {
	}

	public record IncidentDetailResponse(IncidentDetail data) {
	}

	public record ReanalyzeResponse(ReanalyzeAcceptedResult data) {
	}

	public record ApiMeta(
		@JsonProperty("request_id")
		String requestId,
		@JsonInclude(JsonInclude.Include.NON_NULL)
		@JsonProperty("next_cursor")
		String nextCursor
	) {
	}
}
