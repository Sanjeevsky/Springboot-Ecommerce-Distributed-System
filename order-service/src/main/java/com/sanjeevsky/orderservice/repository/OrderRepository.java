package com.sanjeevsky.orderservice.repository;

import com.sanjeevsky.orderservice.model.Order;
import com.sanjeevsky.platform.model.order.OrderStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
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

    @EntityGraph(attributePaths = "orderItems")
    List<Order> findAllByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
            LocalDateTime from, LocalDateTime to);

    @EntityGraph(attributePaths = "orderItems")
    List<Order> findAllByStatusIn(Collection<OrderStatus> statuses);
}
