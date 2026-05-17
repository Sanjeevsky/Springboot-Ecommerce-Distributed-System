package com.sanjeevsky.authserver.modal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePasswordRequest {
    private String email;
    private String oldPassword;
    private String newPassword;
}
