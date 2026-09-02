package org.electrifyingaustralia.fieldops.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateInstallerRequest(
        @NotBlank @Size(max = 160) String displayName
) {
}
