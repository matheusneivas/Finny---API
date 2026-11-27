package com.finny.api.domain.repository;

import com.finny.api.domain.entity.BillingEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BillingEventRepository extends JpaRepository<BillingEvent, String> {

}
