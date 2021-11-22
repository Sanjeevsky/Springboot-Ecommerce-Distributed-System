package com.sanjeevsky.authserver.service;

import com.sanjeevsky.authserver.exceptions.CredentialsMismatchException;
import com.sanjeevsky.authserver.exceptions.NoSuchUserExistsException;
import com.sanjeevsky.authserver.exceptions.UserAlreadyExistsException;
import com.sanjeevsky.authserver.modal.LoginDTO;
import com.sanjeevsky.authserver.modal.User;
import com.sanjeevsky.authserver.modal.UserDTO;

public interface UserService {

    User registerUser(User user) throws UserAlreadyExistsException;
    LoginDTO authenticateUser(UserDTO user) throws NoSuchUserExistsException, CredentialsMismatchException;
    String updatePassword(User user);
}
