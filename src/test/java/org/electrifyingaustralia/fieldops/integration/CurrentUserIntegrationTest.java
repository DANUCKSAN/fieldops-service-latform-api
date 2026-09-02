package org.electrifyingaustralia.fieldops.integration;

import java.util.List;
import org.electrifyingaustralia.fieldops.entity.User;
import org.electrifyingaustralia.fieldops.enums.UserRole;
import org.electrifyingaustralia.fieldops.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class CurrentUserIntegrationTest {

    private static final String ISSUER =
            "http://localhost:8180/realms/fieldops";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void prepareDatabase() {
        userRepository.deleteAll();
    }

    @Test
    void createsProfileFromVerifiedIdentityClaims() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .with(authenticatedUser(
                                "warehouse-subject",
                                "Warehouse",
                                "Operator",
                                "Warehouse.Operator@Example.com",
                                true,
                                List.of("offline_access", "WAREHOUSE_OPR")
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Warehouse"))
                .andExpect(jsonPath("$.lastName").value("Operator"))
                .andExpect(jsonPath("$.email")
                        .value("warehouse.operator@example.com"))
                .andExpect(jsonPath("$.role").value("WAREHOUSE_OPR"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        User savedUser = userRepository
                .findByIdentityIssuerAndIdentitySubject(
                        ISSUER,
                        "warehouse-subject"
                )
                .orElseThrow();

        assertAll(
                () -> assertNotNull(savedUser.getId()),
                () -> assertEquals(
                        "warehouse.operator@example.com",
                        savedUser.getEmail()
                ),
                () -> assertEquals(
                        UserRole.WAREHOUSE_OPR,
                        savedUser.getRole()
                )
        );
    }

    @Test
    void returnsAndSynchronizesExistingProfileWithoutDuplicatingIt()
            throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .with(authenticatedUser(
                                "admin-subject",
                                "Initial",
                                "Admin",
                                "admin@example.com",
                                true,
                                List.of("ADMIN")
                        )))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/users/me")
                        .with(authenticatedUser(
                                "admin-subject",
                                "Updated",
                                "Administrator",
                                "admin@example.com",
                                true,
                                List.of("ADMIN")
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Updated"))
                .andExpect(jsonPath("$.lastName")
                        .value("Administrator"));

        assertEquals(1, userRepository.count());
    }

    @Test
    void linksVerifiedIdentityToAnExistingUnlinkedProfile()
            throws Exception {
        User existingUser = userRepository.saveAndFlush(
                User.create(
                        "Existing",
                        "Operator",
                        "operator@example.com",
                        UserRole.WAREHOUSE_OPR
                )
        );

        mockMvc.perform(get("/api/v1/users/me")
                        .with(authenticatedUser(
                                "existing-subject",
                                "Existing",
                                "Operator",
                                "operator@example.com",
                                true,
                                List.of("WAREHOUSE_OPR")
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id")
                        .value(existingUser.getId().toString()));

        User linkedUser = userRepository
                .findByIdentityIssuerAndIdentitySubject(
                        ISSUER,
                        "existing-subject"
                )
                .orElseThrow();

        assertEquals(existingUser.getId(), linkedUser.getId());
        assertEquals(1, userRepository.count());
    }

    @Test
    void rejectsAnUnverifiedEmail() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .with(authenticatedUser(
                                "unverified-subject",
                                "Unverified",
                                "User",
                                "unverified@example.com",
                                false,
                                List.of("WAREHOUSE_OPR")
                        )))
                .andExpect(status().isForbidden());

        assertEquals(0, userRepository.count());
    }

    @Test
    void rejectsAnIdentityWithMultipleFieldOpsRoles()
            throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .with(authenticatedUser(
                                "ambiguous-role-subject",
                                "Ambiguous",
                                "User",
                                "ambiguous@example.com",
                                true,
                                List.of("ADMIN", "WAREHOUSE_OPR")
                        )))
                .andExpect(status().isForbidden());

        assertEquals(0, userRepository.count());
    }

    @Test
    void rejectsRequestWithoutAccessToken() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }

    private JwtRequestPostProcessor authenticatedUser(
            String subject,
            String firstName,
            String lastName,
            String email,
            boolean emailVerified,
            List<String> roles
    ) {
        return jwt().jwt(token -> token
                .issuer(ISSUER)
                .subject(subject)
                .claim("given_name", firstName)
                .claim("family_name", lastName)
                .claim("email", email)
                .claim("email_verified", emailVerified)
                .claim("roles", roles));
    }

    @Test
    void createsAdminProfile() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .with(authenticatedUser(
                                "admin-subject",
                                "System",
                                "Administrator",
                                "admin@example.com",
                                true,
                                List.of("ADMIN")
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email")
                        .value("admin@example.com"))
                .andExpect(jsonPath("$.role")
                        .value("ADMIN"))
                .andExpect(jsonPath("$.status")
                        .value("ACTIVE"));

        User savedUser = userRepository
                .findByIdentityIssuerAndIdentitySubject(
                        ISSUER,
                        "admin-subject"
                )
                .orElseThrow();

        assertEquals(UserRole.ADMIN, savedUser.getRole());
        assertEquals(1, userRepository.count());
    }

    @Test
    void rejectsVerifiedUserWithoutFieldOpsRole() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .with(authenticatedUser(
                                "pending-subject",
                                "Pending",
                                "User",
                                "pending@example.com",
                                true,
                                List.of("offline_access")
                        )))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title")
                        .value("FieldOps access denied"));

        assertEquals(0, userRepository.count());
    }
}
