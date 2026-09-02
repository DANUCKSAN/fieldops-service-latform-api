package org.electrifyingaustralia.fieldops.dto.response;

import org.electrifyingaustralia.fieldops.entity.InventoryBalance;

public record StockReceiptItemResponse(
        ProductResponse product,
        int quantityReceived
) {
    public static StockReceiptItemResponse from(
            InventoryBalance balance,
            int quantityReceived
    ) {
        return new StockReceiptItemResponse(
                ProductResponse.from(balance),
                quantityReceived
        );
    }
}
