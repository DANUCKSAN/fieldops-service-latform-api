package org.electrifyingaustralia.fieldops.repository;

import java.util.UUID;
import org.electrifyingaustralia.fieldops.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    boolean existsBySkuIgnoreCase(String sku);
}
