package com.logcopilot.common.persistence;

import com.fasterxml.jackson.core.type.TypeReference;

import java.util.Optional;

public interface StateSnapshotRepository {

	void save(String scope, Object snapshot);

	<T> Optional<T> load(String scope, Class<T> type);

	<T> Optional<T> load(String scope, TypeReference<T> typeReference);
}
