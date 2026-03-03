package com.logcopilot.ingest.domain;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class EventDeduplicationPolicy {

	public int countDeduplicatedEvents(List<CanonicalLogEvent> events) {
		Set<String> seenEventIds = new HashSet<>();
		int deduplicated = 0;

		for (CanonicalLogEvent event : events) {
			if (!seenEventIds.add(event.eventId())) {
				deduplicated++;
			}
		}
		return deduplicated;
	}
}
