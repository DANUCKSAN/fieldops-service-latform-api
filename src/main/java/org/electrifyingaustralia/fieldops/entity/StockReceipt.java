package org.electrifyingaustralia.fieldops.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Entity
@Table(name = "stock_receipts")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "receipt_reference", nullable = false, length = 80)
    private String receiptReference;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(length = 500)
    private String note;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "performed_by_user_id", nullable = false)
    private User performedBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private StockReceipt(
            String receiptReference,
            Instant receivedAt,
            String note,
            User performedBy
    ) {
        this.receiptReference = requireText(
                receiptReference,
                "receiptReference"
        );
        this.receivedAt = receivedAt;
        this.note = normalizeOptional(note);
        this.performedBy = Objects.requireNonNull(
                performedBy,
                "performedBy is required"
        );
    }

    public static StockReceipt create(
            String receiptReference,
            Instant receivedAt,
            String note,
            User performedBy
    ) {
        if (receivedAt == null) {
            throw new IllegalArgumentException("receivedAt is required");
        }
        return new StockReceipt(
                receiptReference,
                receivedAt,
                note,
                performedBy
        );
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
}
