package com.prono10.auth;

import com.prono10.auth.AuthDtos.AuthResponse;
import com.prono10.auth.verification.VerificationService;
import com.prono10.user.User;
import com.prono10.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * "Continue with Google": the browser obtains an ID token from Google and
 * posts it here. We verify the signature against Google's public keys and
 * that it was minted for our client, then trust the email it carries — so
 * Google-signed-in accounts skip the code flow entirely.
 */
@Service
public class GoogleAuthService {

    private static final String JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";

    private final UserRepository users;
    private final JwtService jwtService;
    private final VerificationService verification;
    private final String clientId;
    private volatile JwtDecoder decoder;

    public GoogleAuthService(UserRepository users, JwtService jwtService, VerificationService verification,
                             @Value("${app.google.client-id:}") String clientId) {
        this.users = users;
        this.jwtService = jwtService;
        this.verification = verification;
        this.clientId = clientId;
    }

    public String clientId() {
        return clientId;
    }

    public boolean isEnabled() {
        return !clientId.isBlank();
    }

    @Transactional
    public AuthResponse signIn(String idToken, String deviceLabel) {
        if (!isEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Google sign-in is not configured");
        }
        Jwt jwt;
        try {
            jwt = decoder().decode(idToken);
        } catch (JwtException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google sign-in could not be verified");
        }
        if (!jwt.getAudience().contains(clientId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "This Google token is for another app");
        }
        if (!Boolean.TRUE.equals(jwt.getClaim("email_verified"))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "This Google account has no verified email");
        }
        String subject = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        String name = jwt.getClaimAsString("name");

        User user = users.findByGoogleSubject(subject)
                .or(() -> users.findByEmailIgnoreCase(email))
                .orElseGet(() -> users.save(new User(email, null,
                        name == null || name.isBlank() ? email.split("@")[0] : name, null, null)));
        user.linkGoogle(subject);
        // Google already proved the address, so the device is trusted straight away.
        String deviceToken = verification.rememberDevice(user, deviceLabel);
        return AuthResponse.signedIn(jwtService.issueToken(user), user, deviceToken);
    }

    private JwtDecoder decoder() {
        JwtDecoder local = decoder;
        if (local == null) {
            synchronized (this) {
                local = decoder;
                if (local == null) {
                    local = NimbusJwtDecoder.withJwkSetUri(JWK_SET_URI).build();
                    decoder = local;
                }
            }
        }
        return local;
    }
}
