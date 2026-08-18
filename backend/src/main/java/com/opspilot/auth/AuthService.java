package com.opspilot.auth;

import java.util.Locale;

import com.opspilot.auth.dto.LoginRequest;
import com.opspilot.auth.dto.LoginResponse;
import com.opspilot.auth.dto.RegisterRequest;
import com.opspilot.user.User;
import com.opspilot.user.UserRepository;
import com.opspilot.user.dto.UserResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) throw new DuplicateEmailException();
        User user = new User(email, passwordEncoder.encode(request.password()), request.name().trim());
        try {
            return UserResponse.from(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateEmailException();
        }
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmail(email).orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return new LoginResponse(jwtService.generateToken(user), "Bearer", jwtService.getExpirationSeconds(),
                new LoginResponse.UserSummary(user.getId(), user.getEmail(), user.getName()));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
