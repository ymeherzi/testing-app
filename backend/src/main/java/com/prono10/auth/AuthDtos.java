package com.prono10.auth;

import com.prono10.user.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record SignupRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(min = 2, max = 50) String displayName,
            @Pattern(regexp = "[A-Z]{2}", message = "must be an ISO 3166-1 alpha-2 code") String country,
            Long favouriteClubTeamId) {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password,
            String deviceToken) {
    }

    public record VerifyRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 6, max = 6) String code,
            boolean rememberDevice) {
    }

    public record ResendRequest(@NotBlank @Email String email) {
    }

    public record GoogleRequest(@NotBlank String idToken) {
    }

    public record UserResponse(java.util.UUID id, String email, String displayName, String country,
                               Long favouriteClubTeamId, boolean admin) {

        public static UserResponse from(User user) {
            return new UserResponse(user.getPublicId(), user.getEmail(), user.getDisplayName(),
                    user.getCountry(), user.getFavouriteClubTeamId(), user.isAdmin());
        }
    }

    /**
     * Either the caller is in (token present) or a six-digit code is waiting
     * in their inbox (verificationRequired).
     */
    public record AuthResponse(String token, UserResponse user,
                               boolean verificationRequired, String email, String deviceToken) {

        public static AuthResponse signedIn(String token, User user, String deviceToken) {
            return new AuthResponse(token, UserResponse.from(user), false, user.getEmail(), deviceToken);
        }

        public static AuthResponse codeSent(String email) {
            return new AuthResponse(null, null, true, email, null);
        }
    }
}
