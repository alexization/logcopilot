package com.logcopilot.common.persistence;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "logcopilot.persistence")
public class PersistenceProperties {

	private boolean enabled = true;
	private String sqlitePath = "./data/logcopilot.sqlite";
	private String encryptionKey;

	@PostConstruct
	void validate() {
		if (!enabled) {
			return;
		}
		if (encryptionKey == null || encryptionKey.isBlank()) {
			throw new IllegalStateException(
				"logcopilot.persistence.encryption-key must be configured when persistence is enabled"
			);
		}
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getSqlitePath() {
		return sqlitePath;
	}

	public void setSqlitePath(String sqlitePath) {
		this.sqlitePath = sqlitePath;
	}

	public String getEncryptionKey() {
		return encryptionKey;
	}

	public void setEncryptionKey(String encryptionKey) {
		this.encryptionKey = encryptionKey;
	}
}
