package com.tengames.auth;

import com.tengames.auth.AuthDtos.AuthResponse;
import com.tengames.auth.AuthDtos.ForgotPasswordRequest;
import com.tengames.auth.AuthDtos.GoogleRequest;
import com.tengames.auth.AuthDtos.LoginRequest;
import com.tengames.auth.AuthDtos.ResendRequest;
import com.tengames.auth.AuthDtos.ResetPasswordRequest;
import com.tengames.auth.AuthDtos.SignupRequest;
import com.tengames.auth.AuthDtos.VerifyRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final GoogleAuthService googleAuthService;

    public AuthController(AuthService authService, GoogleAuthService googleAuthService) {
        this.authService = authService;
        this.googleAuthService = googleAuthService;
    }

    @PostMapping("/signup")
    public AuthResponse signup(@Valid @RequestBody SignupRequest request) {
        return authService.signup(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        return authService.login(request, deviceLabel(servletRequest));
    }

    @PostMapping("/verify")
    public AuthResponse verify(@Valid @RequestBody VerifyRequest request, HttpServletRequest servletRequest) {
        return authService.verify(request, deviceLabel(servletRequest));
    }

    @PostMapping("/resend")
    public AuthResponse resend(@Valid @RequestBody ResendRequest request) {
        return authService.resend(request.email());
    }

    @PostMapping("/forgot")
    public AuthResponse forgot(@Valid @RequestBody ForgotPasswordRequest request) {
        return authService.forgotPassword(request.email());
    }

    @PostMapping("/reset")
    public AuthResponse reset(@Valid @RequestBody ResetPasswordRequest request, HttpServletRequest servletRequest) {
        return authService.resetPassword(request.email(), request.code(), request.password(),
                deviceLabel(servletRequest));
    }

    @PostMapping("/google")
    public AuthResponse google(@Valid @RequestBody GoogleRequest request, HttpServletRequest servletRequest) {
        return googleAuthService.signIn(request.idToken(), deviceLabel(servletRequest));
    }

    /** Tells the client which sign-in options this deployment offers. */
    @GetMapping("/options")
    public Map<String, Object> options() {
        return googleAuthService.isEnabled()
                ? Map.of("google", true, "clientId", googleAuthService.clientId())
                : Map.of("google", false);
    }

    private static String deviceLabel(HttpServletRequest request) {
        return request.getHeader("User-Agent");
    }
}
