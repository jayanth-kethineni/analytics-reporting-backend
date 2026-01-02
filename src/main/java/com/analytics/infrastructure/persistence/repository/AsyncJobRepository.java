package com.analytics.infrastructure.persistence.repository;

import com.analytics.infrastructure.persistence.entity.AsyncJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AsyncJobRepository extends JpaRepository<AsyncJobEntity, UUID> {
    
    List<AsyncJobEntity> findTop10ByStatusOrderByCreatedAtAsc(AsyncJobEntity.JobStatus status);
}
