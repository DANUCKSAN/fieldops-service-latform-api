package org.electrifyingaustralia.fieldops.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.electrifyingaustralia.fieldops.entity.InventoryBalance;
import org.electrifyingaustralia.fieldops.entity.Product;
import org.electrifyingaustralia.fieldops.enums.CapacityUnit;
import org.electrifyingaustralia.fieldops.enums.ProductBrand;
import org.electrifyingaustralia.fieldops.enums.ProductCategory;

public record ProductResponse(
        UUID id,
        String sku,
        ProductCategory category,
        ProductBrand brand,
        String model,
        BigDecimal capacity,
        CapacityUnit capacityUnit,
        boolean active,
        InventoryAmountsResponse inventory,
        Instant createdAt,
        Instant updatedAt
) {
    public static ProductResponse from(InventoryBalance balance) {
        Product product = balance.getProduct();
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getCategory(),
                product.getBrand(),
                product.getModel(),
                product.getCapacity(),
                product.getCapacityUnit(),
                product.isActive(),
                InventoryAmountsResponse.from(balance),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
