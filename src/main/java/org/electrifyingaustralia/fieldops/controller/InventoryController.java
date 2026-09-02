package org.electrifyingaustralia.fieldops.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.electrifyingaustralia.fieldops.dto.request.CreateStockReceiptRequest;
import org.electrifyingaustralia.fieldops.dto.response.ProductResponse;
import org.electrifyingaustralia.fieldops.dto.response.StockReceiptResponse;
import org.electrifyingaustralia.fieldops.enums.ProductCategory;
import org.electrifyingaustralia.fieldops.service.InventoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @PostMapping("/receipts")
    public ResponseEntity<StockReceiptResponse> receive(
            @Valid @RequestBody CreateStockReceiptRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(inventoryService.receive(request, jwt));
    }

    @GetMapping
    public List<ProductResponse> list(
            @RequestParam(required = false) ProductCategory category,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return inventoryService.list(category, jwt);
    }
}
