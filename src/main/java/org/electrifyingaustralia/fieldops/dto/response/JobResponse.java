package org.electrifyingaustralia.fieldops.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.electrifyingaustralia.fieldops.entity.FieldJob;
import org.electrifyingaustralia.fieldops.enums.JobStatus;

public record JobResponse(
        UUID id,
        String jobId,
        String customerName,
        String location,
        LocalDate jobDate,
        JobStatus status,
        InstallerResponse installer,
        List<JobMaterialResponse> materials,
        Instant createdAt,
        Instant updatedAt
) {
    public static JobResponse from(FieldJob job) {
        List<JobMaterialResponse> materials = job.getAllocations().stream()
                .map(JobMaterialResponse::from)
                .sorted(Comparator.comparing(JobMaterialResponse::category))
                .toList();

        return new JobResponse(
                job.getId(),
                job.getJobId(),
                job.getCustomerName(),
                job.getLocation(),
                job.getJobDate(),
                job.getStatus(),
                InstallerResponse.from(job.getInstaller()),
                materials,
                job.getCreatedAt(),
                job.getUpdatedAt()
        );
    }
}
