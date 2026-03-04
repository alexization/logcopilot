package com.logcopilot.common.persistence;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersistencePropertiesTest {

	@Test
	void failsValidationWhenPersistenceEnabledAndKeyMissing() {
		PersistenceProperties properties = new PersistenceProperties();
		properties.setEnabled(true);
		properties.setEncryptionKey(" ");

		assertThatThrownBy(properties::validate)
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("logcopilot.persistence.encryption-key must be configured when persistence is enabled");
	}

	@Test
	void allowsMissingKeyWhenPersistenceDisabled() {
		PersistenceProperties properties = new PersistenceProperties();
		properties.setEnabled(false);
		properties.setEncryptionKey(null);

		assertThatCode(properties::validate).doesNotThrowAnyException();
	}
}
