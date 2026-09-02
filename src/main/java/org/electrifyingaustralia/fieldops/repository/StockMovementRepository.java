package org.electrifyingaustralia.fieldops.repository;

import java.util.UUID;
import org.electrifyingaustralia.fieldops.entity.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {
}
