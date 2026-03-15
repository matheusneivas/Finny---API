package com.finny.api.application.dto.request;

import com.finny.api.domain.enums.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OnboardingStep5Request {

    private Set<String> shortTermGoals;

    private Set<String> longTermGoals;

    private String primaryGoal;

    private String primaryGoalTimeframe;

    @NotNull(message = "Preferência de controle de gastos é obrigatória")
    private ExpenseControlPreference expenseControlPreference;

    private Set<String> mainExpenseCategories;

    @NotNull(message = "Nível de conhecimento financeiro é obrigatório")
    private FinancialKnowledgeLevel knowledgeLevel;

    @NotNull(message = "Frequência de relatórios é obrigatória")
    private ReportFrequency reportFrequency;

    @NotNull(message = "Estilo de comunicação é obrigatório")
    private CommunicationStyle communicationStyle;

    private Set<String> notificationPreferences;
}
