package com.logcopilot.common.persistence;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "logcopilot.persistence")
public class PersistenceProperties {

	private boolean enabled = true;
	private String sqlitePath = "./data/logcopilot.sqlite";
	private String encryptionKey = "logcopilot-dev-only-change-me";

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
