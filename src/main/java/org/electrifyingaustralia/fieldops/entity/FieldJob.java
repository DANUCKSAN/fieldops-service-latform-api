package org.electrifyingaustralia.fieldops.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.electrifyingaustralia.fieldops.enums.JobStatus;
import org.electrifyingaustralia.fieldops.enums.ProductCategory;

@Getter
@Entity
@Table(name = "field_jobs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FieldJob extends BaseEntity {

    @Column(name = "job_reference", nullable = false, length = 80)
    private String jobId;

    @Column(name = "customer_name", nullable = false, length = 160)
    private String customerName;

    @Column(nullable = false, length = 500)
    private String location;

    @Column(name = "job_date", nullable = false)
    private LocalDate jobDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "installer_id", nullable = false)
    private Installer installer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private JobStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdBy;

    @OneToMany(mappedBy = "job", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<JobAllocation> allocations = new ArrayList<>();

    private FieldJob(
            String jobId,
            String customerName,
            String location,
            LocalDate jobDate,
            Installer installer,
            User createdBy
    ) {
        this.jobId = requireText(jobId, "jobId");
        this.customerName = requireText(customerName, "customerName");
        this.location = requireText(location, "location");
        this.jobDate = Objects.requireNonNull(jobDate, "jobDate is required");
        this.installer = Objects.requireNonNull(installer, "installer is required");
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy is required");
    }

    public static FieldJob createAllocated(
            String jobId,
            String customerName,
            String location,
            LocalDate jobDate,
            Installer installer,
            User createdBy,
            List<Material> materials
    ) {
        if (materials == null || materials.size() != 3) {
            throw new IllegalArgumentException(
                    "An allocated job requires exactly three materials"
            );
        }

        FieldJob job = new FieldJob(
                jobId,
                customerName,
                location,
                jobDate,
                installer,
                createdBy
        );

        EnumSet<ProductCategory> categories =
                EnumSet.noneOf(ProductCategory.class);
        for (Material material : materials) {
            Product product = Objects.requireNonNull(
                    material.product(),
                    "material product is required"
            );
            if (material.quantity() <= 0 || !categories.add(product.getCategory())) {
                throw new IllegalArgumentException(
                        "An allocated job requires one positive quantity from each category"
                );
            }
            job.allocations.add(JobAllocation.create(
                    job,
                    product,
                    material.quantity()
            ));
        }

        if (!categories.equals(EnumSet.allOf(ProductCategory.class))) {
            throw new IllegalArgumentException(
                    "An allocated job requires one product from each category"
            );
        }
        job.status = JobStatus.ALLOCATED;
        return job;
    }

    public List<JobAllocation> getAllocations() {
        return Collections.unmodifiableList(allocations);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    public record Material(Product product, int quantity) {
    }
}
