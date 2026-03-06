package com.logcopilot.policy;

import com.logcopilot.common.error.BadRequestException;
import com.logcopilot.common.persistence.StateSnapshotRepository;
import com.logcopilot.common.security.SensitiveDataSanitizer;
import com.logcopilot.project.ProjectService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Service
public class PolicyService {

	private static final String SNAPSHOT_SCOPE = "policy-service";
	private static final int MAX_REDACTION_RULES = 200;
	private static final int MAX_REGEX_PATTERN_LENGTH = 512;
	private static final String DEFAULT_EXPORT_LEVEL = "level1_byom_only";
	private static final boolean DEFAULT_REDACTION_ENABLED = true;
	private static final Pattern NESTED_QUANTIFIER_PATTERN =
		Pattern.compile(
			"\\((?:[^()\\\\]|\\\\.)*(?:[+*?]|\\{\\d+(?:,\\d*)?\\})(?:[^()\\\\]|\\\\.)*\\)(?:[+*?]|\\{\\d+(?:,\\d*)?\\})"
		);

	private final ProjectService projectService;
	private final StateSnapshotRepository stateSnapshotRepository;
	private final Map<String, ExportPolicyState> exportPolicyByProject = new HashMap<>();
	private final Map<String, RedactionPolicyState> redactionPolicyByProject = new HashMap<>();

	public PolicyService(ProjectService projectService) {
		this(projectService, (StateSnapshotRepository) null);
	}

	@Autowired
	public PolicyService(
		ProjectService projectService,
		ObjectProvider<StateSnapshotRepository> stateSnapshotRepositoryProvider
	) {
		this(projectService, stateSnapshotRepositoryProvider.getIfAvailable());
	}

	PolicyService(ProjectService projectService, StateSnapshotRepository stateSnapshotRepository) {
		this.projectService = projectService;
		this.stateSnapshotRepository = stateSnapshotRepository;
		restoreState();
	}

	public synchronized ExportPolicyResult updateExportPolicy(String projectId, ExportPolicyCommand command) {
		requireProject(projectId);
		if (command == null) {
			throw new BadRequestException("Request body must not be null");
		}

		String level = validateExportLevel(command.level());
		ExportPolicyState updated = new ExportPolicyState(level, Instant.now());
		exportPolicyByProject.put(projectId, updated);
		persistState();
		return new ExportPolicyResult(updated.level(), updated.updatedAt());
	}

	public synchronized RedactionPolicyResult updateRedactionPolicy(
		String projectId,
		RedactionPolicyCommand command
	) {
		requireProject(projectId);
		if (command == null) {
			throw new BadRequestException("Request body must not be null");
		}

		boolean enabled = validateEnabled(command.enabled());
		List<RedactionRuleState> normalizedRules = normalizeAndValidateRules(command.rules());
		RedactionPolicyState updated = new RedactionPolicyState(enabled, normalizedRules, Instant.now());
		redactionPolicyByProject.put(projectId, updated);
		persistState();
		return new RedactionPolicyResult(updated.enabled(), updated.rules().size(), updated.updatedAt());
	}

	public synchronized String redactForLlm(String projectId, String rawText) {
		requireProject(projectId);
		RedactionPolicyState policy = redactionPolicyByProject.get(projectId);
		if (policy == null) {
			throw new IllegalStateException("Redaction policy is not configured");
		}
		if (!policy.enabled()) {
			throw new IllegalStateException("Redaction policy is disabled");
		}
		if (policy.rules().isEmpty()) {
			throw new IllegalStateException("Redaction policy has no active rules");
		}
		if (rawText == null || rawText.isEmpty()) {
			return rawText;
		}

		String redacted = rawText;
		for (RedactionRuleState rule : policy.rules()) {
			try {
				redacted = redacted.replaceAll(rule.pattern(), rule.replaceWith());
			} catch (RuntimeException exception) {
				throw new IllegalStateException(
					"Redaction rule execution failed: " + rule.name(),
					exception
				);
			}
		}
		if (SensitiveDataSanitizer.containsUnmaskedSensitiveValue(redacted)) {
			throw new IllegalStateException("Redaction did not mask all sensitive values");
		}
		return redacted;
	}

	public synchronized ExportPolicyView getExportPolicy(String projectId) {
		requireProject(projectId);
		ExportPolicyState configured = exportPolicyByProject.get(projectId);
		if (configured == null) {
			return new ExportPolicyView(false, DEFAULT_EXPORT_LEVEL, null);
		}
		return new ExportPolicyView(true, configured.level(), configured.updatedAt());
	}

	public synchronized RedactionPolicyView getRedactionPolicy(String projectId) {
		requireProject(projectId);
		RedactionPolicyState configured = redactionPolicyByProject.get(projectId);
		if (configured == null) {
			return new RedactionPolicyView(false, DEFAULT_REDACTION_ENABLED, List.of(), null);
		}
		List<RedactionRuleView> rules = configured.rules().stream()
			.map(rule -> new RedactionRuleView(rule.name(), rule.pattern(), rule.replaceWith()))
			.toList();
		return new RedactionPolicyView(
			true,
			configured.enabled(),
			rules,
			configured.updatedAt()
		);
	}

	private void requireProject(String projectId) {
		if (projectId == null || projectId.isBlank() || !projectService.existsById(projectId)) {
			throw new BadRequestException("Project not found");
		}
	}

	private String validateExportLevel(String level) {
		if (level == null) {
			throw new BadRequestException(
				"level must be one of: level0_rule_only, level1_byom_only, level2_byom_with_telemetry"
			);
		}
		String normalized = level.trim().toLowerCase(Locale.ROOT);
		return switch (normalized) {
			case "level0_rule_only", "level1_byom_only", "level2_byom_with_telemetry" -> normalized;
			default -> throw new BadRequestException(
				"level must be one of: level0_rule_only, level1_byom_only, level2_byom_with_telemetry"
			);
		};
	}

	private boolean validateEnabled(Boolean enabled) {
		if (enabled == null) {
			throw new BadRequestException("enabled must not be null");
		}
		return enabled;
	}

	private List<RedactionRuleState> normalizeAndValidateRules(List<RedactionRuleCommand> rules) {
		if (rules == null) {
			throw new BadRequestException("rules must not be null");
		}
		if (rules.size() > MAX_REDACTION_RULES) {
			throw new BadRequestException("rules must contain at most 200 items");
		}

		List<RedactionRuleState> normalized = new ArrayList<>(rules.size());
		for (int index = 0; index < rules.size(); index++) {
			RedactionRuleCommand rule = rules.get(index);
			if (rule == null) {
				throw new BadRequestException("rules[%d] must not be null".formatted(index));
			}

			String name = requireNonBlank(rule.name(), "rules[%d].name must not be blank".formatted(index));
			String pattern = requireNonBlank(
				rule.pattern(),
				"rules[%d].pattern must not be blank".formatted(index)
			);
			String replaceWith = requireNonBlank(
				rule.replaceWith(),
				"rules[%d].replace_with must not be blank".formatted(index)
			);

			validateRegex(index, pattern);
			normalized.add(new RedactionRuleState(name, pattern, replaceWith));
		}
		return List.copyOf(normalized);
	}

	private String requireNonBlank(String value, String message) {
		if (value == null) {
			throw new BadRequestException(message);
		}

		String trimmed = value.trim();
		if (trimmed.isEmpty()) {
			throw new BadRequestException(message);
		}
		return trimmed;
	}

	private void validateRegex(int index, String pattern) {
		if (pattern.length() > MAX_REGEX_PATTERN_LENGTH) {
			throw new BadRequestException(
				"rules[%d].pattern must be at most %d characters".formatted(index, MAX_REGEX_PATTERN_LENGTH)
			);
		}

		if (NESTED_QUANTIFIER_PATTERN.matcher(pattern).find()) {
			throw new BadRequestException(
				"rules[%d].pattern contains disallowed nested quantifier".formatted(index)
			);
		}

		try {
			Pattern.compile(pattern);
		} catch (PatternSyntaxException exception) {
			throw new BadRequestException("rules[%d].pattern must be a valid regex".formatted(index));
		}
	}

	public record ExportPolicyCommand(String level) {
	}

	public record RedactionPolicyCommand(
		Boolean enabled,
		List<RedactionRuleCommand> rules
	) {
	}

	public record RedactionRuleCommand(
		String name,
		String pattern,
		String replaceWith
	) {
	}

	public record ExportPolicyResult(
		String level,
		Instant updatedAt
	) {
	}

	public record RedactionPolicyResult(
		boolean enabled,
		int rulesCount,
		Instant updatedAt
	) {
	}

	public record ExportPolicyView(
		boolean configured,
		String level,
		Instant updatedAt
	) {
	}

	public record RedactionPolicyView(
		boolean configured,
		boolean enabled,
		List<RedactionRuleView> rules,
		Instant updatedAt
	) {
	}

	public record RedactionRuleView(
		String name,
		String pattern,
		String replaceWith
	) {
	}

	record ExportPolicyState(
		String level,
		Instant updatedAt
	) {
	}

	record RedactionPolicyState(
		boolean enabled,
		List<RedactionRuleState> rules,
		Instant updatedAt
	) {
	}

	record RedactionRuleState(
		String name,
		String pattern,
		String replaceWith
	) {
	}

	private void restoreState() {
		if (stateSnapshotRepository == null) {
			return;
		}
		stateSnapshotRepository.load(SNAPSHOT_SCOPE, PolicySnapshot.class)
			.ifPresent(snapshot -> {
				exportPolicyByProject.clear();
				redactionPolicyByProject.clear();
				if (snapshot.exportPolicyByProject() != null) {
					exportPolicyByProject.putAll(snapshot.exportPolicyByProject());
				}
				if (snapshot.redactionPolicyByProject() != null) {
					redactionPolicyByProject.putAll(snapshot.redactionPolicyByProject());
				}
			});
	}

	private void persistState() {
		if (stateSnapshotRepository == null) {
			return;
		}
		stateSnapshotRepository.save(
			SNAPSHOT_SCOPE,
			new PolicySnapshot(
				new HashMap<>(exportPolicyByProject),
				new HashMap<>(redactionPolicyByProject)
			)
		);
	}

	record PolicySnapshot(
		Map<String, ExportPolicyState> exportPolicyByProject,
		Map<String, RedactionPolicyState> redactionPolicyByProject
	) {
	}
}
