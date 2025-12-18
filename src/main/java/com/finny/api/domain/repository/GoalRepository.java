package com.finny.api.domain.repository;

import com.finny.api.domain.entity.Goal;
import com.finny.api.domain.enums.GoalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GoalRepository extends JpaRepository<Goal, UUID> {

    // Buscar meta por ID e usuário (tenant isolation)
    Optional<Goal> findByIdAndUserId(UUID id, UUID userId);

    // Buscar todas as metas do usuário
    List<Goal> findByUserIdOrderByCreatedAtDesc(UUID userId);

    // Buscar metas por status
    List<Goal> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, GoalStatus status);

    // Buscar metas ativas do usuário
    List<Goal> findByUserIdAndStatusOrderByDeadlineAsc(UUID userId, GoalStatus status);

    // Contar metas do usuário
    long countByUserId(UUID userId);

    // Contar metas ativas do usuário
    long countByUserIdAndStatus(UUID userId, GoalStatus status);

    /**
     * Conta metas ativas do usuário (query customizada)
     */
    @Query("SELECT COUNT(g) FROM Goal g " +
           "WHERE g.user.id = :userId " +
           "AND g.status = 'ACTIVE'")
    long countActiveByUserId(@Param("userId") UUID userId);

    /**
     * Verifica se usuário é dono da meta
     */
    @Query("SELECT CASE WHEN COUNT(g) > 0 THEN true ELSE false END " +
           "FROM Goal g " +
           "WHERE g.id = :goalId " +
           "AND g.user.id = :userId")
    boolean existsByIdAndUserId(
        @Param("goalId") UUID goalId,
        @Param("userId") UUID userId
    );
}
