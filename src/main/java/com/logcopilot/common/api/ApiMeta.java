package com.logcopilot.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

public record ApiMeta(
	@JsonProperty("request_id")
	String requestId,
	@JsonInclude(JsonInclude.Include.NON_NULL)
	@JsonProperty("next_cursor")
	String nextCursor
) {
}
