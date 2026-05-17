package com.sanjeevsky.customerservice.service;

import com.sanjeevsky.customerservice.exceptions.AddressDoesnotExistsException;
import com.sanjeevsky.customerservice.exceptions.MandatoryFieldException;
import com.sanjeevsky.customerservice.exceptions.NoAddressExistsException;
import com.sanjeevsky.customerservice.model.Address;
import com.sanjeevsky.customerservice.repository.AddressRepository;
import com.sanjeevsky.customerservice.service.impl.AddressServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AddressServiceImplTest {

    @Mock
    private AddressRepository repository;

    @InjectMocks
    private AddressServiceImpl addressService;

    private static final String USER = "buyer@example.com";
    private static final UUID ADDR_ID = UUID.randomUUID();

    private Address validAddress() {
        Address a = new Address();
        a.setId(ADDR_ID);
        a.setUser(USER);
        a.setCity("Mumbai");
        a.setState("Maharashtra");
        a.setCountry("India");
        a.setZipCode(400001);
        a.setHome("42B");
        a.setStreetLocality("MG Road");
        return a;
    }

    @BeforeEach
    void stubSave() {
        lenient().when(repository.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ─── addAddress ───────────────────────────────────────────────────────────

    @Test
    void addAddress_validAddress_savesAndSetsUser() {
        Address addr = validAddress();
        addr.setUser(null);

        Address result = addressService.addAddress(addr, USER);

        assertThat(result.getUser()).isEqualTo(USER);
        verify(repository).save(addr);
    }

    @Test
    void addAddress_emptyCity_throwsMandatoryFieldException() {
        Address addr = validAddress();
        addr.setCity("");

        assertThatThrownBy(() -> addressService.addAddress(addr, USER))
                .isInstanceOf(MandatoryFieldException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void addAddress_emptyState_throwsMandatoryFieldException() {
        Address addr = validAddress();
        addr.setState("");

        assertThatThrownBy(() -> addressService.addAddress(addr, USER))
                .isInstanceOf(MandatoryFieldException.class);
    }

    @Test
    void addAddress_emptyCountry_throwsMandatoryFieldException() {
        Address addr = validAddress();
        addr.setCountry("");

        assertThatThrownBy(() -> addressService.addAddress(addr, USER))
                .isInstanceOf(MandatoryFieldException.class);
    }

    @Test
    void addAddress_invalidZip_throwsMandatoryFieldException() {
        Address addr = validAddress();
        addr.setZipCode(5);

        assertThatThrownBy(() -> addressService.addAddress(addr, USER))
                .isInstanceOf(MandatoryFieldException.class);
    }

    // ─── getAddress ───────────────────────────────────────────────────────────

    @Test
    void getAddress_exists_returnsAddress() {
        Address addr = validAddress();
        when(repository.findByIdAndUser(ADDR_ID, USER)).thenReturn(Optional.of(addr));

        assertThat(addressService.getAddress(ADDR_ID, USER)).isSameAs(addr);
    }

    @Test
    void getAddress_notFound_throwsAddressDoesnotExistsException() {
        when(repository.findByIdAndUser(ADDR_ID, USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.getAddress(ADDR_ID, USER))
                .isInstanceOf(AddressDoesnotExistsException.class);
    }

    // ─── getAddresses ─────────────────────────────────────────────────────────

    @Test
    void getAddresses_hasAddresses_returnsList() {
        when(repository.findAllByUser(USER)).thenReturn(List.of(validAddress()));

        List<Address> result = addressService.getAddresses(USER);

        assertThat(result).hasSize(1);
    }

    @Test
    void getAddresses_noAddresses_throwsNoAddressExistsException() {
        when(repository.findAllByUser(USER)).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> addressService.getAddresses(USER))
                .isInstanceOf(NoAddressExistsException.class);
    }

    // ─── updateAddress ────────────────────────────────────────────────────────

    @Test
    void updateAddress_found_appliesPartialUpdate() {
        Address existing = validAddress();
        when(repository.findByIdAndUser(ADDR_ID, USER)).thenReturn(Optional.of(existing));

        Address patch = new Address();
        patch.setCity("Pune");

        Address result = addressService.updateAddress(ADDR_ID, patch, USER);

        assertThat(result.getCity()).isEqualTo("Pune");
        assertThat(result.getState()).isEqualTo("Maharashtra");
    }

    @Test
    void updateAddress_notFound_throwsAddressDoesnotExistsException() {
        when(repository.findByIdAndUser(ADDR_ID, USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.updateAddress(ADDR_ID, new Address(), USER))
                .isInstanceOf(AddressDoesnotExistsException.class);
    }

    // ─── deleteAddress ────────────────────────────────────────────────────────

    @Test
    void deleteAddress_found_deletesAddress() {
        Address addr = validAddress();
        when(repository.findByIdAndUser(ADDR_ID, USER)).thenReturn(Optional.of(addr));

        addressService.deleteAddress(ADDR_ID, USER);

        verify(repository).delete(addr);
    }

    @Test
    void deleteAddress_notFound_throwsAddressDoesnotExistsException() {
        when(repository.findByIdAndUser(ADDR_ID, USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.deleteAddress(ADDR_ID, USER))
                .isInstanceOf(AddressDoesnotExistsException.class);
    }
}
