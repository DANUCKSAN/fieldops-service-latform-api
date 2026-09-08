package org.electrifyingaustralia.fieldops.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.electrifyingaustralia.fieldops.enums.JobStatus;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Entity
@Table(name = "job_status_history")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private FieldJob job;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 30)
    private JobStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 30)
    private JobStatus newStatus;

    @Column(length = 80)
    private String reference;

    @Column(length = 500)
    private String note;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "performed_by_user_id", nullable = false)
    private User performedBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static JobStatusHistory initialAllocation(
            FieldJob job,
            User performedBy
    ) {
        return create(
                job,
                null,
                JobStatus.ALLOCATED,
                job.getJobId(),
                null,
                performedBy
        );
    }

    public static JobStatusHistory transition(
            FieldJob job,
            JobStatus previousStatus,
            JobStatus newStatus,
            String reference,
            String note,
            User performedBy
    ) {
        Objects.requireNonNull(previousStatus, "previousStatus is required");
        return create(
                job,
                previousStatus,
                newStatus,
                reference,
                note,
                performedBy
        );
    }

    private static JobStatusHistory create(
            FieldJob job,
            JobStatus previousStatus,
            JobStatus newStatus,
            String reference,
            String note,
            User performedBy
    ) {
        JobStatusHistory history = new JobStatusHistory();
        history.job = Objects.requireNonNull(job, "job is required");
        history.previousStatus = previousStatus;
        history.newStatus = Objects.requireNonNull(
                newStatus,
                "newStatus is required"
        );
        history.reference = normalizeOptional(reference);
        history.note = normalizeOptional(note);
        history.performedBy = Objects.requireNonNull(
                performedBy,
                "performedBy is required"
        );
        return history;
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
