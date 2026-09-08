package org.electrifyingaustralia.fieldops.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelJobRequest(
        @NotBlank @Size(max = 500) String reason
) {
}
