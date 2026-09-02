package org.electrifyingaustralia.fieldops.repository;

import java.util.UUID;
import org.electrifyingaustralia.fieldops.entity.StockReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockReceiptRepository extends JpaRepository<StockReceipt, UUID> {

    boolean existsByReceiptReferenceIgnoreCase(String receiptReference);
}
