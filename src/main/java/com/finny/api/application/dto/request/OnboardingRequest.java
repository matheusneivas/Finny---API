package com.finny.api.application.dto.request;

import com.finny.api.domain.enums.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OnboardingRequest {

    // Etapa 1: Informações Básicas
    @NotNull(message = "Data de nascimento é obrigatória")
    @Past(message = "Data de nascimento deve ser no passado")
    private LocalDate birthDate;

    private String occupation;

    @NotNull(message = "Faixa de renda é obrigatória")
    private IncomeRange incomeRange;

    // Etapa 2: Situação Financeira
    private Boolean hasDebts;

    @Positive(message = "Total de dívidas deve ser positivo")
    private BigDecimal totalDebt;

    private Set<String> debtTypes;

    private Boolean hasEmergencyFund;

    @Positive(message = "Meses de fundo de emergência deve ser positivo")
    private Integer emergencyFundMonths;

    private Boolean hasInvestments;

    private Set<String> investmentTypes;

    // Etapa 3: Objetivos
    private Set<String> shortTermGoals;

    private Set<String> longTermGoals;

    private String primaryGoal;

    private String primaryGoalTimeframe;

    // Etapa 4: Preferências de Controle
    @NotNull(message = "Preferência de controle de gastos é obrigatória")
    private ExpenseControlPreference expenseControlPreference;

    private Set<String> mainExpenseCategories;

    @NotNull(message = "Nível de conhecimento financeiro é obrigatório")
    private FinancialKnowledgeLevel knowledgeLevel;

    @NotNull(message = "Frequência de relatórios é obrigatória")
    private ReportFrequency reportFrequency;

    // Etapa 5: Personalização
    @NotNull(message = "Estilo de comunicação é obrigatório")
    private CommunicationStyle communicationStyle;

    private Set<String> notificationPreferences;

    // Validação customizada: idade >= 18 anos
    public boolean isAdult() {
        if (birthDate == null) return false;
        return Period.between(birthDate, LocalDate.now()).getYears() >= 18;
    }
}
