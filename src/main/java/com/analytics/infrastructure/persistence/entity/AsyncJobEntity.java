package com.analytics.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity for tracking async query jobs.
 * 
 * Heavy queries (>5 seconds) are processed asynchronously.
 * Users poll this table for job status and results.
 */
@Entity
@Table(name = "async_jobs", indexes = {
    @Index(name = "idx_job_id", columnList = "jobId"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_created_at", columnList = "createdAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsyncJobEntity {
    
    @Id
    @Column(columnDefinition = "UUID")
    private UUID jobId;
    
    @Column(nullable = false, length = 50)
    private String queryType;
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String queryParams;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private JobStatus status = JobStatus.PENDING;
    
    @Column(columnDefinition = "TEXT")
    private String result;
    
    @Column(length = 500)
    private String errorMessage;
    
    @Column(nullable = false)
    private Instant createdAt;
    
    @Column
    private Instant startedAt;
    
    @Column
    private Instant completedAt;
    
    public enum JobStatus {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED
    }
    
    @PrePersist
    protected void onCreate() {
        if (jobId == null) {
            jobId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
    
    public void markStarted() {
        this.status = JobStatus.RUNNING;
        this.startedAt = Instant.now();
    }
    
    public void markCompleted(String result) {
        this.status = JobStatus.COMPLETED;
        this.result = result;
        this.completedAt = Instant.now();
    }
    
    public void markFailed(String error) {
        this.status = JobStatus.FAILED;
        this.errorMessage = error;
        this.completedAt = Instant.now();
    }
    
    public long getExecutionTimeMs() {
        if (startedAt == null || completedAt == null) {
            return 0;
        }
        return completedAt.toEpochMilli() - startedAt.toEpochMilli();
    }
}
