package com.streamhub.platform.category.dto;

import com.streamhub.platform.category.entity.Category;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class CategoryResponse {
    private UUID id;
    private String name;

    public static CategoryResponse from(Category category) {
        return CategoryResponse.builder().id(category.getId()).name(category.getName()).build();
    }
}
