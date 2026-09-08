package org.electrifyingaustralia.fieldops.entity;

import java.math.BigDecimal;
import org.electrifyingaustralia.fieldops.enums.CapacityUnit;
import org.electrifyingaustralia.fieldops.enums.ProductBrand;
import org.electrifyingaustralia.fieldops.enums.ProductCategory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InventoryBalanceTest {

    @Test
    void receivingAndReservingStockUpdatesAvailability() {
        Product product = Product.create(
                "jinko-440",
                ProductCategory.PANEL,
                ProductBrand.JINKO,
                "Tiger Neo 440W",
                new BigDecimal("440")
        );
        InventoryBalance balance = InventoryBalance.emptyFor(product);

        balance.receive(30);
        balance.reserve(18);

        assertEquals("JINKO-440", product.getSku());
        assertEquals(CapacityUnit.W, product.getCapacityUnit());
        assertEquals(30, balance.getOnHandQuantity());
        assertEquals(18, balance.getReservedQuantity());
        assertEquals(12, balance.availableQuantity());
    }

    @Test
    void reservationCannotExceedAvailableStock() {
        Product product = Product.create(
                "goodwe-10",
                ProductCategory.INVERTER,
                ProductBrand.GOODWE,
                "ET 10kW",
                new BigDecimal("10")
        );
        InventoryBalance balance = InventoryBalance.emptyFor(product);
        balance.receive(1);

        assertThrows(IllegalStateException.class, () -> balance.reserve(2));
        assertEquals(0, balance.getReservedQuantity());
        assertEquals(1, balance.availableQuantity());
    }

    @Test
    void cancellationReleasesReservationWithoutChangingOnHandStock() {
        InventoryBalance balance = InventoryBalance.emptyFor(panel());
        balance.receive(30);
        balance.reserve(18);

        balance.releaseReservation(18);

        assertEquals(30, balance.getOnHandQuantity());
        assertEquals(0, balance.getReservedQuantity());
        assertEquals(30, balance.availableQuantity());
    }

    @Test
    void dispatchConsumesOnHandStockAndItsReservationTogether() {
        InventoryBalance balance = InventoryBalance.emptyFor(panel());
        balance.receive(30);
        balance.reserve(18);

        balance.dispatchReserved(18);

        assertEquals(12, balance.getOnHandQuantity());
        assertEquals(0, balance.getReservedQuantity());
        assertEquals(12, balance.availableQuantity());
    }

    @Test
    void reservationReleaseAndDispatchRejectMissingReservedStock() {
        InventoryBalance balance = InventoryBalance.emptyFor(panel());
        balance.receive(30);
        balance.reserve(5);

        assertAll(
                () -> assertThrows(
                        IllegalStateException.class,
                        () -> balance.releaseReservation(6)
                ),
                () -> assertThrows(
                        IllegalStateException.class,
                        () -> balance.dispatchReserved(6)
                ),
                () -> assertEquals(30, balance.getOnHandQuantity()),
                () -> assertEquals(5, balance.getReservedQuantity())
        );
    }

    @Test
    void productRejectsUnsupportedBrandForCategory() {
        assertThrows(IllegalArgumentException.class, () -> Product.create(
                "tesla-panel",
                ProductCategory.PANEL,
                ProductBrand.TESLA,
                "Unsupported panel",
                new BigDecimal("440")
        ));
    }

    private Product panel() {
        return Product.create(
                "jinko-440",
                ProductCategory.PANEL,
                ProductBrand.JINKO,
                "Tiger Neo 440W",
                new BigDecimal("440")
        );
    }
}
