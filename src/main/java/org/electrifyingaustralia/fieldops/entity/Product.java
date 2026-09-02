package org.electrifyingaustralia.fieldops.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.electrifyingaustralia.fieldops.enums.CapacityUnit;
import org.electrifyingaustralia.fieldops.enums.ProductBrand;
import org.electrifyingaustralia.fieldops.enums.ProductCategory;

@Getter
@Entity
@Table(name = "products")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseEntity {

    @Column(nullable = false, length = 80)
    private String sku;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProductBrand brand;

    @Column(nullable = false, length = 160)
    private String model;

    @Column(nullable = false, precision = 10, scale = 3)
    private BigDecimal capacity;

    @Enumerated(EnumType.STRING)
    @Column(name = "capacity_unit", nullable = false, length = 10)
    private CapacityUnit capacityUnit;

    @Column(nullable = false)
    private boolean active;

    private Product(
            String sku,
            ProductCategory category,
            ProductBrand brand,
            String model,
            BigDecimal capacity
    ) {
        this.sku = requireText(sku, "sku").toUpperCase(Locale.ROOT);
        this.category = Objects.requireNonNull(category, "category is required");
        this.brand = Objects.requireNonNull(brand, "brand is required");
        this.model = requireText(model, "model");
        this.capacity = requirePositive(capacity, "capacity");
        this.capacityUnit = CapacityUnit.forCategory(category);
        this.active = true;

        if (!brand.supports(category)) {
            throw new IllegalArgumentException(
                    brand + " is not supported for category " + category
            );
        }
    }

    public static Product create(
            String sku,
            ProductCategory category,
            ProductBrand brand,
            String model,
            BigDecimal capacity
    ) {
        return new Product(sku, category, brand, model, capacity);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    private static BigDecimal requirePositive(BigDecimal value, String fieldName) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(fieldName + " must be greater than zero");
        }
        return value;
    }
}
