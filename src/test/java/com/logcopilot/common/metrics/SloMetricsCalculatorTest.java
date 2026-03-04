package com.logcopilot.common.metrics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SloMetricsCalculatorTest {

	@Test
	@DisplayName("p95 계산은 nearest-rank 방식으로 수행한다")
	void percentileMillisUsesNearestRank() {
		long p95 = SloMetricsCalculator.percentileMillis(
			List.of(20L, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L, 13L, 14L, 15L, 16L, 17L, 18L, 19L),
			95.0
		);

		assertThat(p95).isEqualTo(19L);
	}

	@Test
	@DisplayName("성공률 계산은 퍼센트 값으로 반환한다")
	void successRatePercentReturnsRate() {
		double successRate = SloMetricsCalculator.successRatePercent(199, 200);

		assertThat(successRate).isEqualTo(99.5);
	}

	@Test
	@DisplayName("입력 샘플이 없으면 percentile 계산은 예외를 던진다")
	void percentileMillisThrowsWhenNoSamples() {
		assertThatThrownBy(() -> SloMetricsCalculator.percentileMillis(List.of(), 95.0))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("latencies must not be empty");
	}

	@Test
	@DisplayName("percentile 값이 NaN이면 예외를 던진다")
	void percentileMillisThrowsWhenPercentileIsNaN() {
		assertThatThrownBy(() -> SloMetricsCalculator.percentileMillis(List.of(1L, 2L), Double.NaN))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("percentile must be in range");
	}

	@Test
	@DisplayName("샘플에 null 값이 있으면 예외를 던진다")
	void percentileMillisThrowsWhenSamplesContainNull() {
		assertThatThrownBy(() -> SloMetricsCalculator.percentileMillis(Arrays.asList(1L, null, 2L), 95.0))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("latencies must not contain null");
	}

	@Test
	@DisplayName("success rate 입력 범위가 잘못되면 예외를 던진다")
	void successRatePercentThrowsWhenInputIsInvalid() {
		assertThatThrownBy(() -> SloMetricsCalculator.successRatePercent(-1, 100))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("successCount");
		assertThatThrownBy(() -> SloMetricsCalculator.successRatePercent(101, 100))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("successCount");
		assertThatThrownBy(() -> SloMetricsCalculator.successRatePercent(0, 0))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("totalCount");
	}
}
