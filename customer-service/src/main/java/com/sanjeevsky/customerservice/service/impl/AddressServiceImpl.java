package com.sanjeevsky.customerservice.service.impl;

import com.sanjeevsky.customerservice.exceptions.AddressDoesnotExistsException;
import com.sanjeevsky.customerservice.exceptions.InvalidRequestException;
import com.sanjeevsky.customerservice.exceptions.MandatoryFieldException;
import com.sanjeevsky.customerservice.exceptions.NoAddressExistsException;
import com.sanjeevsky.customerservice.model.Address;
import com.sanjeevsky.customerservice.repository.AddressRepository;
import com.sanjeevsky.customerservice.service.AddressService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.sanjeevsky.customerservice.utils.ErrorConstants.ADDRESS_WITH_GIVEN_UUID_DOES_NOT_EXIST;
import static com.sanjeevsky.customerservice.utils.ErrorConstants.CITY_FIELD_CAN_T_BE_EMPTY;
import static com.sanjeevsky.customerservice.utils.ErrorConstants.COUNTRY_FIELD_CAN_T_BE_EMPTY;
import static com.sanjeevsky.customerservice.utils.ErrorConstants.HOUSE_NUMBER_FIELD_CAN_T_BE_EMPTY;
import static com.sanjeevsky.customerservice.utils.ErrorConstants.NO_ADDRESS_FOUND;
import static com.sanjeevsky.customerservice.utils.ErrorConstants.PLEASE_PROVIDE_PROPER_ZIP_CODE_FOR_ADDRESS_PROCESSING;
import static com.sanjeevsky.customerservice.utils.ErrorConstants.STATE_FIELD_CAN_T_BE_EMPTY;
import static com.sanjeevsky.customerservice.utils.ErrorConstants.STREET_FIELD_CAN_T_BE_EMPTY;

@Service
public class AddressServiceImpl implements AddressService {

    private final AddressRepository repository;

    public AddressServiceImpl(AddressRepository repository) {
        this.repository = repository;
    }

    @Override
    public Address addAddress(Address address, String user) {
        String normalizedUser = validateUser(user);
        validateAddressRequest(address);
        validateZipCode(address.getZipCode());
        address.setCity(requiredField(address.getCity(), CITY_FIELD_CAN_T_BE_EMPTY));
        address.setState(requiredField(address.getState(), STATE_FIELD_CAN_T_BE_EMPTY));
        address.setCountry(requiredField(address.getCountry(), COUNTRY_FIELD_CAN_T_BE_EMPTY));
        address.setHome(requiredField(address.getHome(), HOUSE_NUMBER_FIELD_CAN_T_BE_EMPTY));
        address.setStreetLocality(requiredField(address.getStreetLocality(), STREET_FIELD_CAN_T_BE_EMPTY));
        address.setLandmark(optionalField(address.getLandmark()));
        address.setUser(normalizedUser);
        return repository.save(address);
    }

    @Override
    public Address getAddress(UUID uuid, String user) {
        validateAddressId(uuid);
        String normalizedUser = validateUser(user);
        Optional<Address> address = repository.findByIdAndUser(uuid, normalizedUser);
        if (address.isEmpty()) throw new AddressDoesnotExistsException(ADDRESS_WITH_GIVEN_UUID_DOES_NOT_EXIST);
        return address.get();
    }

    @Override
    public List<Address> getAddresses(String user) {
        String normalizedUser = validateUser(user);
        List<Address> all = repository.findAllByUser(normalizedUser);
        if (all.isEmpty()) throw new NoAddressExistsException(NO_ADDRESS_FOUND);
        return all;
    }

    @Override
    public Address updateAddress(UUID id, Address updated, String user) {
        validateAddressId(id);
        validateAddressRequest(updated);
        String normalizedUser = validateUser(user);
        Address found = repository.findByIdAndUser(id, normalizedUser)
                .orElseThrow(() -> new AddressDoesnotExistsException(ADDRESS_WITH_GIVEN_UUID_DOES_NOT_EXIST));
        if (updated.getCity() != null) found.setCity(requiredField(updated.getCity(), CITY_FIELD_CAN_T_BE_EMPTY));
        if (updated.getState() != null) found.setState(requiredField(updated.getState(), STATE_FIELD_CAN_T_BE_EMPTY));
        if (updated.getCountry() != null) found.setCountry(requiredField(updated.getCountry(), COUNTRY_FIELD_CAN_T_BE_EMPTY));
        if (updated.getZipCode() != 0) {
            validateZipCode(updated.getZipCode());
            found.setZipCode(updated.getZipCode());
        }
        if (updated.getHome() != null) found.setHome(requiredField(updated.getHome(), HOUSE_NUMBER_FIELD_CAN_T_BE_EMPTY));
        if (updated.getStreetLocality() != null) found.setStreetLocality(requiredField(updated.getStreetLocality(), STREET_FIELD_CAN_T_BE_EMPTY));
        if (updated.getLandmark() != null) found.setLandmark(optionalField(updated.getLandmark()));
        return repository.save(found);
    }

    @Override
    public void deleteAddress(UUID id, String user) {
        validateAddressId(id);
        String normalizedUser = validateUser(user);
        Address found = repository.findByIdAndUser(id, normalizedUser)
                .orElseThrow(() -> new AddressDoesnotExistsException(ADDRESS_WITH_GIVEN_UUID_DOES_NOT_EXIST));
        repository.delete(found);
    }

    private void validateAddressRequest(Address address) {
        if (address == null) {
            throw new InvalidRequestException("Address request is required");
        }
    }

    private void validateZipCode(int zipCode) {
        if (zipCode < 6) {
            throw new MandatoryFieldException(PLEASE_PROVIDE_PROPER_ZIP_CODE_FOR_ADDRESS_PROCESSING);
        }
    }

    private String validateUser(String user) {
        String normalized = optionalField(user);
        if (normalized == null) {
            throw new InvalidRequestException("Address user is required");
        }
        return normalized;
    }

    private void validateAddressId(UUID id) {
        if (id == null) {
            throw new InvalidRequestException("Address id is required");
        }
    }

    private String requiredField(String value, String message) {
        String normalized = optionalField(value);
        if (normalized == null) {
            throw new MandatoryFieldException(message);
        }
        return normalized;
    }

    private String optionalField(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
