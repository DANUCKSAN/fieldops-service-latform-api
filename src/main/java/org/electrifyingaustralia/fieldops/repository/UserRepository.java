package org.electrifyingaustralia.fieldops.repository;

import java.util.Optional;
import java.util.UUID;
import org.electrifyingaustralia.fieldops.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByIdentityIssuerAndIdentitySubject(
            String identityIssuer,
            String identitySubject
    );
}
