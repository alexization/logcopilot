package com.logcopilot.common.persistence;

import java.util.Map;
import java.util.Optional;

public interface TokenHashStore {

	void ensureDefaults(Map<String, String> tokenTypeByPlainToken);

	Optional<String> findTokenType(String plainToken);
}
