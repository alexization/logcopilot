package com.logcopilot.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdminUiRuntimeBehaviorTest {

	@Test
	@DisplayName("admin app script 의 비동기 컨텍스트 가드는 런타임에서 회귀 없이 동작한다")
	void adminAppRuntimeChecksPass() throws Exception {
		Path scriptPath = Path.of("src", "test", "resources", "admin-ui-runtime-check.js");
		Process process = new ProcessBuilder("node", scriptPath.toString())
			.directory(Path.of(".").toFile())
			.redirectErrorStream(true)
			.start();

		String output = readOutput(process);
		int exitCode = process.waitFor();

		assertEquals(0, exitCode, output);
	}

	private String readOutput(Process process) throws IOException {
		try (var input = process.getInputStream()) {
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
