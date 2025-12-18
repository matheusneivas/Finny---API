package com.finny.api.application.service;

import com.finny.api.application.dto.request.CategoryRequest;
import com.finny.api.application.dto.response.CategoryResponse;
import com.finny.api.application.exception.BusinessException;
import com.finny.api.application.exception.EntityNotFoundException;
import com.finny.api.application.mapper.CategoryMapper;
import com.finny.api.domain.entity.Category;
import com.finny.api.domain.entity.User;
import com.finny.api.domain.repository.CategoryRepository;
import com.finny.api.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final CategoryMapper categoryMapper;

    @Transactional(readOnly = true)
    public List<CategoryResponse> getCategories(UUID userId) {
        log.debug("Getting categories for user: {}", userId);

        // Retorna categorias do usuário + categorias padrão
        List<Category> categories = categoryRepository.findByUserIdOrUserIdIsNull(userId);

        return categories.stream()
                .map(categoryMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public CategoryResponse createCategory(UUID userId, CategoryRequest request) {
        log.debug("Creating category for user: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado"));

        Category category = categoryMapper.toEntity(request);
        category.setUser(user);
        category.setIsDefault(false);

        category = categoryRepository.save(category);

        log.info("Category created successfully: {}", category.getId());

        return categoryMapper.toResponse(category);
    }

    @Transactional
    public CategoryResponse updateCategory(UUID userId, UUID categoryId, CategoryRequest request) {
        log.debug("Updating category: {} for user: {}", categoryId, userId);

        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new EntityNotFoundException("Categoria não encontrada"));

        // Verificar se categoria pertence ao usuário
        if (category.getUser() == null || !category.getUser().getId().equals(userId)) {
            throw new BusinessException("Categoria não pertence ao usuário ou é uma categoria padrão");
        }

        categoryMapper.updateEntityFromRequest(request, category);
        category = categoryRepository.save(category);

        log.info("Category updated successfully: {}", categoryId);

        return categoryMapper.toResponse(category);
    }

    @Transactional
    public void deleteCategory(UUID userId, UUID categoryId) {
        log.debug("Deleting category: {} for user: {}", categoryId, userId);

        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new EntityNotFoundException("Categoria não encontrada"));

        // Verificar se categoria pertence ao usuário
        if (category.getUser() == null || !category.getUser().getId().equals(userId)) {
            throw new BusinessException("Categoria não pertence ao usuário ou é uma categoria padrão");
        }

        // TODO: Verificar se há transações usando esta categoria
        // Se houver, pode lançar exceção ou reatribuir para categoria padrão

        categoryRepository.delete(category);

        log.info("Category deleted successfully: {}", categoryId);
    }
}
