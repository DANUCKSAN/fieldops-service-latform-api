package org.electrifyingaustralia.fieldops.dto.response;

import java.time.Instant;
import java.util.UUID;
import org.electrifyingaustralia.fieldops.entity.JobStatusHistory;
import org.electrifyingaustralia.fieldops.enums.JobStatus;
import org.electrifyingaustralia.fieldops.enums.UserRole;

public record JobStatusHistoryResponse(
        UUID id,
        JobStatus previousStatus,
        JobStatus newStatus,
        String reference,
        String note,
        UUID performedByUserId,
        String performedByName,
        UserRole performedByRole,
        Instant createdAt
) {
    public static JobStatusHistoryResponse from(JobStatusHistory history) {
        return new JobStatusHistoryResponse(
                history.getId(),
                history.getPreviousStatus(),
                history.getNewStatus(),
                history.getReference(),
                history.getNote(),
                history.getPerformedBy().getId(),
                history.getPerformedBy().getFirstName()
                        + " "
                        + history.getPerformedBy().getLastName(),
                history.getPerformedBy().getRole(),
                history.getCreatedAt()
        );
    }
}
