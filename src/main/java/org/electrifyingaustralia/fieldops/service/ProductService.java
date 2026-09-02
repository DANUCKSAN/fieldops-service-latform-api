package org.electrifyingaustralia.fieldops.service;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.electrifyingaustralia.fieldops.constant.DatabaseConstraints;
import org.electrifyingaustralia.fieldops.dto.request.CreateProductRequest;
import org.electrifyingaustralia.fieldops.dto.response.ProductResponse;
import org.electrifyingaustralia.fieldops.entity.InventoryBalance;
import org.electrifyingaustralia.fieldops.entity.Product;
import org.electrifyingaustralia.fieldops.enums.ProductCategory;
import org.electrifyingaustralia.fieldops.exception.ConflictException;
import org.electrifyingaustralia.fieldops.exception.InvalidOperationException;
import org.electrifyingaustralia.fieldops.exception.ResourceNotFoundException;
import org.electrifyingaustralia.fieldops.repository.InventoryBalanceRepository;
import org.electrifyingaustralia.fieldops.repository.ProductRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final InventoryBalanceRepository balanceRepository;
    private final UserService userService;

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_OPR')")
    public ProductResponse create(CreateProductRequest request, Jwt jwt) {
        userService.resolveAuthenticatedUser(jwt);

        if (!request.brand().supports(request.category())) {
            throw new InvalidOperationException(
                    "INVALID_PRODUCT_BRAND",
                    request.brand() + " is not supported for " + request.category()
            );
        }

        String sku = request.sku().trim();
        if (productRepository.existsBySkuIgnoreCase(sku)) {
            throw duplicateSku(sku, null);
        }

        Product product = Product.create(
                sku,
                request.category(),
                request.brand(),
                request.model(),
                request.capacity()
        );
        InventoryBalance balance = InventoryBalance.emptyFor(product);

        try {
            productRepository.save(product);
            return ProductResponse.from(balanceRepository.saveAndFlush(balance));
        } catch (DataIntegrityViolationException exception) {
            if (DatabaseConstraints.isNamed(exception, "uq_products_sku")) {
                throw duplicateSku(sku, exception);
            }
            throw exception;
        }
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

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_OPR')")
    public ProductResponse get(UUID id, Jwt jwt) {
        userService.resolveAuthenticatedUser(jwt);
        InventoryBalance balance = balanceRepository.findByProductId(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "PRODUCT_NOT_FOUND",
                        "Product was not found: " + id
                ));
        return ProductResponse.from(balance);
    }

    private ConflictException duplicateSku(String sku, Throwable cause) {
        String message = "A product with SKU " + sku + " already exists";
        return cause == null
                ? new ConflictException("DUPLICATE_PRODUCT_SKU", message)
                : new ConflictException("DUPLICATE_PRODUCT_SKU", message, cause);
    }
}
