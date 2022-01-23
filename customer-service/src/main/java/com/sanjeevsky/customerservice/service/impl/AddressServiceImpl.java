package com.sanjeevsky.customerservice.service.impl;

import com.sanjeevsky.customerservice.exceptions.AddressDoesnotExistsException;
import com.sanjeevsky.customerservice.exceptions.MandatoryFieldException;
import com.sanjeevsky.customerservice.exceptions.NoAddressExistsException;
import com.sanjeevsky.customerservice.model.Address;
import com.sanjeevsky.customerservice.repository.AddressRepository;
import com.sanjeevsky.customerservice.service.AddressService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.sanjeevsky.customerservice.utils.ErrorConstants.*;

@Service
public class AddressServiceImpl implements AddressService {
    @Autowired
    AddressRepository repository;
    @Override
    public Address addAddress(Address address, String user) {
        if(address.getCity().isEmpty()) throw new MandatoryFieldException(CITY_FIELD_CAN_T_BE_EMPTY);
        if(address.getState().isEmpty()) throw new MandatoryFieldException(STATE_FIELD_CAN_T_BE_EMPTY);
        if(address.getCountry().isEmpty()) throw new MandatoryFieldException(COUNTRY_FIELD_CAN_T_BE_EMPTY);
        if(address.getZipCode()<6) throw new MandatoryFieldException(PLEASE_PROVIDE_PROPER_ZIP_CODE_FOR_ADDRESS_PROCESSING);
        if(address.getHome().isEmpty()) throw new MandatoryFieldException(HOUSE_NUMBER_FIELD_CAN_T_BE_EMPTY);
        if(address.getStreetLocality().isEmpty()) throw new MandatoryFieldException(STREET_FIELD_CAN_T_BE_EMPTY);
        //add user to address
        address.setUser(user);
        return repository.save(address);
    }

    @Override
    public Address getAddress(UUID uuid, String user) {
        Optional<Address> address = repository.findByIdAndUser(uuid,user);
        if (address.isEmpty()) throw new AddressDoesnotExistsException(ADDRESS_WITH_GIVEN_UUID_DOES_NOT_EXIST);
        return address.get();
    }

    @Override
    public List<Address> getAddresses(String user) {
        List<Address> all = repository.findAllByUser(user);
        if (all.isEmpty()) throw new NoAddressExistsException(NO_ADDRESS_FOUND);
        return all;
    }

}
