package com.finny.api.domain.entity;

import com.finny.api.domain.enums.*;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "user_profiles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class UserProfile {

    @Id
    private UUID userId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    // Etapa 1: Informações Básicas
    @Column
    private LocalDate birthDate;

    @Column(length = 255)
    private String occupation;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private IncomeRange incomeRange;

    // Etapa 2: Situação Financeira
    @Column
    private Boolean hasDebts;

    @Column(precision = 15, scale = 2)
    private BigDecimal totalDebt;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "user_debt_types", joinColumns = @JoinColumn(name = "user_profile_user_id"))
    @Column(name = "debt_types", length = 50)
    private Set<String> debtTypes = new HashSet<>();

    @Column
    private Boolean hasEmergencyFund;

    @Column
    private Integer emergencyFundMonths;

    @Column
    private Boolean hasInvestments;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "user_investment_types", joinColumns = @JoinColumn(name = "user_profile_user_id"))
    @Column(name = "investment_types", length = 50)
    private Set<String> investmentTypes = new HashSet<>();

    // Etapa 3: Objetivos
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "user_short_term_goals", joinColumns = @JoinColumn(name = "user_profile_user_id"))
    @Column(name = "short_term_goals", length = 100)
    private Set<String> shortTermGoals = new HashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "user_long_term_goals", joinColumns = @JoinColumn(name = "user_profile_user_id"))
    @Column(name = "long_term_goals", length = 100)
    private Set<String> longTermGoals = new HashSet<>();

    @Column(length = 255)
    private String primaryGoal;

    @Column(length = 50)
    private String primaryGoalTimeframe;

    // Etapa 4: Preferências de Controle
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ExpenseControlPreference expenseControlPreference;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "user_main_categories", joinColumns = @JoinColumn(name = "user_profile_user_id"))
    @Column(name = "main_expense_categories", length = 50)
    private Set<String> mainExpenseCategories = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private FinancialKnowledgeLevel knowledgeLevel;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ReportFrequency reportFrequency;

    // Etapa 5: Personalização
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private CommunicationStyle communicationStyle;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "user_notification_preferences", joinColumns = @JoinColumn(name = "user_profile_user_id"))
    @Column(name = "notification_preferences", length = 100)
    private Set<String> notificationPreferences = new HashSet<>();

    // Metadata
    @Column(nullable = false)
    private Boolean onboardingCompleted = false;

    @Column
    private LocalDateTime onboardingCompletedAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
