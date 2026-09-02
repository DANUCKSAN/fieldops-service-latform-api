package org.electrifyingaustralia.fieldops.enums;

public enum CapacityUnit {
    W,
    KW,
    KWH;

    public static CapacityUnit forCategory(ProductCategory category) {
        return switch (category) {
            case PANEL -> W;
            case BATTERY -> KWH;
            case INVERTER -> KW;
        };
    }
}
