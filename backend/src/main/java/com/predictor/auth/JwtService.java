package com.predictor.auth;

import com.predictor.user.User;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final Clock clock;

    public JwtService(JwtProperties properties, Clock clock) {
        this.encoder = new NimbusJwtEncoder(
                new ImmutableSecret<>(new SecretKeySpec(properties.secret().getBytes(), "HmacSHA256")));
        this.properties = properties;
        this.clock = clock;
    }

    public String issueToken(User user) {
        Instant now = clock.instant();
        List<String> roles = user.isAdmin() ? List.of("USER", "ADMIN") : List.of("USER");
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(now.plus(properties.ttl()))
                .claim("email", user.getEmail())
                .claim("roles", roles)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
