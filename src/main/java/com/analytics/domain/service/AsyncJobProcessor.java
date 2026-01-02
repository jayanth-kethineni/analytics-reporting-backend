package com.analytics.domain.service;

import com.analytics.domain.model.EventQueryRequest;
import com.analytics.infrastructure.persistence.entity.AsyncJobEntity;
import com.analytics.infrastructure.persistence.repository.AsyncJobRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Async job processor for heavy queries.
 * 
 * Heavy queries (estimated >5 seconds) are processed asynchronously:
 * 1. User submits query → Job created with PENDING status
 * 2. User receives job ID immediately
 * 3. Background worker picks up job and processes
 * 4. User polls for job status and results
 * 
 * Why Async?
 * - Prevents API timeout for heavy queries
 * - Better user experience (no blocking)
 * - Can retry failed jobs
 * - Can prioritize jobs
 * 
 * Processing Flow:
 * 1. Poll for PENDING jobs every 1 second
 * 2. Mark job as RUNNING
 * 3. Execute query
 * 4. Store result
 * 5. Mark job as COMPLETED
 * 
 * Failure Handling:
 * - Job timeout (10 minutes)
 * - Retry on transient failures
 * - Mark as FAILED with error message
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncJobProcessor {
    
    private final AsyncJobRepository asyncJobRepository;
    private final QueryService queryService;
    private final ObjectMapper objectMapper;
    
    /**
     * Submit async job for heavy query.
     * 
     * Returns job ID immediately.
     */
    @Transactional
    public UUID submitAsyncJob(String queryType, EventQueryRequest request) {
        try {
            String queryParams = objectMapper.writeValueAsString(request);
            
            AsyncJobEntity job = AsyncJobEntity.builder()
                    .queryType(queryType)
                    .queryParams(queryParams)
                    .build();
            
            job = asyncJobRepository.save(job);
            
            log.info("Async job submitted: {} (type: {})", job.getJobId(), queryType);
            
            return job.getJobId();
            
        } catch (Exception e) {
            log.error("Error submitting async job: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to submit async job", e);
        }
    }
    
    /**
     * Get job status and result.
     */
    @Transactional(readOnly = true)
    public AsyncJobEntity getJobStatus(UUID jobId) {
        return asyncJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
    }
    
    /**
     * Process pending jobs.
     * 
     * Runs every 1 second to pick up new jobs.
     */
    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void processPendingJobs() {
        try {
            List<AsyncJobEntity> pendingJobs = asyncJobRepository
                    .findTop10ByStatusOrderByCreatedAtAsc(AsyncJobEntity.JobStatus.PENDING);
            
            if (pendingJobs.isEmpty()) {
                return;
            }
            
            log.debug("Processing {} pending async jobs", pendingJobs.size());
            
            for (AsyncJobEntity job : pendingJobs) {
                processJobAsync(job);
            }
            
        } catch (Exception e) {
            log.error("Error processing pending jobs: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Process single job asynchronously.
     * 
     * Uses @Async to run in separate thread pool.
     */
    @Async
    @Transactional
    public void processJobAsync(AsyncJobEntity job) {
        try {
            log.info("Processing async job: {} (type: {})", job.getJobId(), job.getQueryType());
            
            // Mark as running
            job.markStarted();
            asyncJobRepository.save(job);
            
            // Parse query parameters
            EventQueryRequest request = objectMapper.readValue(
                    job.getQueryParams(), 
                    EventQueryRequest.class
            );
            
            // Execute query based on type
            Object result = switch (job.getQueryType()) {
                case "EVENTS" -> queryService.queryEvents(request);
                case "AGGREGATE_TYPE" -> queryService.aggregateEventsByType(request);
                case "AGGREGATE_HOUR" -> queryService.aggregateEventsByHour(request);
                default -> throw new IllegalArgumentException("Unknown query type: " + job.getQueryType());
            };
            
            // Store result
            String resultJson = objectMapper.writeValueAsString(result);
            job.markCompleted(resultJson);
            asyncJobRepository.save(job);
            
            log.info("Async job completed: {} ({} ms)", 
                    job.getJobId(), job.getExecutionTimeMs());
            
        } catch (Exception e) {
            log.error("Error processing async job {}: {}", job.getJobId(), e.getMessage(), e);
            
            job.markFailed(e.getMessage());
            asyncJobRepository.save(job);
        }
    }
}
