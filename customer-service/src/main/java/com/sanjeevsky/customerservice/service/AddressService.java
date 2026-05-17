package com.sanjeevsky.customerservice.service;

import com.sanjeevsky.customerservice.model.Address;

import java.util.List;
import java.util.UUID;

public interface AddressService {

    Address addAddress(Address address, String user);

    Address getAddress(UUID uuid, String user);

    List<Address> getAddresses(String user);

    Address updateAddress(UUID id, Address address, String user);

    void deleteAddress(UUID id, String user);
}
