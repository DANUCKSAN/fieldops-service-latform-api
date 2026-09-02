package org.electrifyingaustralia.fieldops.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import org.electrifyingaustralia.fieldops.enums.ProductBrand;
import org.electrifyingaustralia.fieldops.enums.ProductCategory;

public record CreateProductRequest(
        @NotBlank @Size(max = 80) String sku,
        @NotNull ProductCategory category,
        @NotNull ProductBrand brand,
        @NotBlank @Size(max = 160) String model,
        @NotNull @DecimalMin("0.001") @Digits(integer = 7, fraction = 3)
        BigDecimal capacity
) {
}
