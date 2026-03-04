package com.logcopilot.project;

import com.logcopilot.common.error.BadRequestException;
import com.logcopilot.common.error.ConflictException;
import com.logcopilot.common.persistence.StateSnapshotRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class ProjectService {

	private static final String SNAPSHOT_SCOPE = "project-service";

	private final StateSnapshotRepository stateSnapshotRepository;
	private final Map<String, ProjectDto> projectsById = new LinkedHashMap<>();
	private final Map<String, String> projectIdByNameKey = new HashMap<>();

	public ProjectService() {
		this((StateSnapshotRepository) null);
	}

	@Autowired
	public ProjectService(ObjectProvider<StateSnapshotRepository> stateSnapshotRepositoryProvider) {
		this(stateSnapshotRepositoryProvider.getIfAvailable());
	}

	ProjectService(StateSnapshotRepository stateSnapshotRepository) {
		this.stateSnapshotRepository = stateSnapshotRepository;
		restoreState();
	}

	public synchronized ProjectDto create(String name, String environment) {
		String normalizedName = validateName(name);
		String normalizedEnvironment = validateEnvironment(environment);

		String nameKey = normalizedName.toLowerCase(Locale.ROOT);
		if (projectIdByNameKey.containsKey(nameKey)) {
			throw new ConflictException("Project name already exists");
		}

		ProjectDto project = new ProjectDto(
			UUID.randomUUID().toString(),
			normalizedName,
			normalizedEnvironment,
			Instant.now()
		);

		projectsById.put(project.id(), project);
		projectIdByNameKey.put(nameKey, project.id());
		persistState();
		return project;
	}

	public synchronized List<ProjectDto> list() {
		return List.copyOf(projectsById.values());
	}

	public synchronized boolean existsById(String projectId) {
		return projectsById.containsKey(projectId);
	}

	private String validateName(String name) {
		if (name == null) {
			throw new BadRequestException("Project name must be between 1 and 100 characters");
		}

		String trimmed = name.trim();
		if (trimmed.isEmpty() || trimmed.length() > 100) {
			throw new BadRequestException("Project name must be between 1 and 100 characters");
		}

		return trimmed;
	}

	private String validateEnvironment(String environment) {
		if (environment == null) {
			throw new BadRequestException("Environment must be one of: prod, staging, dev");
		}

		return switch (environment) {
			case "prod", "staging", "dev" -> environment;
			default -> throw new BadRequestException("Environment must be one of: prod, staging, dev");
		};
	}

	private void restoreState() {
		if (stateSnapshotRepository == null) {
			return;
		}
		stateSnapshotRepository.load(SNAPSHOT_SCOPE, ProjectSnapshot.class)
			.ifPresent(snapshot -> {
				projectsById.clear();
				projectIdByNameKey.clear();
				if (snapshot.projectsById() != null) {
					projectsById.putAll(snapshot.projectsById());
				}
				if (snapshot.projectIdByNameKey() != null) {
					projectIdByNameKey.putAll(snapshot.projectIdByNameKey());
				}
			});
	}

	private void persistState() {
		if (stateSnapshotRepository == null) {
			return;
		}
		stateSnapshotRepository.save(
			SNAPSHOT_SCOPE,
			new ProjectSnapshot(
				new LinkedHashMap<>(projectsById),
				new HashMap<>(projectIdByNameKey)
			)
		);
	}

	record ProjectSnapshot(
		Map<String, ProjectDto> projectsById,
		Map<String, String> projectIdByNameKey
	) {
	}
}
