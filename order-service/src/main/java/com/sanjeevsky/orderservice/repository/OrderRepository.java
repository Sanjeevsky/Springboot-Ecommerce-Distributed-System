package com.sanjeevsky.orderservice.repository;

import com.sanjeevsky.orderservice.model.Order;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {
    @EntityGraph(attributePaths = {"orderItems", "shippingAddress"})
    Optional<Order> findByIdAndUserId(UUID id, String userId);

    @EntityGraph(attributePaths = {"orderItems", "shippingAddress"})
    Optional<Order> findByUserIdAndIdempotencyKey(String userId, String idempotencyKey);

    List<Order> findAllByUserId(String userId);
}
