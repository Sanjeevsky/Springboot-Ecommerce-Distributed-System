package com.sanjeevsky.wishlistservice.repository;

import com.sanjeevsky.wishlistservice.model.WishlistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WishlistRepository extends JpaRepository<WishlistItem, UUID> {

    List<WishlistItem> findByUserId(String userId);

    Optional<WishlistItem> findByUserIdAndProductId(String userId, UUID productId);

    @Transactional
    void deleteByUserIdAndProductId(String userId, UUID productId);
}
