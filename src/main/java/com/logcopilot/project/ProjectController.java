package com.logcopilot.project;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
		@RequestBody CreateProjectRequest request
	) {
		ProjectDto project = projectService.create(request.name(), request.environment());
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(new ProjectResponse(project));
	}

	@GetMapping
	public ProjectListResponse listProjects() {
		return new ProjectListResponse(projectService.list());
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
