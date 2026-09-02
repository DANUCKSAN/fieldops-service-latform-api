package org.electrifyingaustralia.fieldops.dto.response;

import java.math.BigDecimal;
import java.util.UUID;
import org.electrifyingaustralia.fieldops.entity.JobAllocation;
import org.electrifyingaustralia.fieldops.enums.CapacityUnit;
import org.electrifyingaustralia.fieldops.enums.ProductBrand;
import org.electrifyingaustralia.fieldops.enums.ProductCategory;

public record JobMaterialResponse(
        UUID productId,
        String sku,
        ProductCategory category,
        ProductBrand brand,
        String model,
        BigDecimal capacity,
        CapacityUnit capacityUnit,
        int quantity
) {
    public static JobMaterialResponse from(JobAllocation allocation) {
        return new JobMaterialResponse(
                allocation.getProduct().getId(),
                allocation.getProductSku(),
                allocation.getCategory(),
                allocation.getProductBrand(),
                allocation.getProductModel(),
                allocation.getProductCapacity(),
                allocation.getProductCapacityUnit(),
                allocation.getQuantity()
        );
    }
}
