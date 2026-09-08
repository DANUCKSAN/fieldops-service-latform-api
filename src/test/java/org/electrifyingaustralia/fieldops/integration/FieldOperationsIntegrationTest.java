package org.electrifyingaustralia.fieldops.integration;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.electrifyingaustralia.fieldops.config.PostgresTestContainerConfiguration;
import org.electrifyingaustralia.fieldops.entity.FieldJob;
import org.electrifyingaustralia.fieldops.entity.Installer;
import org.electrifyingaustralia.fieldops.entity.InventoryBalance;
import org.electrifyingaustralia.fieldops.entity.Product;
import org.electrifyingaustralia.fieldops.enums.JobStatus;
import org.electrifyingaustralia.fieldops.enums.ProductBrand;
import org.electrifyingaustralia.fieldops.enums.ProductCategory;
import org.electrifyingaustralia.fieldops.repository.FieldJobRepository;
import org.electrifyingaustralia.fieldops.repository.InstallerRepository;
import org.electrifyingaustralia.fieldops.repository.InventoryBalanceRepository;
import org.electrifyingaustralia.fieldops.repository.JobStatusHistoryRepository;
import org.electrifyingaustralia.fieldops.repository.ProductRepository;
import org.electrifyingaustralia.fieldops.repository.StockMovementRepository;
import org.electrifyingaustralia.fieldops.repository.StockReceiptRepository;
import org.electrifyingaustralia.fieldops.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(PostgresTestContainerConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class FieldOperationsIntegrationTest {

    private static final String ISSUER =
            "http://localhost:8180/realms/fieldops";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FieldJobRepository jobRepository;

    @Autowired
    private StockMovementRepository movementRepository;

    @Autowired
    private JobStatusHistoryRepository historyRepository;

    @Autowired
    private StockReceiptRepository receiptRepository;

    @Autowired
    private InventoryBalanceRepository balanceRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InstallerRepository installerRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void prepareDatabase() {
        cleanDatabase();
    }

    @AfterEach
    void leaveDatabaseClean() {
        cleanDatabase();
    }

    @Test
    void completeWorkflowCreatesStockThenReservesItForAJob() throws Exception {
        UUID panelId = createProduct(
                "JINKO-440W",
                "PANEL",
                "JINKO",
                "Tiger Neo 440W",
                "440"
        );
        UUID batteryId = createProduct(
                "TESLA-PW3-13.5",
                "BATTERY",
                "TESLA",
                "Powerwall 3",
                "13.5"
        );
        UUID inverterId = createProduct(
                "SUNGROW-SH10RS",
                "INVERTER",
                "SUNGROW",
                "SH10RS",
                "10"
        );
        UUID installerId = createInstaller("Installer One");

        mockMvc.perform(get("/api/v1/installers")
                        .with(operator("WAREHOUSE_OPR", "warehouse-installers")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(installerId.toString()))
                .andExpect(jsonPath("$[0].displayName").value("Installer One"));

        String receiptRequest = """
                {
                  "receiptReference": "DELIVERY-1001",
                  "receivedAt": "%s",
                  "note": "Initial warehouse delivery",
                  "items": [
                    {"productId": "%s", "quantity": 40},
                    {"productId": "%s", "quantity": 5},
                    {"productId": "%s", "quantity": 8}
                  ]
                }
                """.formatted(
                Instant.now().minusSeconds(60),
                panelId,
                batteryId,
                inverterId
        );

        mockMvc.perform(post("/api/v1/inventory/receipts")
                        .with(operator("WAREHOUSE_OPR", "warehouse-receipt"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(receiptRequest))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.receiptReference")
                        .value("DELIVERY-1001"))
                .andExpect(jsonPath("$.items.length()").value(3));

        mockMvc.perform(get("/api/v1/products")
                        .queryParam("category", "PANEL")
                        .with(operator("WAREHOUSE_OPR", "warehouse-products")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sku").value("JINKO-440W"))
                .andExpect(jsonPath("$[0].inventory.availableQuantity")
                        .value(40));

        MvcResult jobResult = mockMvc.perform(post("/api/v1/jobs")
                        .with(operator("WAREHOUSE_OPR", "warehouse-job"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jobRequest(
                                "CRM-1001",
                                installerId,
                                panelId,
                                18,
                                batteryId,
                                1,
                                inverterId,
                                1
                        )))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.jobId").value("CRM-1001"))
                .andExpect(jsonPath("$.status").value("ALLOCATED"))
                .andExpect(jsonPath("$.installer.displayName")
                        .value("Installer One"))
                .andExpect(jsonPath("$.materials.length()").value(3))
                .andReturn();

        UUID jobId = responseId(jobResult);
        mockMvc.perform(get("/api/v1/jobs/{id}", jobId)
                        .with(operator("ADMIN", "admin-job-detail")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("CRM-1001"));

        mockMvc.perform(get("/api/v1/jobs/by-job-id/{jobId}", "crm-1001")
                        .with(operator("ADMIN", "admin-job-lookup")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(jobId.toString()));

        mockMvc.perform(get("/api/v1/jobs")
                        .queryParam("page", "0")
                        .queryParam("size", "20")
                        .with(operator("ADMIN", "admin-job-list")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].jobId").value("CRM-1001"))
                .andExpect(jsonPath("$.page.totalElements").value(1));

        assertAll(
                () -> assertInventory(panelId, 40, 18, 22),
                () -> assertInventory(batteryId, 5, 1, 4),
                () -> assertInventory(inverterId, 8, 1, 7),
                () -> assertEquals(1, jobRepository.count()),
                () -> assertEquals(1, receiptRepository.count()),
                () -> assertEquals(6, movementRepository.count())
        );

        FieldJob savedJob = jobRepository.findDetailedById(jobId).orElseThrow();
        assertEquals(3, savedJob.getAllocations().size());
        assertEquals(
                "warehouse-job",
                savedJob.getCreatedBy().getIdentitySubject()
        );
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "DELETE FROM stock_movements"
        ));
    }

    @Test
    void insufficientLastMaterialRollsBackTheWholeJob() throws Exception {
        SeededStock stock = seedStock(40, 5, 0);

        mockMvc.perform(post("/api/v1/jobs")
                        .with(operator("WAREHOUSE_OPR", "rollback-user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jobRequest(
                                "CRM-ROLLBACK",
                                stock.installerId(),
                                stock.panelId(),
                                18,
                                stock.batteryId(),
                                1,
                                stock.inverterId(),
                                1
                        )))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));

        assertAll(
                () -> assertInventory(stock.panelId(), 40, 0, 40),
                () -> assertInventory(stock.batteryId(), 5, 0, 5),
                () -> assertInventory(stock.inverterId(), 0, 0, 0),
                () -> assertEquals(0, jobRepository.count()),
                () -> assertEquals(0, movementRepository.count())
        );
    }

    @Test
    void rejectsAJobWithoutExactlyOneProductFromEachCategory()
            throws Exception {
        SeededStock stock = seedStock(40, 5, 8);
        Product secondPanel = productRepository.save(
                Product.create(
                        "AIKO-455W",
                        ProductCategory.PANEL,
                        ProductBrand.AIKO,
                        "Neostar 455W",
                        new BigDecimal("455")
                )
        );
        InventoryBalance secondPanelBalance =
                InventoryBalance.emptyFor(secondPanel);
        secondPanelBalance.receive(40);
        balanceRepository.saveAndFlush(secondPanelBalance);

        mockMvc.perform(post("/api/v1/jobs")
                        .with(operator("WAREHOUSE_OPR", "invalid-material-user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jobRequest(
                                "CRM-BAD-MATERIALS",
                                stock.installerId(),
                                stock.panelId(),
                                10,
                                secondPanel.getId(),
                                10,
                                stock.inverterId(),
                                1
                        )))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_MATERIAL_COMBINATION"));

        assertEquals(0, jobRepository.count());
        assertInventory(stock.panelId(), 40, 0, 40);
    }

    @Test
    void duplicateJobIdNeverReservesStockTwice() throws Exception {
        SeededStock stock = seedStock(40, 5, 8);
        String first = jobRequest(
                "CRM-DUPLICATE",
                stock.installerId(),
                stock.panelId(), 10,
                stock.batteryId(), 1,
                stock.inverterId(), 1
        );

        mockMvc.perform(post("/api/v1/jobs")
                        .with(operator("ADMIN", "duplicate-admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(first))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/jobs")
                        .with(operator("ADMIN", "duplicate-admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(first.replace("CRM-DUPLICATE", "crm-duplicate")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_JOB_ID"));

        assertAll(
                () -> assertInventory(stock.panelId(), 40, 10, 30),
                () -> assertInventory(stock.batteryId(), 5, 1, 4),
                () -> assertInventory(stock.inverterId(), 8, 1, 7),
                () -> assertEquals(1, jobRepository.count()),
                () -> assertEquals(3, movementRepository.count())
        );
    }

    @Test
    void duplicateStockReceiptNeverAddsStockTwice() throws Exception {
        Product product = saveProduct(
                "SIGEN-BAT-8",
                ProductCategory.BATTERY,
                ProductBrand.SIGEN,
                "SigenStor 8",
                "8",
                0
        );
        String receipt = """
                {
                  "receiptReference": "DELIVERY-DUPLICATE",
                  "receivedAt": "%s",
                  "items": [{"productId": "%s", "quantity": 5}]
                }
                """.formatted(Instant.now().minusSeconds(60), product.getId());

        mockMvc.perform(post("/api/v1/inventory/receipts")
                        .with(operator("WAREHOUSE_OPR", "receipt-user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(receipt))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/inventory/receipts")
                        .with(operator("WAREHOUSE_OPR", "receipt-user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(receipt))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("DUPLICATE_RECEIPT_REFERENCE"));

        assertInventory(product.getId(), 5, 0, 5);
        assertEquals(1, receiptRepository.count());
        assertEquals(1, movementRepository.count());
    }

    @Test
    void enforcesAuthenticationRolesAndDisabledUserStatus() throws Exception {
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mockMvc.perform(get("/api/v1/products")
                        .with(jwt().authorities(
                                new SimpleGrantedAuthority("ROLE_SALES")
                        )))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/installers")
                        .with(operator("WAREHOUSE_OPR", "warehouse-installer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Not Allowed\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/users/me")
                        .with(operator("WAREHOUSE_OPR", "disabled-user")))
                .andExpect(status().isOk());
        jdbcTemplate.update(
                "UPDATE app_users SET status = 'DISABLED' WHERE identity_subject = ?",
                "disabled-user"
        );

        mockMvc.perform(get("/api/v1/products")
                        .with(operator("WAREHOUSE_OPR", "disabled-user")))
                .andExpect(status().isForbidden());
    }

    @Test
    void validatesNullMaterialElementsAndAllowsConfiguredCorsPreflight()
            throws Exception {
        mockMvc.perform(post("/api/v1/jobs")
                        .with(operator("WAREHOUSE_OPR", "validation-user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jobId": "CRM-NULL-MATERIALS",
                                  "customerName": "Jordan Lee",
                                  "location": "Sydney NSW",
                                  "jobDate": "2026-09-15",
                                  "installerId": "00000000-0000-0000-0000-000000000001",
                                  "materials": [null, null, null]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        assertEquals(0, jobRepository.count());

        mockMvc.perform(options("/api/v1/jobs")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Access-Control-Allow-Origin",
                        "http://localhost:3000"
                ));
    }

    @Test
    void concurrentJobsCannotOverReserveStock() throws Exception {
        SeededStock stock = seedStock(10, 2, 2);
        provision("race-user-a");
        provision("race-user-b");

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(() -> {
                start.await();
                return createConcurrentJob(
                        "CRM-RACE-A",
                        "race-user-a",
                        stock
                );
            });
            Future<Integer> second = executor.submit(() -> {
                start.await();
                return createConcurrentJob(
                        "CRM-RACE-B",
                        "race-user-b",
                        stock
                );
            });

            start.countDown();
            List<Integer> statuses = new ArrayList<>(
                    List.of(first.get(), second.get())
            );
            Collections.sort(statuses);

            assertEquals(List.of(201, 409), statuses);
        } finally {
            executor.shutdownNow();
        }

        assertAll(
                () -> assertInventory(stock.panelId(), 10, 7, 3),
                () -> assertEquals(1, jobRepository.count()),
                () -> assertEquals(3, movementRepository.count())
        );
    }

    @Test
    void dispatchAndCompletionAreAuditedAtomicAndIdempotent()
            throws Exception {
        SeededStock stock = seedStock(40, 5, 8);
        UUID jobId = createAllocatedJob(
                "CRM-LIFECYCLE",
                "lifecycle-creator",
                stock,
                18
        );

        String dispatchRequest = """
                {
                  "dispatchReference": "DISPATCH-2026-1001",
                  "note": "Loaded onto vehicle 12"
                }
                """;
        mockMvc.perform(post("/api/v1/jobs/{id}/dispatch", jobId)
                        .with(operator("WAREHOUSE_OPR", "dispatcher"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dispatchRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISPATCHED"));

        assertAll(
                () -> assertInventory(stock.panelId(), 22, 0, 22),
                () -> assertInventory(stock.batteryId(), 4, 0, 4),
                () -> assertInventory(stock.inverterId(), 7, 0, 7),
                () -> assertEquals(6, movementRepository.count()),
                () -> assertEquals(2, historyRepository.count()),
                () -> assertEquals(
                        3L,
                        movementCount(jobId, "JOB_DISPATCH")
                )
        );

        // Retrying the same command returns the current representation without
        // consuming inventory or appending duplicate ledger records.
        mockMvc.perform(post("/api/v1/jobs/{id}/dispatch", jobId)
                        .with(operator("WAREHOUSE_OPR", "dispatcher"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dispatchRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISPATCHED"));

        mockMvc.perform(post("/api/v1/jobs/{id}/complete", jobId)
                        .with(operator("WAREHOUSE_OPR", "completer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        mockMvc.perform(post("/api/v1/jobs/{id}/complete", jobId)
                        .with(operator("WAREHOUSE_OPR", "completer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        mockMvc.perform(get("/api/v1/jobs/{id}/history", jobId)
                        .with(operator("ADMIN", "history-reader")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].newStatus").value("ALLOCATED"))
                .andExpect(jsonPath("$[1].previousStatus")
                        .value("ALLOCATED"))
                .andExpect(jsonPath("$[1].newStatus")
                        .value("DISPATCHED"))
                .andExpect(jsonPath("$[1].reference")
                        .value("DISPATCH-2026-1001"))
                .andExpect(jsonPath("$[1].performedByName")
                        .value("Test Operator"))
                .andExpect(jsonPath("$[2].newStatus")
                        .value("COMPLETED"));

        assertAll(
                () -> assertEquals(6, movementRepository.count()),
                () -> assertEquals(3, historyRepository.count()),
                () -> assertThrows(DataAccessException.class, () ->
                        jdbcTemplate.update(
                                "DELETE FROM job_status_history WHERE job_id = ?",
                                jobId
                        ))
        );
    }

    @Test
    void cancellationIsAdminOnlyAndReleasesReservationsOnce()
            throws Exception {
        SeededStock stock = seedStock(40, 5, 8);
        UUID jobId = createAllocatedJob(
                "CRM-CANCEL",
                "cancel-creator",
                stock,
                18
        );
        String cancellation = "{\"reason\":\"Customer withdrew\"}";

        mockMvc.perform(post("/api/v1/jobs/{id}/cancel", jobId)
                        .with(operator("ADMIN", "admin-canceller"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/api/v1/jobs/{id}/cancel", jobId)
                        .with(operator("WAREHOUSE_OPR", "warehouse-canceller"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancellation))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/jobs/{id}/cancel", jobId)
                        .with(operator("ADMIN", "admin-canceller"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancellation))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mockMvc.perform(post("/api/v1/jobs/{id}/cancel", jobId)
                        .with(operator("ADMIN", "admin-canceller"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancellation))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertAll(
                () -> assertInventory(stock.panelId(), 40, 0, 40),
                () -> assertInventory(stock.batteryId(), 5, 0, 5),
                () -> assertInventory(stock.inverterId(), 8, 0, 8),
                () -> assertEquals(6, movementRepository.count()),
                () -> assertEquals(2, historyRepository.count()),
                () -> assertEquals(
                        3L,
                        movementCount(jobId, "JOB_RESERVATION_RELEASE")
                ),
                () -> assertEquals(
                        "Customer withdrew",
                        jdbcTemplate.queryForObject(
                                """
                                SELECT note
                                FROM job_status_history
                                WHERE job_id = ? AND new_status = 'CANCELLED'
                                """,
                                String.class,
                                jobId
                        )
                )
        );
    }

    @Test
    void invalidLifecycleTransitionsReturnAStableConflict()
            throws Exception {
        SeededStock stock = seedStock(40, 5, 8);
        UUID jobId = createAllocatedJob(
                "CRM-INVALID-TRANSITION",
                "transition-creator",
                stock,
                18
        );

        mockMvc.perform(post("/api/v1/jobs/{id}/dispatch", jobId)
                        .with(operator("WAREHOUSE_OPR", "dispatcher"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dispatchReference\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/api/v1/jobs/{id}/complete", jobId)
                        .with(operator("WAREHOUSE_OPR", "early-completer")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_JOB_TRANSITION"));

        mockMvc.perform(post("/api/v1/jobs/{id}/dispatch", jobId)
                        .with(operator("WAREHOUSE_OPR", "dispatcher"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dispatchReference\":\"DISPATCH-INVALID\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/jobs/{id}/cancel", jobId)
                        .with(operator("ADMIN", "late-canceller"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Too late\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_JOB_TRANSITION"));

        assertEquals(JobStatus.DISPATCHED, jobRepository.findById(jobId)
                .orElseThrow()
                .getStatus());
    }

    @Test
    void concurrentCancelAndDispatchApplyExactlyOneInventoryEffect()
            throws Exception {
        SeededStock stock = seedStock(40, 5, 8);
        UUID jobId = createAllocatedJob(
                "CRM-TRANSITION-RACE",
                "race-creator",
                stock,
                18
        );
        provision("ADMIN", "race-canceller");
        provision("WAREHOUSE_OPR", "race-dispatcher");

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> cancellation = executor.submit(() -> {
                start.await();
                return lifecycleRequest(
                        "/api/v1/jobs/" + jobId + "/cancel",
                        "ADMIN",
                        "race-canceller",
                        "{\"reason\":\"Concurrent cancellation\"}"
                );
            });
            Future<Integer> dispatch = executor.submit(() -> {
                start.await();
                return lifecycleRequest(
                        "/api/v1/jobs/" + jobId + "/dispatch",
                        "WAREHOUSE_OPR",
                        "race-dispatcher",
                        "{\"dispatchReference\":\"RACE-DISPATCH\"}"
                );
            });

            start.countDown();
            List<Integer> statuses = new ArrayList<>(
                    List.of(cancellation.get(), dispatch.get())
            );
            Collections.sort(statuses);
            assertEquals(List.of(200, 409), statuses);
        } finally {
            executor.shutdownNow();
        }

        JobStatus finalStatus = jobRepository.findById(jobId)
                .orElseThrow()
                .getStatus();
        assertTrue(
                finalStatus == JobStatus.CANCELLED
                        || finalStatus == JobStatus.DISPATCHED
        );
        if (finalStatus == JobStatus.CANCELLED) {
            assertInventory(stock.panelId(), 40, 0, 40);
            assertInventory(stock.batteryId(), 5, 0, 5);
            assertInventory(stock.inverterId(), 8, 0, 8);
        } else {
            assertInventory(stock.panelId(), 22, 0, 22);
            assertInventory(stock.batteryId(), 4, 0, 4);
            assertInventory(stock.inverterId(), 7, 0, 7);
        }
        assertEquals(6, movementRepository.count());
        assertEquals(2, historyRepository.count());
    }

    private int createConcurrentJob(
            String jobId,
            String subject,
            SeededStock stock
    ) throws Exception {
        return mockMvc.perform(post("/api/v1/jobs")
                        .with(operator("WAREHOUSE_OPR", subject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jobRequest(
                                jobId,
                                stock.installerId(),
                                stock.panelId(), 7,
                                stock.batteryId(), 1,
                                stock.inverterId(), 1
                        )))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private long movementCount(UUID jobId, String movementType) {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM stock_movements
                WHERE job_id = ? AND movement_type = ?
                """,
                Long.class,
                jobId,
                movementType
        );
        return count == null ? 0 : count;
    }

    private UUID createAllocatedJob(
            String jobId,
            String subject,
            SeededStock stock,
            int panelQuantity
    ) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/jobs")
                        .with(operator("WAREHOUSE_OPR", subject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jobRequest(
                                jobId,
                                stock.installerId(),
                                stock.panelId(), panelQuantity,
                                stock.batteryId(), 1,
                                stock.inverterId(), 1
                        )))
                .andExpect(status().isCreated())
                .andReturn();
        return responseId(result);
    }

    private int lifecycleRequest(
            String path,
            String role,
            String subject,
            String content
    ) throws Exception {
        return mockMvc.perform(post(path)
                        .with(operator(role, subject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private void provision(String subject) throws Exception {
        provision("WAREHOUSE_OPR", subject);
    }

    private void provision(String role, String subject) throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .with(operator(role, subject)))
                .andExpect(status().isOk());
    }

    private UUID createProduct(
            String sku,
            String category,
            String brand,
            String model,
            String capacity
    ) throws Exception {
        String request = """
                {
                  "sku": "%s",
                  "category": "%s",
                  "brand": "%s",
                  "model": "%s",
                  "capacity": %s
                }
                """.formatted(sku, category, brand, model, capacity);

        MvcResult result = mockMvc.perform(post("/api/v1/products")
                        .with(operator("ADMIN", "catalog-admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.inventory.availableQuantity").value(0))
                .andReturn();
        return responseId(result);
    }

    private UUID createInstaller(String displayName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/installers")
                        .with(operator("ADMIN", "installer-admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"" + displayName + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return responseId(result);
    }

    private UUID responseId(MvcResult result) throws Exception {
        String value = JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.id"
        );
        return UUID.fromString(value);
    }

    private SeededStock seedStock(
            int panelQuantity,
            int batteryQuantity,
            int inverterQuantity
    ) {
        Product panel = saveProduct(
                "JINKO-440W",
                ProductCategory.PANEL,
                ProductBrand.JINKO,
                "Tiger Neo 440W",
                "440",
                panelQuantity
        );
        Product battery = saveProduct(
                "TESLA-PW3-13.5",
                ProductCategory.BATTERY,
                ProductBrand.TESLA,
                "Powerwall 3",
                "13.5",
                batteryQuantity
        );
        Product inverter = saveProduct(
                "SUNGROW-SH10RS",
                ProductCategory.INVERTER,
                ProductBrand.SUNGROW,
                "SH10RS",
                "10",
                inverterQuantity
        );
        Installer installer = installerRepository.saveAndFlush(
                Installer.create("Installer One")
        );
        return new SeededStock(
                panel.getId(),
                battery.getId(),
                inverter.getId(),
                installer.getId()
        );
    }

    private Product saveProduct(
            String sku,
            ProductCategory category,
            ProductBrand brand,
            String model,
            String capacity,
            int onHand
    ) {
        Product product = productRepository.save(
                Product.create(
                        sku,
                        category,
                        brand,
                        model,
                        new BigDecimal(capacity)
                )
        );
        InventoryBalance balance = InventoryBalance.emptyFor(product);
        if (onHand > 0) {
            balance.receive(onHand);
        }
        balanceRepository.saveAndFlush(balance);
        return product;
    }

    private String jobRequest(
            String jobId,
            UUID installerId,
            UUID firstProductId,
            int firstQuantity,
            UUID secondProductId,
            int secondQuantity,
            UUID thirdProductId,
            int thirdQuantity
    ) {
        return """
                {
                  "jobId": "%s",
                  "customerName": "Jordan Lee",
                  "location": "10 George Street, Sydney NSW",
                  "jobDate": "%s",
                  "installerId": "%s",
                  "materials": [
                    {"productId": "%s", "quantity": %d},
                    {"productId": "%s", "quantity": %d},
                    {"productId": "%s", "quantity": %d}
                  ]
                }
                """.formatted(
                jobId,
                LocalDate.now().plusDays(14),
                installerId,
                firstProductId, firstQuantity,
                secondProductId, secondQuantity,
                thirdProductId, thirdQuantity
        );
    }

    private void assertInventory(
            UUID productId,
            int onHand,
            int reserved,
            int available
    ) {
        InventoryBalance balance = balanceRepository
                .findByProductId(productId)
                .orElseThrow();
        assertAll(
                () -> assertEquals(onHand, balance.getOnHandQuantity()),
                () -> assertEquals(reserved, balance.getReservedQuantity()),
                () -> assertEquals(available, balance.availableQuantity())
        );
    }

    private JwtRequestPostProcessor operator(String role, String subject) {
        return jwt()
                .jwt(token -> token
                        .issuer(ISSUER)
                        .subject(subject)
                        .claim("given_name", "Test")
                        .claim("family_name", "Operator")
                        .claim("email", subject + "@example.com")
                        .claim("email_verified", true)
                        .claim("roles", List.of(role)))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private void cleanDatabase() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    job_status_history,
                    stock_movements,
                    stock_receipts,
                    job_allocations,
                    field_jobs,
                    inventory_balances,
                    products,
                    installers,
                    app_users
                CASCADE
                """);
    }

    private record SeededStock(
            UUID panelId,
            UUID batteryId,
            UUID inverterId,
            UUID installerId
    ) {
    }
}
