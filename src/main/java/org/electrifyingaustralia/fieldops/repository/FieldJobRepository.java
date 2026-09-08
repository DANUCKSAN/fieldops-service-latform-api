package org.electrifyingaustralia.fieldops.repository;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Optional;
import java.util.UUID;
import org.electrifyingaustralia.fieldops.entity.FieldJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface FieldJobRepository extends JpaRepository<FieldJob, UUID> {

    boolean existsByJobIdIgnoreCase(String jobId);

    @EntityGraph(attributePaths = {
            "installer",
            "createdBy",
            "allocations",
            "allocations.product"
    })
    Optional<FieldJob> findDetailedById(UUID id);

    @EntityGraph(attributePaths = {
            "installer",
            "createdBy",
            "allocations",
            "allocations.product"
    })
    Optional<FieldJob> findDetailedByJobIdIgnoreCase(String jobId);

    @EntityGraph(attributePaths = "installer")
    Page<FieldJob> findAllBy(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(
            name = "jakarta.persistence.lock.timeout",
            value = "5000"
    ))
    @Query("select job from FieldJob job where job.id = :id")
    Optional<FieldJob> findByIdForUpdate(@Param("id") UUID id);
}
