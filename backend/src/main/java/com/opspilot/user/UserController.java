package com.opspilot.user;

import com.opspilot.user.dto.UserResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserRepository userRepository;
    public UserController(UserRepository userRepository) { this.userRepository = userRepository; }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> currentUser(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return userRepository.findById(userId)
                .map(user -> ResponseEntity.ok(UserResponse.from(user)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
