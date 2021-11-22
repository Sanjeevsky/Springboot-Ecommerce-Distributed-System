package com.sanjeevsky.authserver.repository;

import com.sanjeevsky.authserver.modal.User;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends CrudRepository<User,String> {
}
