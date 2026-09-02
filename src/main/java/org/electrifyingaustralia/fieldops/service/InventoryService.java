package org.electrifyingaustralia.fieldops.service;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.electrifyingaustralia.fieldops.constant.DatabaseConstraints;
import org.electrifyingaustralia.fieldops.dto.request.CreateStockReceiptRequest;
import org.electrifyingaustralia.fieldops.dto.request.StockReceiptItemRequest;
import org.electrifyingaustralia.fieldops.dto.response.ProductResponse;
import org.electrifyingaustralia.fieldops.dto.response.StockReceiptItemResponse;
import org.electrifyingaustralia.fieldops.dto.response.StockReceiptResponse;
import org.electrifyingaustralia.fieldops.entity.InventoryBalance;
import org.electrifyingaustralia.fieldops.entity.Product;
import org.electrifyingaustralia.fieldops.entity.StockMovement;
import org.electrifyingaustralia.fieldops.entity.StockReceipt;
import org.electrifyingaustralia.fieldops.entity.User;
import org.electrifyingaustralia.fieldops.enums.ProductCategory;
import org.electrifyingaustralia.fieldops.exception.ConflictException;
import org.electrifyingaustralia.fieldops.exception.InvalidOperationException;
import org.electrifyingaustralia.fieldops.exception.ResourceNotFoundException;
import org.electrifyingaustralia.fieldops.repository.InventoryBalanceRepository;
import org.electrifyingaustralia.fieldops.repository.StockMovementRepository;
import org.electrifyingaustralia.fieldops.repository.StockReceiptRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryBalanceRepository balanceRepository;
    private final StockReceiptRepository receiptRepository;
    private final StockMovementRepository movementRepository;
    private final UserService userService;

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_OPR')")
    public StockReceiptResponse receive(
            CreateStockReceiptRequest request,
            Jwt jwt
    ) {
        User actor = userService.resolveAuthenticatedUser(jwt);
        String reference = request.receiptReference().trim();

        if (receiptRepository.existsByReceiptReferenceIgnoreCase(reference)) {
            throw duplicateReceipt(reference, null);
        }

        Map<UUID, StockReceiptItemRequest> requestedItems =
                uniqueItems(request.items());
        StockReceipt receipt = StockReceipt.create(
                reference,
                request.receivedAt(),
                request.note(),
                actor
        );

        try {
            receiptRepository.saveAndFlush(receipt);
        } catch (DataIntegrityViolationException exception) {
            if (DatabaseConstraints.isNamed(
                    exception,
                    "uq_stock_receipts_reference"
            )) {
                throw duplicateReceipt(reference, exception);
            }
            throw exception;
        }

        List<InventoryBalance> balances =
                balanceRepository.findAllByProductIdForUpdate(
                        requestedItems.keySet()
                );
        ensureAllInventoryExists(requestedItems.keySet(), balances);

        List<StockReceiptItemResponse> responseItems = balances.stream()
                .map(balance -> receiveItem(
                        balance,
                        requestedItems.get(balance.getProduct().getId()),
                        receipt,
                        actor
                ))
                .toList();

        balanceRepository.saveAllAndFlush(balances);
        movementRepository.flush();
        return StockReceiptResponse.from(receipt, responseItems);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_OPR')")
    public List<ProductResponse> list(ProductCategory category, Jwt jwt) {
        userService.resolveAuthenticatedUser(jwt);
        List<InventoryBalance> balances = category == null
                ? balanceRepository.findAllActiveProducts()
                : balanceRepository.findAllActiveProductsByCategory(category);
        return balances.stream().map(ProductResponse::from).toList();
    }

    private StockReceiptItemResponse receiveItem(
            InventoryBalance balance,
            StockReceiptItemRequest item,
            StockReceipt receipt,
            User actor
    ) {
        if (!balance.getProduct().isActive()) {
            throw new ConflictException(
                    "INACTIVE_PRODUCT",
                    "Stock cannot be received for inactive product "
                            + balance.getProduct().getSku()
            );
        }

        try {
            balance.receive(item.quantity());
        } catch (ArithmeticException exception) {
            throw new ConflictException(
                    "STOCK_CAPACITY_EXCEEDED",
                    "The resulting stock quantity is too large",
                    exception
            );
        }

        StockMovement movement = StockMovement.receipt(
                balance.getProduct(),
                receipt,
                item.quantity(),
                balance.getOnHandQuantity(),
                balance.getReservedQuantity(),
                receipt.getReceiptReference(),
                receipt.getNote(),
                actor
        );
        movementRepository.save(movement);
        return StockReceiptItemResponse.from(balance, item.quantity());
    }

    private Map<UUID, StockReceiptItemRequest> uniqueItems(
            List<StockReceiptItemRequest> items
    ) {
        Map<UUID, StockReceiptItemRequest> byProduct = new HashMap<>();
        for (StockReceiptItemRequest item : items) {
            if (byProduct.put(item.productId(), item) != null) {
                throw new InvalidOperationException(
                        "DUPLICATE_RECEIPT_PRODUCT",
                        "A product can only appear once in a stock receipt"
                );
            }
        }
        return byProduct;
    }

    private void ensureAllInventoryExists(
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

    private ConflictException duplicateReceipt(
            String reference,
            Throwable cause
    ) {
        String message = "A stock receipt with reference "
                + reference + " already exists";
        return cause == null
                ? new ConflictException("DUPLICATE_RECEIPT_REFERENCE", message)
                : new ConflictException(
                        "DUPLICATE_RECEIPT_REFERENCE",
                        message,
                        cause
                );
    }
}
