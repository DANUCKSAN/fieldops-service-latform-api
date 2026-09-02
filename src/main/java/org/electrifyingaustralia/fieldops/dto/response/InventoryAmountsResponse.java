package org.electrifyingaustralia.fieldops.dto.response;

import org.electrifyingaustralia.fieldops.entity.InventoryBalance;

public record InventoryAmountsResponse(
        int onHandQuantity,
        int reservedQuantity,
        int availableQuantity
) {
    public static InventoryAmountsResponse from(InventoryBalance balance) {
        return new InventoryAmountsResponse(
                balance.getOnHandQuantity(),
                balance.getReservedQuantity(),
                balance.availableQuantity()
        );
    }
}
