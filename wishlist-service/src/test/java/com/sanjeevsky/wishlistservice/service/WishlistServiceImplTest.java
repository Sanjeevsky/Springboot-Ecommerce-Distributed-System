package com.sanjeevsky.wishlistservice.service;

import com.sanjeevsky.wishlistservice.clients.CartFeignClient;
import com.sanjeevsky.wishlistservice.dto.AddToWishlistRequest;
import com.sanjeevsky.wishlistservice.exceptions.WishlistItemAlreadyExistsException;
import com.sanjeevsky.wishlistservice.exceptions.WishlistItemNotFoundException;
import com.sanjeevsky.wishlistservice.model.WishlistItem;
import com.sanjeevsky.wishlistservice.repository.WishlistRepository;
import com.sanjeevsky.wishlistservice.service.impl.WishlistServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WishlistServiceImplTest {

    @Mock
    private WishlistRepository wishlistRepository;

    @Mock
    private CartFeignClient cartFeignClient;

    @InjectMocks
    private WishlistServiceImpl wishlistService;

    private static final String USER_ID = "user@example.com";
    private static final UUID PRODUCT_ID = UUID.randomUUID();

    private WishlistItem existingItem() {
        return WishlistItem.builder()
                .id(UUID.randomUUID())
                .userId(USER_ID)
                .productId(PRODUCT_ID)
                .productName("Running Shoes")
                .salePrice(999.0)
                .build();
    }

    private AddToWishlistRequest addRequest() {
        return AddToWishlistRequest.builder()
                .productId(PRODUCT_ID)
                .productName("Running Shoes")
                .salePrice(999.0)
                .build();
    }

    // ─── addToWishlist ────────────────────────────────────────────────────────

    @Test
    void addToWishlist_newItem_savesAndReturns() {
        when(wishlistRepository.findByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(Optional.empty());
        when(wishlistRepository.save(any(WishlistItem.class))).thenAnswer(inv -> inv.getArgument(0));

        WishlistItem result = wishlistService.addToWishlist(USER_ID, addRequest());

        assertThat(result.getUserId()).isEqualTo(USER_ID);
        assertThat(result.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(result.getProductName()).isEqualTo("Running Shoes");
        assertThat(result.getSalePrice()).isEqualTo(999.0);
        verify(wishlistRepository).save(any(WishlistItem.class));
    }

    @Test
    void addToWishlist_duplicate_throwsAlreadyExistsException() {
        when(wishlistRepository.findByUserIdAndProductId(USER_ID, PRODUCT_ID))
                .thenReturn(Optional.of(existingItem()));

        assertThatThrownBy(() -> wishlistService.addToWishlist(USER_ID, addRequest()))
                .isInstanceOf(WishlistItemAlreadyExistsException.class);

        verify(wishlistRepository, never()).save(any());
    }

    // ─── getWishlist ──────────────────────────────────────────────────────────

    @Test
    void getWishlist_returnsItemsForUser() {
        List<WishlistItem> items = List.of(existingItem(), existingItem());
        when(wishlistRepository.findByUserId(USER_ID)).thenReturn(items);

        List<WishlistItem> result = wishlistService.getWishlist(USER_ID);

        assertThat(result).hasSize(2);
        verify(wishlistRepository).findByUserId(USER_ID);
    }

    @Test
    void getWishlist_emptyWishlist_returnsEmptyList() {
        when(wishlistRepository.findByUserId(USER_ID)).thenReturn(List.of());

        List<WishlistItem> result = wishlistService.getWishlist(USER_ID);

        assertThat(result).isEmpty();
    }

    // ─── removeFromWishlist ───────────────────────────────────────────────────

    @Test
    void removeFromWishlist_existingItem_deletesIt() {
        when(wishlistRepository.findByUserIdAndProductId(USER_ID, PRODUCT_ID))
                .thenReturn(Optional.of(existingItem()));

        wishlistService.removeFromWishlist(USER_ID, PRODUCT_ID);

        verify(wishlistRepository).deleteByUserIdAndProductId(USER_ID, PRODUCT_ID);
    }

    @Test
    void removeFromWishlist_notFound_throwsNotFoundException() {
        when(wishlistRepository.findByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> wishlistService.removeFromWishlist(USER_ID, PRODUCT_ID))
                .isInstanceOf(WishlistItemNotFoundException.class);

        verify(wishlistRepository, never()).deleteByUserIdAndProductId(any(), any());
    }

    // ─── moveToCart ───────────────────────────────────────────────────────────

    @Test
    void moveToCart_returnsItemAndRemovesItFromWishlist() {
        WishlistItem item = existingItem();
        when(wishlistRepository.findByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(Optional.of(item));
        doNothing().when(cartFeignClient).addItem(any(), any(Map.class));

        WishlistItem result = wishlistService.moveToCart(USER_ID, PRODUCT_ID);

        assertThat(result).isSameAs(item);
        verify(cartFeignClient).addItem(eq(USER_ID), any(Map.class));
        verify(wishlistRepository).deleteByUserIdAndProductId(USER_ID, PRODUCT_ID);
    }

    @Test
    void moveToCart_notFound_throwsNotFoundException() {
        when(wishlistRepository.findByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> wishlistService.moveToCart(USER_ID, PRODUCT_ID))
                .isInstanceOf(WishlistItemNotFoundException.class);
    }
}
