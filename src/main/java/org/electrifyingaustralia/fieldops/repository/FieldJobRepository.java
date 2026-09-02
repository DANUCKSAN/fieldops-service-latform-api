package org.electrifyingaustralia.fieldops.repository;

import java.util.Optional;
import java.util.UUID;
import org.electrifyingaustralia.fieldops.entity.FieldJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
