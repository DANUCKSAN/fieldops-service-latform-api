package org.electrifyingaustralia.fieldops.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "inventory_balances")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryBalance extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

    @Column(name = "on_hand_quantity", nullable = false)
    private int onHandQuantity;

    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity;

    private InventoryBalance(Product product) {
        this.product = Objects.requireNonNull(product, "product is required");
        this.onHandQuantity = 0;
        this.reservedQuantity = 0;
    }

    public static InventoryBalance emptyFor(Product product) {
        return new InventoryBalance(product);
    }

    public void receive(int quantity) {
        requirePositive(quantity);
        onHandQuantity = Math.addExact(onHandQuantity, quantity);
    }

    public void reserve(int quantity) {
        requirePositive(quantity);
        if (availableQuantity() < quantity) {
            throw new IllegalStateException("insufficient stock");
        }
        reservedQuantity = Math.addExact(reservedQuantity, quantity);
    }

    public void releaseReservation(int quantity) {
        requirePositive(quantity);
        ensureReservationExists(quantity);
        reservedQuantity = Math.subtractExact(reservedQuantity, quantity);
    }

    public void dispatchReserved(int quantity) {
        requirePositive(quantity);
        ensureReservationExists(quantity);
        onHandQuantity = Math.subtractExact(onHandQuantity, quantity);
        reservedQuantity = Math.subtractExact(reservedQuantity, quantity);
    }

    public int availableQuantity() {
        return onHandQuantity - reservedQuantity;
    }

    private static void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be greater than zero");
        }
    }

    private void ensureReservationExists(int quantity) {
        if (reservedQuantity < quantity) {
            throw new IllegalStateException("insufficient reserved stock");
        }
    }
}
