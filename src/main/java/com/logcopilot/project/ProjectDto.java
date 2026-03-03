package com.logcopilot.project;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record ProjectDto(
	String id,
	String name,
	String environment,
	@JsonProperty("created_at")
	Instant createdAt
) {
}
