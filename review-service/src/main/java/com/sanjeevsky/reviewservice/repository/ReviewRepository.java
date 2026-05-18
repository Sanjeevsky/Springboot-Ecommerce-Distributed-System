package com.sanjeevsky.reviewservice.repository;

import com.sanjeevsky.reviewservice.model.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReviewRepository extends JpaRepository<Review, UUID> {

    List<Review> findByProductIdAndStatus(UUID productId, String status);

    List<Review> findByUserId(String userId);

    List<Review> findByProductId(UUID productId);
}
