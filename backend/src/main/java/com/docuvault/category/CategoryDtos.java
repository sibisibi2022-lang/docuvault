package com.docuvault.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class CategoryDtos {

    private CategoryDtos() {
    }

    public record CategoryRequest(
            @NotBlank(message = "Name is required")
            @Size(max = 100, message = "Name must be at most 100 characters")
            String name,

            @Size(max = 500, message = "Description must be at most 500 characters")
            String description) {
    }

    public record CategoryResponse(
            Long id,
            String name,
            String description,
            Instant createdAt,
            Instant updatedAt) {

        static CategoryResponse from(Category category) {
            return new CategoryResponse(
                    category.getId(),
                    category.getName(),
                    category.getDescription(),
                    category.getCreatedAt(),
                    category.getUpdatedAt());
        }
    }
}
