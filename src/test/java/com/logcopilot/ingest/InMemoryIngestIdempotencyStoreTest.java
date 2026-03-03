package com.logcopilot.ingest;

import com.logcopilot.ingest.domain.IngestAcceptedResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryIngestIdempotencyStoreTest {

	@Test
	@DisplayName("InMemoryIngestIdempotencyStore는 키-결과를 저장하고 조회한다")
	void savesAndFindsAcceptedResult() {
		InMemoryIngestIdempotencyStore store = new InMemoryIngestIdempotencyStore(10);
		IngestAcceptedResult accepted = new IngestAcceptedResult(true, "ing-1", 2, 1);

		store.save("key-1", accepted);

		assertThat(store.find("key-1")).contains(accepted);
	}

	@Test
	@DisplayName("InMemoryIngestIdempotencyStore는 최대 크기 초과 시 가장 오래된 항목을 제거한다")
	void evictsOldestEntryWhenMaxSizeExceeded() {
		InMemoryIngestIdempotencyStore store = new InMemoryIngestIdempotencyStore(2);
		store.save("key-1", new IngestAcceptedResult(true, "ing-1", 1, 0));
		store.save("key-2", new IngestAcceptedResult(true, "ing-2", 1, 0));
		store.save("key-3", new IngestAcceptedResult(true, "ing-3", 1, 0));

		assertThat(store.find("key-1")).isEmpty();
		assertThat(store.find("key-2")).isPresent();
		assertThat(store.find("key-3")).isPresent();
	}

	@Test
	@DisplayName("InMemoryIngestIdempotencyStore는 computeIfAbsent를 원자적으로 처리한다")
	void computesValueOnlyWhenAbsent() {
		InMemoryIngestIdempotencyStore store = new InMemoryIngestIdempotencyStore(10);
		IngestAcceptedResult first = store.computeIfAbsent("key-1", key -> new IngestAcceptedResult(true, "ing-1", 1, 0));
		IngestAcceptedResult second = store.computeIfAbsent("key-1", key -> new IngestAcceptedResult(true, "ing-2", 2, 1));

		assertThat(first.ingestionId()).isEqualTo("ing-1");
		assertThat(second.ingestionId()).isEqualTo("ing-1");
		assertThat(store.find("key-1")).contains(first);
	}

	@Test
	@DisplayName("InMemoryIngestIdempotencyStore는 maxSize가 1 미만이면 예외를 던진다")
	void throwsWhenMaxSizeIsLessThanOne() {
		assertThatThrownBy(() -> new InMemoryIngestIdempotencyStore(0))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("maxSize must be >= 1");
	}
}
