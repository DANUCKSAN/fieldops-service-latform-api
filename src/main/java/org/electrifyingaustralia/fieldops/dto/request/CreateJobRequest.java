package org.electrifyingaustralia.fieldops.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateJobRequest(
        @NotBlank @Size(max = 80) String jobId,
        @NotBlank @Size(max = 160) String customerName,
        @NotBlank @Size(max = 500) String location,
        @NotNull LocalDate jobDate,
        @NotNull UUID installerId,
        @NotNull @Size(min = 3, max = 3)
        List<@NotNull @Valid MaterialAllocationRequest> materials
) {
}
