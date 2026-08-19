package com.streamhub.platform.category.service;

import com.streamhub.platform.category.dto.CategoryResponse;
import com.streamhub.platform.category.entity.Category;
import com.streamhub.platform.category.repository.CategoryRepository;
import com.streamhub.platform.common.exception.BadRequestException;
import com.streamhub.platform.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Cacheable(cacheNames = "categories")
    public List<CategoryResponse> findAll() {
        return categoryRepository.findAll()
                .stream()
                .map(CategoryResponse::from)
                .toList();
    }

    public Category findById(UUID id) {
        return categoryRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Category not found: " + id));
    }

    @CacheEvict(cacheNames = "categories", allEntries = true)
    public Category create(String name) {
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new BadRequestException(
                    "A category with this name already exists"
            );
        }

        return categoryRepository.save(
                Category.builder()
                        .name(name)
                        .build()
        );
    }

    @CacheEvict(cacheNames = "categories", allEntries = true)
    public Category update(UUID id, String name) {
        Category category = findById(id);
        category.setName(name);
        return categoryRepository.save(category);
    }

    @CacheEvict(cacheNames = "categories", allEntries = true)
    public void delete(UUID id) {
        Category category = findById(id);
        category.markDeleted();
        categoryRepository.save(category);
    }
}