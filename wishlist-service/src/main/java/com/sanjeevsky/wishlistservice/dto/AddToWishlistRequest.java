package com.sanjeevsky.wishlistservice.dto;

import lombok.*;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddToWishlistRequest {

    @NotNull(message = "productId must not be null")
    private UUID productId;

    @NotBlank(message = "productName is required")
    private String productName;

    @Positive(message = "salePrice must be greater than zero")
    private double salePrice;
}
