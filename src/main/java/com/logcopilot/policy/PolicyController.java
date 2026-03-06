package com.logcopilot.policy;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.logcopilot.common.error.BadRequestException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/v1/projects/{project_id}/policies")
public class PolicyController {

	private final PolicyService policyService;

	public PolicyController(PolicyService policyService) {
		this.policyService = policyService;
	}

	@GetMapping("/export")
	public ExportPolicyDetailResponse getExportPolicy(
		@PathVariable("project_id") String projectId
	) {
		PolicyService.ExportPolicyView policy = policyService.getExportPolicy(projectId);
		return new ExportPolicyDetailResponse(new ExportPolicyDetailData(
			policy.configured(),
			policy.level(),
			policy.updatedAt()
		));
	}

	@GetMapping("/redaction")
	public RedactionPolicyDetailResponse getRedactionPolicy(
		@PathVariable("project_id") String projectId
	) {
		PolicyService.RedactionPolicyView policy = policyService.getRedactionPolicy(projectId);
		List<RedactionRuleData> rules = policy.rules().stream()
			.map(rule -> new RedactionRuleData(
				rule.name(),
				rule.pattern(),
				rule.replaceWith()
			))
			.toList();
		return new RedactionPolicyDetailResponse(new RedactionPolicyDetailData(
			policy.configured(),
			policy.enabled(),
			rules.size(),
			rules,
			policy.updatedAt()
		));
	}

	@PutMapping("/export")
	public ExportPolicyResponse updateExportPolicy(
		@PathVariable("project_id") String projectId,
		@RequestBody(required = false) ExportPolicyRequest request
	) {
		if (request == null) {
			throw new BadRequestException("Malformed JSON request body");
		}

		PolicyService.ExportPolicyResult result = policyService.updateExportPolicy(
			projectId,
			new PolicyService.ExportPolicyCommand(request.level())
		);
		return new ExportPolicyResponse(new ExportPolicyData(result.level(), result.updatedAt()));
	}

	@PutMapping("/redaction")
	public RedactionPolicyResponse updateRedactionPolicy(
		@PathVariable("project_id") String projectId,
		@RequestBody(required = false) RedactionPolicyRequest request
	) {
		if (request == null) {
			throw new BadRequestException("Malformed JSON request body");
		}

		List<PolicyService.RedactionRuleCommand> rules = request.rules() == null
			? null
			: request.rules().stream()
				.map(rule -> new PolicyService.RedactionRuleCommand(
					rule == null ? null : rule.name(),
					rule == null ? null : rule.pattern(),
					rule == null ? null : rule.replaceWith()
				))
				.toList();

		PolicyService.RedactionPolicyResult result = policyService.updateRedactionPolicy(
			projectId,
			new PolicyService.RedactionPolicyCommand(request.enabled(), rules)
		);
		return new RedactionPolicyResponse(new RedactionPolicyData(
			result.enabled(),
			result.rulesCount(),
			result.updatedAt()
		));
	}

	public record ExportPolicyRequest(String level) {
	}

	public record RedactionPolicyRequest(
		Boolean enabled,
		List<RedactionRuleRequest> rules
	) {
	}

	public record RedactionRuleRequest(
		String name,
		String pattern,
		@JsonProperty("replace_with")
		String replaceWith
	) {
	}

	public record ExportPolicyResponse(ExportPolicyData data) {
	}

	public record ExportPolicyData(
		String level,
		@JsonProperty("updated_at")
		Instant updatedAt
	) {
	}

	public record RedactionPolicyResponse(RedactionPolicyData data) {
	}

	public record ExportPolicyDetailResponse(ExportPolicyDetailData data) {
	}

	public record ExportPolicyDetailData(
		boolean configured,
		String level,
		@JsonProperty("updated_at")
		Instant updatedAt
	) {
	}

	public record RedactionPolicyDetailResponse(RedactionPolicyDetailData data) {
	}

	public record RedactionPolicyDetailData(
		boolean configured,
		boolean enabled,
		@JsonProperty("rules_count")
		int rulesCount,
		List<RedactionRuleData> rules,
		@JsonProperty("updated_at")
		Instant updatedAt
	) {
	}

	public record RedactionRuleData(
		String name,
		String pattern,
		@JsonProperty("replace_with")
		String replaceWith
	) {
	}

	public record RedactionPolicyData(
		boolean enabled,
		@JsonProperty("rules_count")
		int rulesCount,
		@JsonProperty("updated_at")
		Instant updatedAt
	) {
	}
}
