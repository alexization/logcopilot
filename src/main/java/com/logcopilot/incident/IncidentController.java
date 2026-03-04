package com.logcopilot.incident;

import com.logcopilot.common.api.ApiMeta;
import com.logcopilot.common.error.NotFoundException;
import com.logcopilot.incident.domain.IncidentDetail;
import com.logcopilot.incident.domain.IncidentListResult;
import com.logcopilot.incident.domain.IncidentSummary;
import com.logcopilot.incident.domain.ReanalyzeAcceptedResult;
import com.logcopilot.project.ProjectService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

	public IncidentController(
		IncidentService incidentService,
		ProjectService projectService
	) {
		this.incidentService = incidentService;
		this.projectService = projectService;
	}

	@GetMapping("/projects/{project_id}/incidents")
	public IncidentListResponse listIncidents(
		@PathVariable("project_id") String projectId,
		@RequestParam(value = "status", required = false) String status,
		@RequestParam(value = "service", required = false) String service,
		@RequestParam(value = "cursor", required = false) String cursor,
		@RequestParam(value = "limit", required = false) Integer limit
	) {
		validateProjectExists(projectId);

		IncidentListResult result = incidentService.list(projectId, status, service, cursor, limit);
		return new IncidentListResponse(
			result.data(),
			new ApiMeta(UUID.randomUUID().toString(), result.nextCursor())
		);
	}

	@GetMapping("/incidents/{incident_id}")
	public IncidentDetailResponse getIncident(
		@PathVariable("incident_id") String incidentId
	) {
		IncidentDetail detail = incidentService.getIncident(incidentId);
		validateProjectExists(detail.projectId());
		return new IncidentDetailResponse(detail);
	}

	@PostMapping("/incidents/{incident_id}/reanalyze")
	public ResponseEntity<ReanalyzeResponse> reanalyzeIncident(
		@PathVariable("incident_id") String incidentId,
		@Valid @RequestBody(required = false) ReanalyzeRequest request
	) {
		String reason = request == null ? null : request.reason();

		IncidentDetail detail = incidentService.getIncident(incidentId);
		validateProjectExists(detail.projectId());

		ReanalyzeAcceptedResult accepted = incidentService.reanalyzeIncident(
			incidentId,
			reason
		);
		return ResponseEntity.accepted().body(new ReanalyzeResponse(accepted));
	}

	private void validateProjectExists(String projectId) {
		if (!projectService.existsById(projectId)) {
			throw new NotFoundException("Project not found");
		}
	}

	public record ReanalyzeRequest(
		@Size(max = 500, message = "reason must be at most 500 characters")
		String reason
	) {
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
}
