package com.aryavart.dairy.service;

import com.aryavart.dairy.dto.AuthResponse;
import com.aryavart.dairy.dto.LoginRequest;
import com.aryavart.dairy.dto.RegisterRequest;
import com.aryavart.dairy.model.LoginEvent;
import com.aryavart.dairy.model.User;
import com.aryavart.dairy.repository.LoginEventRepository;
import com.aryavart.dairy.repository.UserRepository;
import com.aryavart.dairy.security.JwtUtil;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final LoginEventRepository loginEventRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserRepository userRepository, LoginEventRepository loginEventRepository,
                       PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.loginEventRepository = loginEventRepository;
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
        user.setSignupSource("PAGE");
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

    /**
     * Successful sign-in: stamp the user, drop a LoginEvent for the activity
     * feed, and quietly evict entries older than 90 days.
     */
    public void recordLogin(User user, String userAgent, String ip) {
        user.setLastLoginAt(java.time.Instant.now());
        userRepository.save(user);

        LoginEvent event = new LoginEvent();
        event.setUserId(user.getId());
        event.setName(user.getName());
        event.setLoginId(user.getPhone());
        event.setRole(user.getRole());
        event.setSide("CUSTOMER".equals(user.getRole()) ? "CUSTOMER" : "MANAGEMENT");
        event.setDevice(describeDevice(userAgent));
        event.setIp(ip);
        loginEventRepository.save(event);
        loginEventRepository.deleteByAtBefore(java.time.Instant.now().minus(java.time.Duration.ofDays(90)));
    }

    /** A short human summary of the browser — never the raw user-agent soup. */
    private String describeDevice(String ua) {
        if (ua == null || ua.isBlank()) return "Unknown device";
        String s = ua.toLowerCase();
        String kind = (s.contains("mobi") || s.contains("android") || s.contains("iphone")) ? "Mobile" : "Desktop";
        String browser = s.contains("edg/") ? "Edge"
                : s.contains("chrome") ? "Chrome"
                : s.contains("safari") ? "Safari"
                : s.contains("firefox") ? "Firefox"
                : "Browser";
        return kind + " · " + browser;
    }

    public AuthResponse login(LoginRequest req, String userAgent, String ip) {
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
        recordLogin(user, userAgent, ip);
        return toAuthResponse(user);
    }

    private AuthResponse toAuthResponse(User user) {
        return new AuthResponse(jwtUtil.generateToken(user), user.getId(), user.getName(),
                user.getPhone(), user.getRole());
    }
}
