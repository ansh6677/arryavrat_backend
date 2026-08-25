package com.aryavart.dairy.controller;

import com.aryavart.dairy.dto.AuthResponse;
import com.aryavart.dairy.dto.LoginRequest;
import com.aryavart.dairy.dto.RegisterRequest;
import com.aryavart.dairy.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request,
                              jakarta.servlet.http.HttpServletRequest http) {
        // Behind Render the client sits in X-Forwarded-For; locally it's the socket.
        String forwarded = http.getHeader("X-Forwarded-For");
        String ip = (forwarded != null && !forwarded.isBlank())
                ? forwarded.split(",")[0].trim() : http.getRemoteAddr();
        return authService.login(request, http.getHeader("User-Agent"), ip);
    }
}
