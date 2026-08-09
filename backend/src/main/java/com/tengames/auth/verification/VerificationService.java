package com.tengames.auth.verification;

import com.tengames.auth.email.EmailSender;
import com.tengames.common.ratelimit.RateLimiter;
import com.tengames.user.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Six-digit email codes and the devices they unlock. Codes are stored
 * hashed, expire in 15 minutes and die after 5 wrong attempts; device
 * tokens are opaque random strings kept as SHA-256 digests.
 */
@Service
public class VerificationService {

    private static final Duration CODE_TTL = Duration.ofMinutes(15);
    private static final Duration DEVICE_TTL = Duration.ofDays(60);
    /** Enough for a genuine retry or two; far short of using us to mail strangers. */
    private static final int CODES_PER_ADDRESS = 5;
    private static final Duration CODE_WINDOW = Duration.ofHours(1);

    private final AuthCodeRepository codes;
    private final TrustedDeviceRepository devices;
    private final EmailSender emailSender;
    private final PasswordEncoder passwordEncoder;
    private final RateLimiter rateLimiter;
    private final Clock clock;
    private final org.springframework.transaction.support.TransactionTemplate attemptTx;
    private final SecureRandom random = new SecureRandom();

    public VerificationService(AuthCodeRepository codes, TrustedDeviceRepository devices,
                               EmailSender emailSender, PasswordEncoder passwordEncoder,
                               RateLimiter rateLimiter,
                               org.springframework.transaction.PlatformTransactionManager txManager, Clock clock) {
        this.codes = codes;
        this.devices = devices;
        this.emailSender = emailSender;
        this.passwordEncoder = passwordEncoder;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
        this.attemptTx = new org.springframework.transaction.support.TransactionTemplate(txManager);
        this.attemptTx.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional
    public void sendCode(User user, AuthCode.Purpose purpose) {
        // Guarding here rather than per endpoint covers every path that can
        // ever send a code, so nobody can point the app at a stranger's inbox
        // — and it protects the provider's free daily quota with it.
        rateLimiter.check("code:" + user.getEmail().toLowerCase(Locale.ROOT),
                CODES_PER_ADDRESS, CODE_WINDOW,
                "Too many codes requested for this address — try again later");
        String code = "%06d".formatted(random.nextInt(1_000_000));
        Instant now = clock.instant();
        codes.save(new AuthCode(user.getId(), purpose, passwordEncoder.encode(code), now.plus(CODE_TTL)));
        String subject = purpose == AuthCode.Purpose.VERIFY_EMAIL
                ? "Your Ten Games confirmation code"
                : "New sign-in to Ten Games";
        emailSender.send(user.getEmail(), subject, """
                Hi %s,

                Your code is %s

                It expires in 15 minutes. If this wasn't you, ignore this email.""".formatted(
                user.getDisplayName(), code));
    }

    /** Throws 400/410 when the code is wrong or unusable; returns quietly on success. */
    @Transactional
    public void consumeCode(User user, AuthCode.Purpose purpose, String submitted) {
        Instant now = clock.instant();
        AuthCode code = codes.findFirstByUserIdAndPurposeOrderByIdDesc(user.getId(), purpose)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request a code first"));
        if (!code.isUsable(now)) {
            throw new ResponseStatusException(HttpStatus.GONE, "That code has expired — request a new one");
        }
        // Committed on its own: rejecting the code rolls back the caller's
        // transaction, and a failed guess that unwinds with it would leave
        // the attempt limit permanently at zero — i.e. unlimited guessing.
        recordAttempt(code.getId());
        if (!passwordEncoder.matches(submitted.trim(), code.getCodeHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That code is not right");
        }
        code.consume(now);
    }

    private void recordAttempt(Long codeId) {
        attemptTx.executeWithoutResult(status -> codes.findById(codeId).ifPresent(AuthCode::recordAttempt));
    }

    /** True when the caller's device token is known, unexpired and theirs. */
    @Transactional
    public boolean isTrustedDevice(User user, String deviceToken) {
        if (deviceToken == null || deviceToken.isBlank()) {
            return false;
        }
        Instant now = clock.instant();
        return devices.findByTokenHash(hash(deviceToken))
                .filter(device -> device.getUserId().equals(user.getId()))
                .filter(device -> now.isBefore(device.getExpiresAt()))
                .map(device -> {
                    device.touch(now);
                    return true;
                })
                .orElse(false);
    }

    /** Issues a new device token the client should keep. */
    @Transactional
    public String rememberDevice(User user, String label) {
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String token = HexFormat.of().formatHex(raw);
        Instant now = clock.instant();
        devices.save(new TrustedDevice(user.getId(), hash(token),
                label == null || label.isBlank() ? null : label.substring(0, Math.min(label.length(), 120)),
                now, now.plus(DEVICE_TTL)));
        return token;
    }

    private static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
