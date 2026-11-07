package com.opspilot.auth;

import com.opspilot.auth.dto.LoginRequest;
import com.opspilot.auth.dto.LoginResponse;
import com.opspilot.auth.dto.RegisterRequest;
import com.opspilot.auth.ratelimit.AuthAction;
import com.opspilot.auth.ratelimit.AuthRateLimiter;
import com.opspilot.user.dto.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final AuthRateLimiter authRateLimiter;

    public AuthController(AuthService authService, AuthRateLimiter authRateLimiter) {
        this.authService = authService;
        this.authRateLimiter = authRateLimiter;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request,
                                                  HttpServletRequest httpRequest) {
        authRateLimiter.check(AuthAction.REGISTER, request.email(), httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                                HttpServletRequest httpRequest) {
        authRateLimiter.check(AuthAction.LOGIN, request.email(), httpRequest);
        return ResponseEntity.ok(authService.login(request));
    }
}
