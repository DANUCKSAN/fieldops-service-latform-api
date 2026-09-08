package org.electrifyingaustralia.fieldops.service;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.electrifyingaustralia.fieldops.constant.DatabaseConstraints;
import org.electrifyingaustralia.fieldops.dto.request.CancelJobRequest;
import org.electrifyingaustralia.fieldops.dto.request.CreateJobRequest;
import org.electrifyingaustralia.fieldops.dto.request.DispatchJobRequest;
import org.electrifyingaustralia.fieldops.dto.request.MaterialAllocationRequest;
import org.electrifyingaustralia.fieldops.dto.response.JobResponse;
import org.electrifyingaustralia.fieldops.dto.response.JobStatusHistoryResponse;
import org.electrifyingaustralia.fieldops.dto.response.JobSummaryResponse;
import org.electrifyingaustralia.fieldops.dto.response.PagedResponse;
import org.electrifyingaustralia.fieldops.entity.FieldJob;
import org.electrifyingaustralia.fieldops.entity.Installer;
import org.electrifyingaustralia.fieldops.entity.InventoryBalance;
import org.electrifyingaustralia.fieldops.entity.JobAllocation;
import org.electrifyingaustralia.fieldops.entity.JobStatusHistory;
import org.electrifyingaustralia.fieldops.entity.Product;
import org.electrifyingaustralia.fieldops.entity.StockMovement;
import org.electrifyingaustralia.fieldops.entity.User;
import org.electrifyingaustralia.fieldops.enums.JobStatus;
import org.electrifyingaustralia.fieldops.enums.ProductCategory;
import org.electrifyingaustralia.fieldops.exception.ConflictException;
import org.electrifyingaustralia.fieldops.exception.InvalidOperationException;
import org.electrifyingaustralia.fieldops.exception.ResourceNotFoundException;
import org.electrifyingaustralia.fieldops.repository.FieldJobRepository;
import org.electrifyingaustralia.fieldops.repository.InstallerRepository;
import org.electrifyingaustralia.fieldops.repository.InventoryBalanceRepository;
import org.electrifyingaustralia.fieldops.repository.JobStatusHistoryRepository;
import org.electrifyingaustralia.fieldops.repository.StockMovementRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class JobService {

    private final FieldJobRepository jobRepository;
    private final InstallerRepository installerRepository;
    private final InventoryBalanceRepository balanceRepository;
    private final StockMovementRepository movementRepository;
    private final JobStatusHistoryRepository historyRepository;
    private final UserService userService;

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_OPR')")
    public JobResponse create(CreateJobRequest request, Jwt jwt) {
        User actor = userService.resolveAuthenticatedUser(jwt);
        String jobId = request.jobId().trim();

        if (jobRepository.existsByJobIdIgnoreCase(jobId)) {
            throw duplicateJob(jobId, null);
        }

        Installer installer = installerRepository.findById(request.installerId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "INSTALLER_NOT_FOUND",
                        "Installer was not found: " + request.installerId()
                ));
        if (!installer.isActive()) {
            throw new ConflictException(
                    "INACTIVE_INSTALLER",
                    "The selected installer is inactive"
            );
        }

        Map<UUID, MaterialAllocationRequest> requestedMaterials =
                uniqueMaterials(request.materials());
        List<InventoryBalance> balances =
                balanceRepository.findAllByProductIdForUpdate(
                        requestedMaterials.keySet()
                );

        ensureAllProductsExist(requestedMaterials.keySet(), balances);
        validateMaterialSelection(balances, requestedMaterials);

        // A concurrent request with the same CRM job ID may have committed
        // while this request waited for inventory row locks.
        if (jobRepository.existsByJobIdIgnoreCase(jobId)) {
            throw duplicateJob(jobId, null);
        }

        ensureStockIsAvailable(balances, requestedMaterials);

        List<FieldJob.Material> allocatedMaterials = balances.stream()
                .map(balance -> new FieldJob.Material(
                        balance.getProduct(),
                        requestedMaterials.get(balance.getProduct().getId()).quantity()
                ))
                .toList();
        FieldJob job = FieldJob.createAllocated(
                jobId,
                request.customerName(),
                request.location(),
                request.jobDate(),
                installer,
                actor,
                allocatedMaterials
        );

        try {
            jobRepository.saveAndFlush(job);
        } catch (DataIntegrityViolationException exception) {
            if (DatabaseConstraints.isNamed(
                    exception,
                    "uq_field_jobs_reference"
            )) {
                throw duplicateJob(jobId, exception);
            }
            throw exception;
        }

        historyRepository.save(JobStatusHistory.initialAllocation(job, actor));

        for (InventoryBalance balance : balances) {
            Product product = balance.getProduct();
            int quantity = requestedMaterials.get(product.getId()).quantity();
            balance.reserve(quantity);
            movementRepository.save(StockMovement.reservation(
                    product,
                    job,
                    quantity,
                    balance.getOnHandQuantity(),
                    balance.getReservedQuantity(),
                    actor
            ));
        }

        balanceRepository.saveAllAndFlush(balances);
        movementRepository.flush();
        return JobResponse.from(job);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_OPR')")
    public JobResponse get(UUID id, Jwt jwt) {
        userService.resolveAuthenticatedUser(jwt);
        FieldJob job = jobRepository.findDetailedById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "JOB_NOT_FOUND",
                        "Job was not found: " + id
                ));
        return JobResponse.from(job);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_OPR')")
    public JobResponse getByJobId(String jobId, Jwt jwt) {
        userService.resolveAuthenticatedUser(jwt);
        String normalizedJobId = jobId == null ? "" : jobId.trim();
        FieldJob job = jobRepository
                .findDetailedByJobIdIgnoreCase(normalizedJobId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "JOB_NOT_FOUND",
                        "Job was not found: " + normalizedJobId
                ));
        return JobResponse.from(job);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_OPR')")
    public PagedResponse<JobSummaryResponse> list(
            int page,
            int size,
            Jwt jwt
    ) {
        userService.resolveAuthenticatedUser(jwt);
        PageRequest pageRequest = PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Order.desc("jobDate"),
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                )
        );
        Page<JobSummaryResponse> jobs = jobRepository.findAllBy(pageRequest)
                .map(JobSummaryResponse::from);
        return PagedResponse.from(jobs);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_OPR')")
    public JobResponse dispatch(
            UUID id,
            DispatchJobRequest request,
            Jwt jwt
    ) {
        User actor = userService.resolveAuthenticatedUser(jwt);
        FieldJob job = findForUpdate(id);

        if (job.getStatus() == JobStatus.DISPATCHED) {
            return JobResponse.from(job);
        }
        requireTransition(job, JobStatus.ALLOCATED, JobStatus.DISPATCHED);

        Map<UUID, Integer> quantities = allocationQuantities(job);
        List<InventoryBalance> balances = lockAllocatedBalances(quantities);
        ensureReservationsExist(balances, quantities);

        job.dispatch();
        for (InventoryBalance balance : balances) {
            int quantity = quantities.get(balance.getProduct().getId());
            balance.dispatchReserved(quantity);
            movementRepository.save(StockMovement.dispatch(
                    balance.getProduct(),
                    job,
                    quantity,
                    balance.getOnHandQuantity(),
                    balance.getReservedQuantity(),
                    request.dispatchReference(),
                    request.note(),
                    actor
            ));
        }
        historyRepository.save(JobStatusHistory.transition(
                job,
                JobStatus.ALLOCATED,
                JobStatus.DISPATCHED,
                request.dispatchReference(),
                request.note(),
                actor
        ));

        flushLifecycleChanges(balances);
        return JobResponse.from(job);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public JobResponse cancel(UUID id, CancelJobRequest request, Jwt jwt) {
        User actor = userService.resolveAuthenticatedUser(jwt);
        FieldJob job = findForUpdate(id);

        if (job.getStatus() == JobStatus.CANCELLED) {
            return JobResponse.from(job);
        }
        requireTransition(job, JobStatus.ALLOCATED, JobStatus.CANCELLED);

        Map<UUID, Integer> quantities = allocationQuantities(job);
        List<InventoryBalance> balances = lockAllocatedBalances(quantities);
        ensureReservationsExist(balances, quantities);

        job.cancel();
        for (InventoryBalance balance : balances) {
            int quantity = quantities.get(balance.getProduct().getId());
            balance.releaseReservation(quantity);
            movementRepository.save(StockMovement.reservationRelease(
                    balance.getProduct(),
                    job,
                    quantity,
                    balance.getOnHandQuantity(),
                    balance.getReservedQuantity(),
                    request.reason(),
                    actor
            ));
        }
        historyRepository.save(JobStatusHistory.transition(
                job,
                JobStatus.ALLOCATED,
                JobStatus.CANCELLED,
                job.getJobId(),
                request.reason(),
                actor
        ));

        flushLifecycleChanges(balances);
        return JobResponse.from(job);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_OPR')")
    public JobResponse complete(UUID id, Jwt jwt) {
        User actor = userService.resolveAuthenticatedUser(jwt);
        FieldJob job = findForUpdate(id);

        if (job.getStatus() == JobStatus.COMPLETED) {
            return JobResponse.from(job);
        }
        requireTransition(job, JobStatus.DISPATCHED, JobStatus.COMPLETED);

        job.complete();
        historyRepository.save(JobStatusHistory.transition(
                job,
                JobStatus.DISPATCHED,
                JobStatus.COMPLETED,
                null,
                null,
                actor
        ));
        jobRepository.flush();
        historyRepository.flush();
        return JobResponse.from(job);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_OPR')")
    public List<JobStatusHistoryResponse> history(UUID id, Jwt jwt) {
        userService.resolveAuthenticatedUser(jwt);
        if (!jobRepository.existsById(id)) {
            throw jobNotFound(id);
        }
        return historyRepository
                .findAllByJobIdOrderByCreatedAtAscIdAsc(id)
                .stream()
                .map(JobStatusHistoryResponse::from)
                .toList();
    }

    private FieldJob findForUpdate(UUID id) {
        return jobRepository.findByIdForUpdate(id)
                .orElseThrow(() -> jobNotFound(id));
    }

    private Map<UUID, Integer> allocationQuantities(FieldJob job) {
        Map<UUID, Integer> quantities = new HashMap<>();
        for (JobAllocation allocation : job.getAllocations()) {
            quantities.put(
                    allocation.getProduct().getId(),
                    allocation.getQuantity()
            );
        }
        return quantities;
    }

    private List<InventoryBalance> lockAllocatedBalances(
            Map<UUID, Integer> quantities
    ) {
        List<InventoryBalance> balances =
                balanceRepository.findAllByProductIdForUpdate(
                        quantities.keySet()
                );
        ensureAllProductsExist(quantities.keySet(), balances);
        return balances;
    }

    private void ensureReservationsExist(
            List<InventoryBalance> balances,
            Map<UUID, Integer> quantities
    ) {
        for (InventoryBalance balance : balances) {
            int expected = quantities.get(balance.getProduct().getId());
            if (balance.getReservedQuantity() < expected) {
                throw new ConflictException(
                        "RESERVATION_MISMATCH",
                        "Reserved stock no longer matches job allocation for "
                                + balance.getProduct().getSku()
                );
            }
        }
    }

    private void requireTransition(
            FieldJob job,
            JobStatus expected,
            JobStatus requested
    ) {
        if (job.getStatus() != expected) {
            throw new ConflictException(
                    "INVALID_JOB_TRANSITION",
                    "Job " + job.getJobId()
                            + " cannot transition from " + job.getStatus()
                            + " to " + requested
            );
        }
    }

    private void flushLifecycleChanges(List<InventoryBalance> balances) {
        balanceRepository.saveAllAndFlush(balances);
        jobRepository.flush();
        movementRepository.flush();
        historyRepository.flush();
    }

    private ResourceNotFoundException jobNotFound(UUID id) {
        return new ResourceNotFoundException(
                "JOB_NOT_FOUND",
                "Job was not found: " + id
        );
    }

    private Map<UUID, MaterialAllocationRequest> uniqueMaterials(
            List<MaterialAllocationRequest> materials
    ) {
        Map<UUID, MaterialAllocationRequest> byProduct = new HashMap<>();
        for (MaterialAllocationRequest material : materials) {
            if (byProduct.put(material.productId(), material) != null) {
                throw new InvalidOperationException(
                        "INVALID_MATERIAL_COMBINATION",
                        "A product can only be allocated once per job"
                );
            }
            if (material.quantity() <= 0) {
                throw new InvalidOperationException(
                        "INVALID_MATERIAL_QUANTITY",
                        "Material quantities must be greater than zero"
                );
            }
        }
        return byProduct;
    }

    private void ensureAllProductsExist(
            Set<UUID> requestedProductIds,
            List<InventoryBalance> balances
    ) {
        Set<UUID> found = new LinkedHashSet<>();
        balances.forEach(balance -> found.add(balance.getProduct().getId()));

        requestedProductIds.stream()
                .filter(productId -> !found.contains(productId))
                .findFirst()
                .ifPresent(productId -> {
                    throw new ResourceNotFoundException(
                            "PRODUCT_NOT_FOUND",
                            "Product was not found: " + productId
                    );
                });
    }

    private void validateMaterialSelection(
            List<InventoryBalance> balances,
            Map<UUID, MaterialAllocationRequest> requestedMaterials
    ) {
        Set<ProductCategory> categories = EnumSet.noneOf(ProductCategory.class);
        for (InventoryBalance balance : balances) {
            Product product = balance.getProduct();
            if (!product.isActive()) {
                throw new ConflictException(
                        "INACTIVE_PRODUCT",
                        "Inactive product cannot be allocated: " + product.getSku()
                );
            }
            if (!categories.add(product.getCategory())) {
                throw invalidMaterialCombination();
            }
            if (requestedMaterials.get(product.getId()).quantity() <= 0) {
                throw new InvalidOperationException(
                        "INVALID_MATERIAL_QUANTITY",
                        "Material quantities must be greater than zero"
                );
            }
        }

        if (!categories.equals(EnumSet.allOf(ProductCategory.class))) {
            throw invalidMaterialCombination();
        }
    }

    private void ensureStockIsAvailable(
            List<InventoryBalance> balances,
            Map<UUID, MaterialAllocationRequest> requestedMaterials
    ) {
        for (InventoryBalance balance : balances) {
            int requested = requestedMaterials
                    .get(balance.getProduct().getId())
                    .quantity();
            if (balance.availableQuantity() < requested) {
                throw new ConflictException(
                        "INSUFFICIENT_STOCK",
                        "Insufficient available stock for "
                                + balance.getProduct().getSku()
                                + ": requested " + requested
                                + ", available " + balance.availableQuantity()
                );
            }
        }
    }

    private InvalidOperationException invalidMaterialCombination() {
        return new InvalidOperationException(
                "INVALID_MATERIAL_COMBINATION",
                "Select exactly one PANEL, one BATTERY, and one INVERTER"
        );
    }

    private ConflictException duplicateJob(String jobId, Throwable cause) {
        String message = "A job with jobId " + jobId + " already exists";
        return cause == null
                ? new ConflictException("DUPLICATE_JOB_ID", message)
                : new ConflictException("DUPLICATE_JOB_ID", message, cause);
    }
}
