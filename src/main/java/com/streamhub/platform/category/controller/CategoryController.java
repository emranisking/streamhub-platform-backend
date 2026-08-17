package com.streamhub.platform.category.controller;

import com.streamhub.platform.category.dto.CategoryRequest;
import com.streamhub.platform.category.dto.CategoryResponse;
import com.streamhub.platform.category.entity.Category;
import com.streamhub.platform.category.service.CategoryService;
import com.streamhub.platform.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@Tag(name = "Categories")
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @Operation(summary = "List all categories (public)")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> list() {
        List<CategoryResponse> categories = categoryService.findAll().stream()
                .map(CategoryResponse::from).toList();
        return ResponseEntity.ok(ApiResponse.ok(categories));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a category (ADMIN only)")
    public ResponseEntity<ApiResponse<CategoryResponse>> create(@Valid @RequestBody CategoryRequest request) {
        Category category = categoryService.create(request.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(CategoryResponse.from(category)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a category (ADMIN only)")
    public ResponseEntity<ApiResponse<CategoryResponse>> update(@PathVariable UUID id, @Valid @RequestBody CategoryRequest request) {
        Category category = categoryService.update(id, request.getName());
        return ResponseEntity.ok(ApiResponse.ok(CategoryResponse.from(category)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a category (ADMIN only, soft delete)")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        categoryService.delete(id);
        return ResponseEntity.ok(ApiResponse.message("Category deleted"));
    }
}
