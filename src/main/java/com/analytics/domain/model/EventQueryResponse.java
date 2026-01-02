package com.analytics.domain.model;

import com.analytics.infrastructure.persistence.entity.EventEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Response model for event queries.
 * 
 * Includes cursor for next page (cursor-based pagination).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventQueryResponse {
    
    private List<EventEntity> events;
    private UUID nextCursor;
    private boolean hasMore;
    private long totalCount;
    private boolean cached;
    private long queryTimeMs;
}
