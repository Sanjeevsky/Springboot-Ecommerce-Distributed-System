package com.sanjeevsky.authserver.service;

import com.sanjeevsky.authserver.exceptions.CredentialsMismatchException;
import com.sanjeevsky.authserver.exceptions.NoSuchUserExistsException;
import com.sanjeevsky.authserver.exceptions.UserAlreadyExistsException;
import com.sanjeevsky.authserver.modal.LoginDTO;
import com.sanjeevsky.authserver.modal.UpdatePasswordRequest;
import com.sanjeevsky.authserver.modal.User;
import com.sanjeevsky.authserver.modal.UserDTO;

public interface UserService {

    User registerUser(User user);
    LoginDTO authenticateUser(UserDTO user);
    String updatePassword(UpdatePasswordRequest request);
}
