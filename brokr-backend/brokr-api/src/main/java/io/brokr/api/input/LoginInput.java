package io.brokr.api.input;

import lombok.Data;

@Data
public class LoginInput {
    private String username;
    private String password;
}