package org.electrifyingaustralia.fieldops.repository;

import java.util.List;
import java.util.UUID;
import org.electrifyingaustralia.fieldops.entity.JobStatusHistory;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobStatusHistoryRepository
        extends JpaRepository<JobStatusHistory, UUID> {

    @EntityGraph(attributePaths = "performedBy")
    List<JobStatusHistory> findAllByJobIdOrderByCreatedAtAscIdAsc(UUID jobId);
}
