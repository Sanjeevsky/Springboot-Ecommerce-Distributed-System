package com.sanjeevsky.orderservice.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class AddressDto {
    private UUID id;
    private String city;
    private String state;
    private String country;
    private int zipCode;
    private String home;
    private String streetLocality;
    private String landmark;
    private String user;
}
