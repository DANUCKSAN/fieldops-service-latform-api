package org.electrifyingaustralia.fieldops.repository;

import java.util.List;
import java.util.UUID;
import org.electrifyingaustralia.fieldops.entity.Installer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstallerRepository extends JpaRepository<Installer, UUID> {

    boolean existsByDisplayNameIgnoreCase(String displayName);

    List<Installer> findAllByActiveTrueOrderByDisplayNameAsc();
}
