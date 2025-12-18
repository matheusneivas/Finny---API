package com.finny.api.application.dto.request;

import com.finny.api.domain.enums.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {
    private LocalDate birthDate;
    private String occupation;
    private IncomeRange incomeRange;
    private Boolean hasDebts;
    private BigDecimal totalDebt;
    private Set<String> debtTypes;
    private Boolean hasEmergencyFund;
    private Integer emergencyFundMonths;
    private Boolean hasInvestments;
    private Set<String> investmentTypes;
    private Set<String> shortTermGoals;
    private Set<String> longTermGoals;
    private String primaryGoal;
    private String primaryGoalTimeframe;
    private ExpenseControlPreference expenseControlPreference;
    private Set<String> mainExpenseCategories;
    private FinancialKnowledgeLevel knowledgeLevel;
    private ReportFrequency reportFrequency;
    private CommunicationStyle communicationStyle;
    private Set<String> notificationPreferences;
}
