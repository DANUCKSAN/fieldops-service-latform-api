package org.electrifyingaustralia.fieldops.enums;

import java.util.EnumSet;
import java.util.Set;

public enum ProductBrand {
    JINKO,
    AIKO,
    TESLA,
    SIGEN,
    ANKER_SOLIX,
    SUNGROW,
    GOODWE;

    private static final Set<ProductBrand> PANEL_BRANDS =
            EnumSet.of(JINKO, AIKO);

    private static final Set<ProductBrand> ENERGY_BRANDS =
            EnumSet.of(TESLA, SIGEN, ANKER_SOLIX, SUNGROW, GOODWE);

    public boolean supports(ProductCategory category) {
        return switch (category) {
            case PANEL -> PANEL_BRANDS.contains(this);
            case BATTERY, INVERTER -> ENERGY_BRANDS.contains(this);
        };
    }
}
