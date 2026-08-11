package com.tengames.user;

import com.tengames.auth.AuthDtos.UserResponse;
import com.tengames.common.web.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class UserController {

    private final UserRepository users;

    public UserController(UserRepository users) {
        this.users = users;
    }

    public record UpdateProfileRequest(
            @NotBlank @Size(min = 2, max = 50) String displayName,
            @Pattern(regexp = "[A-Z]{2}", message = "must be an ISO 3166-1 alpha-2 code") String country,
            Long favouriteClubTeamId,
            /** Absent from older clients, which must not silently unsubscribe anyone. */
            Boolean notifyEmail) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public UserResponse me(Authentication authentication) {
        return UserResponse.from(users.findById(CurrentUser.id(authentication)).orElseThrow());
    }

    /**
     * Finishing or skipping the welcome guide — a verb of its own rather than
     * another field on the profile update, which exists to edit a nickname
     * and a club.
     */
    @PostMapping("/guide-seen")
    @Transactional
    public UserResponse guideSeen(Authentication authentication) {
        User user = users.findById(CurrentUser.id(authentication)).orElseThrow();
        user.markGuideSeen();
        return UserResponse.from(user);
    }

    @PutMapping
    @Transactional
    public UserResponse update(Authentication authentication, @Valid @RequestBody UpdateProfileRequest request) {
        User user = users.findById(CurrentUser.id(authentication)).orElseThrow();
        user.setDisplayName(request.displayName());
        user.setCountry(request.country());
        user.setFavouriteClubTeamId(request.favouriteClubTeamId());
        if (request.notifyEmail() != null) {
            user.setNotifyEmail(request.notifyEmail());
        }
        return UserResponse.from(user);
    }
}
