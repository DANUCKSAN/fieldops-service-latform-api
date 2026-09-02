package org.electrifyingaustralia.fieldops.controller;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.electrifyingaustralia.fieldops.dto.request.CreateProductRequest;
import org.electrifyingaustralia.fieldops.dto.response.ProductResponse;
import org.electrifyingaustralia.fieldops.enums.ProductCategory;
import org.electrifyingaustralia.fieldops.service.ProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
    public ResponseEntity<ProductResponse> create(
            @Valid @RequestBody CreateProductRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        ProductResponse response = productService.create(request, jwt);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public List<ProductResponse> list(
            @RequestParam(required = false) ProductCategory category,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return productService.list(category, jwt);
    }

    @GetMapping("/{id}")
    public ProductResponse get(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return productService.get(id, jwt);
    }
}
