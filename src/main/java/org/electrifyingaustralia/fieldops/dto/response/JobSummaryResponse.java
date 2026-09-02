package org.electrifyingaustralia.fieldops.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.electrifyingaustralia.fieldops.entity.FieldJob;
import org.electrifyingaustralia.fieldops.enums.JobStatus;

public record JobSummaryResponse(
        UUID id,
        String jobId,
        String customerName,
        String location,
        LocalDate jobDate,
        JobStatus status,
        InstallerResponse installer,
        Instant createdAt
) {
    public static JobSummaryResponse from(FieldJob job) {
        return new JobSummaryResponse(
                job.getId(),
                job.getJobId(),
                job.getCustomerName(),
                job.getLocation(),
                job.getJobDate(),
                job.getStatus(),
                InstallerResponse.from(job.getInstaller()),
                job.getCreatedAt()
        );
    }
}
