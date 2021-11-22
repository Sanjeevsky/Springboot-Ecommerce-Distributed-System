package com.sanjeevsky.authserver.service;

import com.sanjeevsky.authserver.exceptions.CredentialsMismatchException;
import com.sanjeevsky.authserver.exceptions.NoSuchUserExistsException;
import com.sanjeevsky.authserver.exceptions.UserAlreadyExistsException;
import com.sanjeevsky.authserver.jwtgenerator.JwtTokenGenerator;
import com.sanjeevsky.authserver.modal.LoginDTO;
import com.sanjeevsky.authserver.modal.User;
import com.sanjeevsky.authserver.modal.UserDTO;
import com.sanjeevsky.authserver.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

@Service
public class UserServiceImp implements UserService {
    @Autowired
    private UserRepository repository;
    @Autowired
    private JwtTokenGenerator generator;

    @Override
    public User registerUser(User user) throws UserAlreadyExistsException {
        //check if user already registerd
        if(repository.findById(user.getEmail()).isPresent()){
            throw new UserAlreadyExistsException("User Already Exists with this Email...!!");
        }
        //password encoding
        user.setPassword(encodeStringPassword(user.getPassword()));
        return repository.save(user);
    }

    @Override
    public LoginDTO authenticateUser(UserDTO user) throws NoSuchUserExistsException, CredentialsMismatchException {
        Optional<User> user1 = repository.findById(user.getEmail());
        if(user1.isPresent()){
            //password validation
            if(decodeStringPassword(user1.get().getPassword()).equals(user.getPassword())){
                return generator.generateToken(user);
            }else{
                throw new CredentialsMismatchException("Credentials Mismatch..Please try with valid credentials");
            }

        }else{
            throw new NoSuchUserExistsException("No User Found With Given Credentials...!!");
        }
    }

    @Override
    public String updatePassword(User user) {
        return null;
    }

    public static String encodeStringPassword(String password){
        return Base64.getEncoder().encodeToString(password.getBytes(StandardCharsets.UTF_8));
    }

    public static String decodeStringPassword(String encode){
        return new String(Base64.getMimeDecoder().decode(encode));
    }
}
