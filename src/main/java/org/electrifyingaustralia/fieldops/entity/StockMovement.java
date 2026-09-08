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
import org.electrifyingaustralia.fieldops.enums.StockMovementType;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Entity
@Table(name = "stock_movements")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id")
    private FieldJob job;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receipt_id")
    private StockReceipt receipt;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 30)
    private StockMovementType movementType;

    @Column(name = "on_hand_delta", nullable = false)
    private int onHandDelta;

    @Column(name = "reserved_delta", nullable = false)
    private int reservedDelta;

    @Column(name = "on_hand_after", nullable = false)
    private int onHandAfter;

    @Column(name = "reserved_after", nullable = false)
    private int reservedAfter;

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

    public static StockMovement receipt(
            Product product,
            StockReceipt receipt,
            int quantity,
            int onHandAfter,
            int reservedAfter,
            String reference,
            String note,
            User performedBy
    ) {
        requirePositive(quantity);
        StockMovement movement = new StockMovement();
        movement.product = Objects.requireNonNull(product, "product is required");
        movement.receipt = Objects.requireNonNull(receipt, "receipt is required");
        movement.movementType = StockMovementType.STOCK_RECEIPT;
        movement.onHandDelta = quantity;
        movement.reservedDelta = 0;
        movement.onHandAfter = onHandAfter;
        movement.reservedAfter = reservedAfter;
        movement.reference = normalizeOptional(reference);
        movement.note = normalizeOptional(note);
        movement.performedBy = Objects.requireNonNull(
                performedBy,
                "performedBy is required"
        );
        return movement;
    }

    public static StockMovement reservation(
            Product product,
            FieldJob job,
            int quantity,
            int onHandAfter,
            int reservedAfter,
            User performedBy
    ) {
        requirePositive(quantity);
        StockMovement movement = new StockMovement();
        movement.product = Objects.requireNonNull(product, "product is required");
        movement.job = Objects.requireNonNull(job, "job is required");
        movement.movementType = StockMovementType.JOB_RESERVATION;
        movement.onHandDelta = 0;
        movement.reservedDelta = quantity;
        movement.onHandAfter = onHandAfter;
        movement.reservedAfter = reservedAfter;
        movement.reference = job.getJobId();
        movement.performedBy = Objects.requireNonNull(
                performedBy,
                "performedBy is required"
        );
        return movement;
    }

    public static StockMovement reservationRelease(
            Product product,
            FieldJob job,
            int quantity,
            int onHandAfter,
            int reservedAfter,
            String reason,
            User performedBy
    ) {
        requirePositive(quantity);
        StockMovement movement = jobMovement(
                product,
                job,
                StockMovementType.JOB_RESERVATION_RELEASE,
                0,
                -quantity,
                onHandAfter,
                reservedAfter,
                performedBy
        );
        movement.reference = job.getJobId();
        movement.note = requireText(reason, "reason");
        return movement;
    }

    public static StockMovement dispatch(
            Product product,
            FieldJob job,
            int quantity,
            int onHandAfter,
            int reservedAfter,
            String dispatchReference,
            String note,
            User performedBy
    ) {
        requirePositive(quantity);
        StockMovement movement = jobMovement(
                product,
                job,
                StockMovementType.JOB_DISPATCH,
                -quantity,
                -quantity,
                onHandAfter,
                reservedAfter,
                performedBy
        );
        movement.reference = requireText(
                dispatchReference,
                "dispatchReference"
        );
        movement.note = normalizeOptional(note);
        return movement;
    }

    private static StockMovement jobMovement(
            Product product,
            FieldJob job,
            StockMovementType movementType,
            int onHandDelta,
            int reservedDelta,
            int onHandAfter,
            int reservedAfter,
            User performedBy
    ) {
        StockMovement movement = new StockMovement();
        movement.product = Objects.requireNonNull(product, "product is required");
        movement.job = Objects.requireNonNull(job, "job is required");
        movement.movementType = movementType;
        movement.onHandDelta = onHandDelta;
        movement.reservedDelta = reservedDelta;
        movement.onHandAfter = onHandAfter;
        movement.reservedAfter = reservedAfter;
        movement.performedBy = Objects.requireNonNull(
                performedBy,
                "performedBy is required"
        );
        return movement;
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    private static void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be greater than zero");
        }
    }

}
