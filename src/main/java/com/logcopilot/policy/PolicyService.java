package com.logcopilot.policy;

import com.logcopilot.common.error.BadRequestException;
import com.logcopilot.project.ProjectService;
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

	private static final int MAX_REDACTION_RULES = 200;
	private static final Pattern NESTED_QUANTIFIER_PATTERN =
		Pattern.compile(
			"\\((?:[^()\\\\]|\\\\.)*(?:[+*?]|\\{\\d+(?:,\\d*)?\\})(?:[^()\\\\]|\\\\.)*\\)(?:[+*?]|\\{\\d+(?:,\\d*)?\\})"
		);

	private final ProjectService projectService;
	private final Map<String, ExportPolicyState> exportPolicyByProject = new HashMap<>();
	private final Map<String, RedactionPolicyState> redactionPolicyByProject = new HashMap<>();

	public PolicyService(ProjectService projectService) {
		this.projectService = projectService;
	}

	public synchronized ExportPolicyResult updateExportPolicy(String projectId, ExportPolicyCommand command) {
		requireProject(projectId);
		if (command == null) {
			throw new BadRequestException("Request body must not be null");
		}

		String level = validateExportLevel(command.level());
		ExportPolicyState updated = new ExportPolicyState(level, Instant.now());
		exportPolicyByProject.put(projectId, updated);
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
		return new RedactionPolicyResult(updated.enabled(), updated.rules().size(), updated.updatedAt());
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

	private record ExportPolicyState(
		String level,
		Instant updatedAt
	) {
	}

	private record RedactionPolicyState(
		boolean enabled,
		List<RedactionRuleState> rules,
		Instant updatedAt
	) {
	}

	private record RedactionRuleState(
		String name,
		String pattern,
		String replaceWith
	) {
	}
}
