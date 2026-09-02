package org.electrifyingaustralia.fieldops.dto.response;

import java.util.UUID;
import org.electrifyingaustralia.fieldops.entity.Installer;

public record InstallerResponse(
        UUID id,
        String displayName,
        boolean active
) {
    public static InstallerResponse from(Installer installer) {
        return new InstallerResponse(
                installer.getId(),
                installer.getDisplayName(),
                installer.isActive()
        );
    }
}
