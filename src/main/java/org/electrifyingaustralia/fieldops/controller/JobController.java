package org.electrifyingaustralia.fieldops.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.electrifyingaustralia.fieldops.dto.request.CancelJobRequest;
import org.electrifyingaustralia.fieldops.dto.request.CreateJobRequest;
import org.electrifyingaustralia.fieldops.dto.request.DispatchJobRequest;
import org.electrifyingaustralia.fieldops.dto.response.JobResponse;
import org.electrifyingaustralia.fieldops.dto.response.JobStatusHistoryResponse;
import org.electrifyingaustralia.fieldops.dto.response.JobSummaryResponse;
import org.electrifyingaustralia.fieldops.dto.response.PagedResponse;
import org.electrifyingaustralia.fieldops.service.JobService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Validated
@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    @PostMapping
    public ResponseEntity<JobResponse> create(
            @Valid @RequestBody CreateJobRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        JobResponse response = jobService.create(request, jwt);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{id}")
    public JobResponse get(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return jobService.get(id, jwt);
    }

    @GetMapping("/by-job-id/{jobId}")
    public JobResponse getByJobId(
            @PathVariable @NotBlank @Size(max = 80) String jobId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return jobService.getByJobId(jobId, jwt);
    }

    @GetMapping
    public PagedResponse<JobSummaryResponse> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return jobService.list(page, size, jwt);
    }

    @PostMapping("/{id}/dispatch")
    public JobResponse dispatch(
            @PathVariable UUID id,
            @Valid @RequestBody DispatchJobRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return jobService.dispatch(id, request, jwt);
    }

    @PostMapping("/{id}/cancel")
    public JobResponse cancel(
            @PathVariable UUID id,
            @Valid @RequestBody CancelJobRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return jobService.cancel(id, request, jwt);
    }

    @PostMapping("/{id}/complete")
    public JobResponse complete(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return jobService.complete(id, jwt);
    }

    @GetMapping("/{id}/history")
    public List<JobStatusHistoryResponse> history(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return jobService.history(id, jwt);
    }
}
