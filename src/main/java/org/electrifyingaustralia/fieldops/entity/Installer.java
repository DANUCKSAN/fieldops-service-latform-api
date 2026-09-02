package org.electrifyingaustralia.fieldops.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "installers")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Installer extends BaseEntity {

    @Column(name = "display_name", nullable = false, length = 160)
    private String displayName;

    @Column(nullable = false)
    private boolean active;

    private Installer(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName is required");
        }
        this.displayName = displayName.trim();
        this.active = true;
    }

    public static Installer create(String displayName) {
        return new Installer(displayName);
    }
}
