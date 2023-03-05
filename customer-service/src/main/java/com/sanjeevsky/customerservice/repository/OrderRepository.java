package com.sanjeevsky.customerservice.repository;

import com.sanjeevsky.customerservice.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByIdAndUserId(UUID id, UUID userId);
}
