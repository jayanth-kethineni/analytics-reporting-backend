package com.analytics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Scalable Analytics & Reporting Backend
 * 
 * Production-grade analytics backend that supports large datasets without killing performance.
 * 
 * Architecture:
 * - REST APIs for analytics queries
 * - Cursor-based pagination for large result sets
 * - Async job processing for heavy queries
 * - Redis caching for expensive queries
 * - Query optimization and indexing
 * - Separation of OLTP vs OLAP workloads
 * 
 * Scale Targets:
 * - 200 QPS sustained (1,000 QPS peak)
 * - 100B+ rows in dataset
 * - p95 latency < 1s for sync queries
 * - 10-60 seconds for async jobs
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class AnalyticsReportingApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnalyticsReportingApplication.class, args);
    }
}
