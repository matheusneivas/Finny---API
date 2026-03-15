package com.finny.api.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnboardingStepResponse {

    private int currentStep;
    private int totalSteps;
    private int progressPercentage;
    private boolean completed;
    private String nextStepDescription;
    private ProfileResponse profile;
}
