package com.sanjeevsky.wishlistservice.dto;

import lombok.*;

import javax.validation.constraints.NotNull;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddToWishlistRequest {

    @NotNull(message = "productId must not be null")
    private UUID productId;

    private String productName;

    private double salePrice;
}
