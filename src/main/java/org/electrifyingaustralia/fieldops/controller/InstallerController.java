package org.electrifyingaustralia.fieldops.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.electrifyingaustralia.fieldops.dto.request.CreateInstallerRequest;
import org.electrifyingaustralia.fieldops.dto.response.InstallerResponse;
import org.electrifyingaustralia.fieldops.service.InstallerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/installers")
@RequiredArgsConstructor
public class InstallerController {

    private final InstallerService installerService;

    @PostMapping
    public ResponseEntity<InstallerResponse> create(
            @Valid @RequestBody CreateInstallerRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(installerService.create(request, jwt));
    }

    @GetMapping
    public List<InstallerResponse> listActive(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return installerService.listActive(jwt);
    }
}
