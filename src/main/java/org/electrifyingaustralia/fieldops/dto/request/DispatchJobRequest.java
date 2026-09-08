package org.electrifyingaustralia.fieldops.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DispatchJobRequest(
        @NotBlank @Size(max = 80) String dispatchReference,
        @Size(max = 500) String note
) {
}
