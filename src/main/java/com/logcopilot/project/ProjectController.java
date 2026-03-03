package com.logcopilot.project;

import com.logcopilot.common.error.UnauthorizedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/projects")
public class ProjectController {

	private final ProjectService projectService;

	public ProjectController(ProjectService projectService) {
		this.projectService = projectService;
	}

	@PostMapping
	public ResponseEntity<ProjectResponse> createProject(
		@RequestHeader(value = "Authorization", required = false) String authorization,
		@RequestBody CreateProjectRequest request
	) {
		validateBearerToken(authorization);
		ProjectDto project = projectService.create(request.name(), request.environment());
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(new ProjectResponse(project));
	}

	@GetMapping
	public ProjectListResponse listProjects(
		@RequestHeader(value = "Authorization", required = false) String authorization
	) {
		validateBearerToken(authorization);
		return new ProjectListResponse(projectService.list());
	}

	private void validateBearerToken(String authorization) {
		if (authorization == null || !authorization.startsWith("Bearer ")) {
			throw new UnauthorizedException("Missing or invalid bearer token");
		}

		String token = authorization.substring(7).trim();
		if (token.isEmpty()) {
			throw new UnauthorizedException("Missing or invalid bearer token");
		}
	}

	public record CreateProjectRequest(
		String name,
		String environment
	) {
	}

	public record ProjectResponse(ProjectDto data) {
	}

	public record ProjectListResponse(List<ProjectDto> data) {
	}
}
