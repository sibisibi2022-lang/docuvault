package com.docuvault.category;

import com.docuvault.document.DocumentRepository;
import com.docuvault.security.AuthenticatedUser;
import com.docuvault.shared.audit.AuditAction;
import com.docuvault.shared.audit.AuditService;
import com.docuvault.shared.error.ConflictException;
import com.docuvault.shared.error.NotFoundException;
import com.docuvault.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public CategoryService(CategoryRepository categoryRepository,
                           DocumentRepository documentRepository,
                           UserRepository userRepository,
                           AuditService auditService) {
        this.categoryRepository = categoryRepository;
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<CategoryDtos.CategoryResponse> list(Pageable pageable) {
        return categoryRepository.findAllByOrderByNameAsc(pageable).map(CategoryDtos.CategoryResponse::from);
    }

    @Transactional(readOnly = true)
    public CategoryDtos.CategoryResponse get(Long id) {
        return categoryRepository.findById(id)
                .map(CategoryDtos.CategoryResponse::from)
                .orElseThrow(() -> new NotFoundException("Category", id));
    }

    @Transactional
    public CategoryDtos.CategoryResponse create(AuthenticatedUser caller, CategoryDtos.CategoryRequest request) {
        String name = request.name().trim();

        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("A category named \"" + name + "\" already exists");
        }

        Category category = categoryRepository.save(Category.builder()
                .name(name)
                .description(trimToNull(request.description()))
                .build());

        auditService.record(AuditAction.CATEGORY_CREATED, actor(caller), null, "Created category \"" + name + "\"");
        return CategoryDtos.CategoryResponse.from(category);
    }

    @Transactional
    public CategoryDtos.CategoryResponse update(AuthenticatedUser caller, Long id,
                                                CategoryDtos.CategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Category", id));

        String name = request.name().trim();

        // Allow a rename that only changes case, but block a collision with a
        // *different* category that already holds the name.
        categoryRepository.findByNameIgnoreCase(name)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ConflictException("A category named \"" + name + "\" already exists");
                });

        category.setName(name);
        category.setDescription(trimToNull(request.description()));

        auditService.record(AuditAction.CATEGORY_UPDATED, actor(caller), null, "Updated category \"" + name + "\"");
        return CategoryDtos.CategoryResponse.from(category);
    }

    @Transactional
    public void delete(AuthenticatedUser caller, Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Category", id));

        // Refuse rather than silently orphaning documents. The caller is told
        // exactly why, so they can reassign the documents first.
        if (documentRepository.existsByCategoryId(id)) {
            throw new ConflictException(
                    "This category still has documents assigned to it. Move them first, then delete it.");
        }

        String name = category.getName();
        categoryRepository.delete(category);

        auditService.record(AuditAction.CATEGORY_DELETED, actor(caller), null, "Deleted category \"" + name + "\"");
    }

    private com.docuvault.user.User actor(AuthenticatedUser caller) {
        return userRepository.findById(caller.id()).orElse(null);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
