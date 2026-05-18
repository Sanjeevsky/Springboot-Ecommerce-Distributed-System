package com.sanjeevsky.reviewservice.repository;

import com.sanjeevsky.reviewservice.model.OrderEligibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface OrderEligibilityRepository extends JpaRepository<OrderEligibility, UUID> {

    boolean existsByUserIdAndProductId(String userId, UUID productId);
}
