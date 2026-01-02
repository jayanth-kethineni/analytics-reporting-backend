package com.analytics.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing an analytics event.
 * 
 * This table can grow to 100B+ rows, so indexing strategy is critical.
 * 
 * Indexing Strategy:
 * - Composite index on (userId, timestamp) for user-specific queries
 * - Index on timestamp for time-range queries
 * - Index on eventType for filtering
 * - Partitioning by timestamp (monthly) for large datasets
 */
@Entity
@Table(name = "events", indexes = {
    @Index(name = "idx_user_timestamp", columnList = "userId,timestamp"),
    @Index(name = "idx_timestamp", columnList = "timestamp"),
    @Index(name = "idx_event_type", columnList = "eventType"),
    @Index(name = "idx_cursor", columnList = "eventId")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventEntity {
    
    @Id
    @Column(columnDefinition = "UUID")
    private UUID eventId;
    
    @Column(nullable = false, columnDefinition = "UUID")
    private UUID userId;
    
    @Column(nullable = false, length = 50)
    private String eventType;
    
    @Column(nullable = false, length = 100)
    private String source;
    
    @Column(columnDefinition = "TEXT")
    private String properties;
    
    @Column(nullable = false)
    private Instant timestamp;
    
    @Column(nullable = false)
    private Instant createdAt;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
