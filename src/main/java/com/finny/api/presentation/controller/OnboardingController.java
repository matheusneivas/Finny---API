package com.finny.api.presentation.controller;

import com.finny.api.application.dto.request.OnboardingRequest;
import com.finny.api.application.dto.response.ProfileResponse;
import com.finny.api.application.service.OnboardingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/onboarding")
@RequiredArgsConstructor
@Tag(name = "Onboarding", description = "Endpoints de onboarding")
@SecurityRequirement(name = "bearerAuth")
public class OnboardingController {

    private final OnboardingService onboardingService;

    @PostMapping("/complete")
    @Operation(summary = "Completar onboarding")
    public ResponseEntity<ProfileResponse> completeOnboarding(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody OnboardingRequest request) {

        UUID userId = UUID.fromString(userDetails.getUsername());
        ProfileResponse response = onboardingService.completeOnboarding(userId, request);

        return ResponseEntity.ok(response);
    }
}
