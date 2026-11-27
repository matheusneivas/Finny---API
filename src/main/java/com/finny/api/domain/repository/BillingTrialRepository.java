package com.finny.api.domain.repository;

import com.finny.api.domain.entity.BillingTrial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BillingTrialRepository extends JpaRepository<BillingTrial, String> {

}
