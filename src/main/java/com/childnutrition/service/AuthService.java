package com.childnutrition.service;

import com.childnutrition.dto.AuthDtos;
import com.childnutrition.entity.User;
import com.childnutrition.repository.UserRepository;
import com.childnutrition.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final JwtService jwt;

    public AuthService(UserRepository users, PasswordEncoder passwords, JwtService jwt) {
        this.users = users; this.passwords = passwords; this.jwt = jwt;
    }

    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (users.existsByEmail(email)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        User user = new User();
        user.setName(request.name().trim()); user.setEmail(email);
        user.setPassword(passwords.encode(request.password())); user.setRole("PARENT");
        users.save(user);
        return response(user);
    }

    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
        User user = users.findByEmail(request.email().trim().toLowerCase())
                .filter(found -> passwords.matches(request.password(), found.getPassword()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        return response(user);
    }

    private AuthDtos.AuthResponse response(User user) {
        return new AuthDtos.AuthResponse(user.getId(), user.getName(), user.getEmail(), user.getRole(),
                jwt.issue(user.getEmail(), user.getRole()));
    }
}
