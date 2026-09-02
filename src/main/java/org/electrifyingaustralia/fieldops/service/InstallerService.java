package org.electrifyingaustralia.fieldops.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.electrifyingaustralia.fieldops.constant.DatabaseConstraints;
import org.electrifyingaustralia.fieldops.dto.request.CreateInstallerRequest;
import org.electrifyingaustralia.fieldops.dto.response.InstallerResponse;
import org.electrifyingaustralia.fieldops.entity.Installer;
import org.electrifyingaustralia.fieldops.exception.ConflictException;
import org.electrifyingaustralia.fieldops.repository.InstallerRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InstallerService {

    private final InstallerRepository installerRepository;
    private final UserService userService;

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public InstallerResponse create(CreateInstallerRequest request, Jwt jwt) {
        userService.resolveAuthenticatedUser(jwt);
        String displayName = request.displayName().trim();

        if (installerRepository.existsByDisplayNameIgnoreCase(displayName)) {
            throw duplicateInstaller(displayName, null);
        }

        try {
            Installer installer = installerRepository.saveAndFlush(
                    Installer.create(displayName)
            );
            return InstallerResponse.from(installer);
        } catch (DataIntegrityViolationException exception) {
            if (DatabaseConstraints.isNamed(
                    exception,
                    "uq_installers_display_name"
            )) {
                throw duplicateInstaller(displayName, exception);
            }
            throw exception;
        }
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_OPR')")
    public List<InstallerResponse> listActive(Jwt jwt) {
        userService.resolveAuthenticatedUser(jwt);
        return installerRepository.findAllByActiveTrueOrderByDisplayNameAsc()
                .stream()
                .map(InstallerResponse::from)
                .toList();
    }

    private ConflictException duplicateInstaller(
            String displayName,
            Throwable cause
    ) {
        String message = "An installer named " + displayName + " already exists";
        return cause == null
                ? new ConflictException("DUPLICATE_INSTALLER", message)
                : new ConflictException("DUPLICATE_INSTALLER", message, cause);
    }
}
