package com.finny.api.domain.repository;

import com.finny.api.domain.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    /**
     * Busca todas as categorias disponíveis para o usuário
     * Inclui: categorias padrão (user_id = null) + categorias personalizadas do usuário
     * Ordenação: Padrão primeiro, depois alfabético
     */
    @Query("SELECT c FROM Category c " +
           "WHERE c.isDefault = true " +
           "OR c.user.id = :userId " +
           "ORDER BY c.isDefault DESC, c.name ASC")
    List<Category> findAllAvailableForUser(@Param("userId") UUID userId);

    /**
     * Busca apenas categorias padrão (visíveis para todos)
     */
    @Query("SELECT c FROM Category c WHERE c.isDefault = true ORDER BY c.name ASC")
    List<Category> findAllDefault();

    /**
     * Busca apenas categorias personalizadas do usuário
     */
    @Query("SELECT c FROM Category c " +
           "WHERE c.user.id = :userId " +
           "AND c.isDefault = false " +
           "ORDER BY c.name ASC")
    List<Category> findCustomByUserId(@Param("userId") UUID userId);

    /**
     * Verifica se usuário já possui categoria com este nome
     * (case-insensitive)
     */
    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END " +
           "FROM Category c " +
           "WHERE c.user.id = :userId " +
           "AND LOWER(c.name) = LOWER(:name)")
    boolean existsByUserIdAndNameIgnoreCase(
        @Param("userId") UUID userId,
        @Param("name") String name
    );

    /**
     * Verifica se categoria pertence ao usuário OU é padrão
     */
    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END " +
           "FROM Category c " +
           "WHERE c.id = :categoryId " +
           "AND (c.isDefault = true OR c.user.id = :userId)")
    boolean isAccessibleByUser(
        @Param("categoryId") UUID categoryId,
        @Param("userId") UUID userId
    );

    /**
     * Busca categoria verificando acesso do usuário
     */
    @Query("SELECT c FROM Category c " +
           "WHERE c.id = :categoryId " +
           "AND (c.isDefault = true OR c.user.id = :userId)")
    Optional<Category> findByIdAndAccessibleByUser(
        @Param("categoryId") UUID categoryId,
        @Param("userId") UUID userId
    );

    /**
     * Conta quantas categorias personalizadas o usuário tem
     */
    @Query("SELECT COUNT(c) FROM Category c " +
           "WHERE c.user.id = :userId " +
           "AND c.isDefault = false")
    long countCustomByUserId(@Param("userId") UUID userId);

    // Legacy methods (mantidos para compatibilidade)
    @Query("SELECT c FROM Category c WHERE c.user.id = :userId OR c.user IS NULL")
    List<Category> findByUserIdOrUserIdIsNull(@Param("userId") UUID userId);

    List<Category> findByUserIsNull();

    List<Category> findByUserId(UUID userId);
}
