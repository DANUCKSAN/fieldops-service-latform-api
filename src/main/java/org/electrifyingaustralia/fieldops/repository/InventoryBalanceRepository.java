package org.electrifyingaustralia.fieldops.repository;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.electrifyingaustralia.fieldops.entity.InventoryBalance;
import org.electrifyingaustralia.fieldops.enums.ProductCategory;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface InventoryBalanceRepository
        extends JpaRepository<InventoryBalance, UUID> {

    Optional<InventoryBalance> findByProductId(UUID productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(
            name = "jakarta.persistence.lock.timeout",
            value = "5000"
    ))
    @Query("""
            select balance
            from InventoryBalance balance
            join fetch balance.product product
            where product.id in :productIds
            order by product.id
            """)
    List<InventoryBalance> findAllByProductIdForUpdate(
            @Param("productIds") Collection<UUID> productIds
    );

    @EntityGraph(attributePaths = "product")
    @Query("""
            select balance
            from InventoryBalance balance
            where balance.product.active = true
            order by balance.product.category,
                     balance.product.brand,
                     balance.product.model
            """)
    List<InventoryBalance> findAllActiveProducts();

    @EntityGraph(attributePaths = "product")
    @Query("""
            select balance
            from InventoryBalance balance
            where balance.product.active = true
              and balance.product.category = :category
            order by balance.product.brand, balance.product.model
            """)
    List<InventoryBalance> findAllActiveProductsByCategory(
            @Param("category") ProductCategory category
    );
}
