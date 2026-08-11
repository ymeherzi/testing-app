package com.tengames.auth;

import com.tengames.auth.AuthDtos.AuthResponse;
import com.tengames.auth.AuthDtos.LoginRequest;
import com.tengames.auth.AuthDtos.SignupRequest;
import com.tengames.auth.AuthDtos.VerifyRequest;
import com.tengames.auth.verification.AuthCode;
import com.tengames.auth.verification.VerificationService;
import com.tengames.user.User;
import com.tengames.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final VerificationService verification;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService,
                       VerificationService verification) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.verification = verification;
    }

    /**
     * Creates the account and emails a code; no session until it's confirmed.
     *
     * <p>A delivery failure rolls the account back with the transaction, on
     * purpose: an account nobody can verify only blocks the address from
     * being used again.
     */
    @Transactional
    public AuthResponse signup(SignupRequest request) {
        if (users.existsByEmailIgnoreCase(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        User user = new User(request.email(), passwordEncoder.encode(request.password()),
                request.displayName(), request.country(), request.favouriteClubTeamId());
        // signing up is the one moment a player fills these in willingly, and
        // both put them in a league without another thought
        user.setFavouriteCompetitionId(request.favouriteCompetitionId());
        users.save(user);
        verification.sendCode(user, AuthCode.Purpose.VERIFY_EMAIL);
        return AuthResponse.codeSent(user.getEmail());
    }

    /**
     * Password check first, then: unverified addresses and unrecognized
     * devices both need a code before a session is issued.
     */
    @Transactional
    public AuthResponse login(LoginRequest request, String deviceLabel) {
        User user = users.findByEmailIgnoreCase(request.email())
                .filter(u -> u.getPasswordHash() != null)
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        if (!user.isEmailVerified()) {
            verification.sendCode(user, AuthCode.Purpose.VERIFY_EMAIL);
            return AuthResponse.codeSent(user.getEmail());
        }
        if (verification.isTrustedDevice(user, request.deviceToken())) {
            return AuthResponse.signedIn(jwtService.issueToken(user), user, null);
        }
        verification.sendCode(user, AuthCode.Purpose.NEW_DEVICE);
        return AuthResponse.codeSent(user.getEmail());
    }

    /** Confirms a code and, when asked, remembers the device for next time. */
    @Transactional
    public AuthResponse verify(VerifyRequest request, String deviceLabel) {
        User user = users.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No account for this email"));
        AuthCode.Purpose purpose = user.isEmailVerified()
                ? AuthCode.Purpose.NEW_DEVICE
                : AuthCode.Purpose.VERIFY_EMAIL;
        verification.consumeCode(user, purpose, request.code());
        user.markEmailVerified();
        String deviceToken = request.rememberDevice() ? verification.rememberDevice(user, deviceLabel) : null;
        return AuthResponse.signedIn(jwtService.issueToken(user), user, deviceToken);
    }

    /**
     * Emails a reset code — and answers the same way whether or not the
     * address has an account, so this endpoint can't be used to find out who
     * plays. The per-address rate limit still applies.
     */
    @Transactional
    public AuthResponse forgotPassword(String email) {
        users.findByEmailIgnoreCase(email)
                .ifPresent(user -> verification.sendCode(user, AuthCode.Purpose.RESET_PASSWORD));
        return AuthResponse.codeSent(email);
    }

    /**
     * Sets a new password once the code proves the caller reads that inbox,
     * and signs them in — asking them to type the password they just chose
     * would be ceremony, not security.
     */
    @Transactional
    public AuthResponse resetPassword(String email, String code, String newPassword, String deviceLabel) {
        User user = users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No account for this email"));
        verification.consumeCode(user, AuthCode.Purpose.RESET_PASSWORD, code);
        user.changePassword(passwordEncoder.encode(newPassword));
        // Reaching the inbox is the same proof signup asks for.
        user.markEmailVerified();
        return AuthResponse.signedIn(jwtService.issueToken(user), user,
                verification.rememberDevice(user, deviceLabel));
    }

    /** Re-sends whichever code the account currently needs. */
    @Transactional
    public AuthResponse resend(String email) {
        User user = users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No account for this email"));
        verification.sendCode(user, user.isEmailVerified()
                ? AuthCode.Purpose.NEW_DEVICE
                : AuthCode.Purpose.VERIFY_EMAIL);
        return AuthResponse.codeSent(user.getEmail());
    }
}
