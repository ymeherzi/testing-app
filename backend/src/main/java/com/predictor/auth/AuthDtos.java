package com.predictor.auth;

import com.predictor.user.User;
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
            @NotBlank String password) {
    }

    public record UserResponse(java.util.UUID id, String email, String displayName, String country,
                               Long favouriteClubTeamId, boolean admin) {

        public static UserResponse from(User user) {
            return new UserResponse(user.getPublicId(), user.getEmail(), user.getDisplayName(),
                    user.getCountry(), user.getFavouriteClubTeamId(), user.isAdmin());
        }
    }

    public record AuthResponse(String token, UserResponse user) {
    }
}
