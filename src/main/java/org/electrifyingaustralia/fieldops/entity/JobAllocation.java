package org.electrifyingaustralia.fieldops.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.electrifyingaustralia.fieldops.enums.CapacityUnit;
import org.electrifyingaustralia.fieldops.enums.ProductBrand;
import org.electrifyingaustralia.fieldops.enums.ProductCategory;

@Getter
@Entity
@Table(name = "job_allocations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private FieldJob job;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductCategory category;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "product_sku", nullable = false, length = 80)
    private String productSku;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_brand", nullable = false, length = 30)
    private ProductBrand productBrand;

    @Column(name = "product_model", nullable = false, length = 160)
    private String productModel;

    @Column(name = "product_capacity", nullable = false, precision = 10, scale = 3)
    private BigDecimal productCapacity;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_capacity_unit", nullable = false, length = 10)
    private CapacityUnit productCapacityUnit;

    private JobAllocation(FieldJob job, Product product, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be greater than zero");
        }
        this.job = Objects.requireNonNull(job, "job is required");
        this.product = Objects.requireNonNull(product, "product is required");
        this.category = product.getCategory();
        this.quantity = quantity;
        this.productSku = product.getSku();
        this.productBrand = product.getBrand();
        this.productModel = product.getModel();
        this.productCapacity = product.getCapacity();
        this.productCapacityUnit = product.getCapacityUnit();
    }

    static JobAllocation create(FieldJob job, Product product, int quantity) {
        return new JobAllocation(job, product, quantity);
    }
}
