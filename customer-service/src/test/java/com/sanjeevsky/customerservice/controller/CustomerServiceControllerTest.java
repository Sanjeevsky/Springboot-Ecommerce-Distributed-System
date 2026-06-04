package com.sanjeevsky.customerservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanjeevsky.customerservice.exceptions.GlobalExceptionHandler;
import com.sanjeevsky.customerservice.model.Address;
import com.sanjeevsky.customerservice.service.AddressService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CustomerServiceControllerTest {

    private static final String USER = "buyer@example.com";
    private static final UUID ADDRESS_ID = UUID.fromString("3b012a5e-32ff-452e-bcc2-deb30dbae60b");

    @Mock
    private AddressService addressService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CustomerServiceController(addressService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void addAddress_validRequest_returns201AndPassesUserHeader() throws Exception {
        Address saved = address();
        when(addressService.addAddress(any(Address.class), eq(USER))).thenReturn(saved);

        mockMvc.perform(post("/customer-service/address")
                        .header("X-User", USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addressWithoutId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(ADDRESS_ID.toString()))
                .andExpect(jsonPath("$.data.user").value(USER));

        ArgumentCaptor<Address> captor = ArgumentCaptor.forClass(Address.class);
        verify(addressService).addAddress(captor.capture(), eq(USER));
        assertThat(captor.getValue().getCity()).isEqualTo("Bangalore");
    }

    @Test
    void addAddress_invalidBody_returns400BeforeServiceCall() throws Exception {
        mockMvc.perform(post("/customer-service/address")
                        .header("X-User", USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("city")));

        verifyNoInteractions(addressService);
    }

    @Test
    void getAddress_returnsAddressForUser() throws Exception {
        when(addressService.getAddress(ADDRESS_ID, USER)).thenReturn(address());

        mockMvc.perform(get("/customer-service/address/{id}", ADDRESS_ID)
                        .header("X-User", USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(ADDRESS_ID.toString()))
                .andExpect(jsonPath("$.data.city").value("Bangalore"));

        verify(addressService).getAddress(ADDRESS_ID, USER);
    }

    @Test
    void getAddresses_returnsUserAddressList() throws Exception {
        when(addressService.getAddresses(USER)).thenReturn(List.of(address()));

        mockMvc.perform(get("/customer-service/addresses")
                        .header("X-User", USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(ADDRESS_ID.toString()));

        verify(addressService).getAddresses(USER);
    }

    @Test
    void updateAddress_validPatch_returnsUpdatedAddressAndPassesUserHeader() throws Exception {
        Address updated = address();
        updated.setStreetLocality("Updated Street");
        when(addressService.updateAddress(eq(ADDRESS_ID), any(Address.class), eq(USER))).thenReturn(updated);

        Address patch = new Address();
        patch.setStreetLocality("Updated Street");

        mockMvc.perform(put("/customer-service/address/{id}", ADDRESS_ID)
                        .header("X-User", USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(patch)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.streetLocality").value("Updated Street"));

        ArgumentCaptor<Address> captor = ArgumentCaptor.forClass(Address.class);
        verify(addressService).updateAddress(eq(ADDRESS_ID), captor.capture(), eq(USER));
        assertThat(captor.getValue().getStreetLocality()).isEqualTo("Updated Street");
    }

    @Test
    void deleteAddress_returns204AndPassesUserHeader() throws Exception {
        mockMvc.perform(delete("/customer-service/address/{id}", ADDRESS_ID)
                        .header("X-User", USER))
                .andExpect(status().isNoContent());

        verify(addressService).deleteAddress(ADDRESS_ID, USER);
    }

    private Address address() {
        Address address = addressWithoutId();
        address.setId(ADDRESS_ID);
        address.setUser(USER);
        return address;
    }

    private Address addressWithoutId() {
        Address address = new Address();
        address.setCity("Bangalore");
        address.setState("Karnataka");
        address.setCountry("India");
        address.setZipCode(560001);
        address.setHome("42");
        address.setStreetLocality("MG Road");
        address.setLandmark("Metro");
        return address;
    }
}
