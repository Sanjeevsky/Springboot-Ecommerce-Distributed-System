package com.sanjeevsky.customerservice.repository;

import com.sanjeevsky.customerservice.model.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
@Repository
public interface AddressRepository extends JpaRepository<Address, UUID> {
    Optional<Address> findByIdAndUser(UUID uuid,String user);
    List<Address> findAllByUser(String user);
}
