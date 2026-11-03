package com.cloudforge.common.error;

/**
 * Credentials or a refresh token were rejected. The message must stay generic (no "unknown user"
 * versus "wrong password" distinction) so the API cannot be used to discover accounts.
 */
public class AuthenticationFailedException extends CloudForgeException {

    public AuthenticationFailedException(String code, String message) {
        super(code, message);
    }
}
