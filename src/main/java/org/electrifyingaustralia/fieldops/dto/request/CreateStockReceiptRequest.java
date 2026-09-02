package org.electrifyingaustralia.fieldops.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public record CreateStockReceiptRequest(
        @NotBlank @Size(max = 80) String receiptReference,
        @NotNull @PastOrPresent Instant receivedAt,
        @Size(max = 500) String note,
        @NotNull @Size(min = 1, max = 100)
        List<@NotNull @Valid StockReceiptItemRequest> items
) {
}
