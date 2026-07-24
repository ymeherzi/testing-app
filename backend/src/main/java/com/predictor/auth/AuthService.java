package com.predictor.auth;

import com.predictor.auth.AuthDtos.AuthResponse;
import com.predictor.auth.AuthDtos.LoginRequest;
import com.predictor.auth.AuthDtos.SignupRequest;
import com.predictor.auth.AuthDtos.VerifyRequest;
import com.predictor.auth.verification.AuthCode;
import com.predictor.auth.verification.VerificationService;
import com.predictor.user.User;
import com.predictor.user.UserRepository;
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

    /** Creates the account and emails a code; no session until it's confirmed. */
    @Transactional
    public AuthResponse signup(SignupRequest request) {
        if (users.existsByEmailIgnoreCase(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        User user = new User(request.email(), passwordEncoder.encode(request.password()),
                request.displayName(), request.country(), request.favouriteClubTeamId());
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
