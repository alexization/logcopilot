package com.logcopilot.common.metrics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SloMetricsCalculator {

	private SloMetricsCalculator() {
	}

	public static long percentileMillis(List<Long> latenciesMillis, double percentile) {
		if (latenciesMillis == null || latenciesMillis.isEmpty()) {
			throw new IllegalArgumentException("latencies must not be empty");
		}
		if (Double.isNaN(percentile) || percentile <= 0.0 || percentile > 100.0) {
			throw new IllegalArgumentException("percentile must be in range (0, 100]");
		}

		List<Long> sorted = new ArrayList<>(latenciesMillis.size());
		for (Long latency : latenciesMillis) {
			if (latency == null) {
				throw new IllegalArgumentException("latencies must not contain null");
			}
			sorted.add(latency);
		}
		sorted.sort(Comparator.naturalOrder());

		int rank = (int) Math.ceil((percentile / 100.0) * sorted.size());
		int index = Math.max(rank - 1, 0);
		return sorted.get(index);
	}

	public static double successRatePercent(int successCount, int totalCount) {
		if (totalCount <= 0) {
			throw new IllegalArgumentException("totalCount must be positive");
		}
		if (successCount < 0 || successCount > totalCount) {
			throw new IllegalArgumentException("successCount must be between 0 and totalCount");
		}
		return (successCount * 100.0) / totalCount;
	}
}
