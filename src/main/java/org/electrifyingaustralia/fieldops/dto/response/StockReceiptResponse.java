package org.electrifyingaustralia.fieldops.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.electrifyingaustralia.fieldops.entity.StockReceipt;

public record StockReceiptResponse(
        UUID id,
        String receiptReference,
        Instant receivedAt,
        String note,
        List<StockReceiptItemResponse> items,
        Instant createdAt
) {
    public static StockReceiptResponse from(
            StockReceipt receipt,
            List<StockReceiptItemResponse> items
    ) {
        return new StockReceiptResponse(
                receipt.getId(),
                receipt.getReceiptReference(),
                receipt.getReceivedAt(),
                receipt.getNote(),
                items,
                receipt.getCreatedAt()
        );
    }
}
