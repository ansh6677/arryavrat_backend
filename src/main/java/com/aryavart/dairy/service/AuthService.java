package com.aryavart.dairy.service;

import com.aryavart.dairy.dto.AuthResponse;
import com.aryavart.dairy.dto.LoginRequest;
import com.aryavart.dairy.dto.RegisterRequest;
import com.aryavart.dairy.model.User;
import com.aryavart.dairy.repository.UserRepository;
import com.aryavart.dairy.security.JwtUtil;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public AuthResponse register(RegisterRequest req) {
        String phone = req.phone().trim();
        if (userRepository.existsByPhone(phone)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This phone number is already registered. Please login.");
        }
        User user = new User();
        user.setName(req.name().trim());
        user.setPhone(phone);
        user.setEmail(req.email());
        user.setAddress(req.address());
        user.setPassword(passwordEncoder.encode(req.password()));
        user.setRole("CUSTOMER");
        user.setActive(true);
        userRepository.save(user);
        return toAuthResponse(user);
    }

    public AuthResponse login(LoginRequest req) {
        User user = userRepository.findByPhone(req.phone().trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Invalid phone number or password"));
        if (!user.isActive()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Your account is disabled. Please contact the farm.");
        }
        if (!passwordEncoder.matches(req.password(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Invalid phone number or password");
        }
        return toAuthResponse(user);
    }

    private AuthResponse toAuthResponse(User user) {
        return new AuthResponse(jwtUtil.generateToken(user), user.getId(), user.getName(),
                user.getPhone(), user.getRole());
    }
}
