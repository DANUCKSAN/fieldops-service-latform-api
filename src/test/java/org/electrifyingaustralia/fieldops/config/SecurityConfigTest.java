package org.electrifyingaustralia.fieldops.config;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityConfigTest {

    @Test
    void mapsTopLevelRolesClaimToSpringRoleAuthorities() {
        Jwt jwt = new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(300),
                java.util.Map.of("alg", "none"),
                java.util.Map.of(
                        "sub", "warehouse-user",
                        "roles", List.of("WAREHOUSE_OPR", "offline_access")
                )
        );
        JwtAuthenticationConverter converter =
                new SecurityConfig().jwtAuthenticationConverter();

        assertTrue(converter.convert(jwt).getAuthorities().contains(
                new SimpleGrantedAuthority("ROLE_WAREHOUSE_OPR")
        ));
    }
}
