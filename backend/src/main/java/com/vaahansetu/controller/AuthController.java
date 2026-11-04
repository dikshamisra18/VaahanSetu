package com.vaahansetu.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.vaahansetu.service.AuthService;

/**
 * Authentication endpoints used by the officer desktop and admin interface.
 *
 * <p>POST /api/auth/login  — authenticate and receive a bearer token.
 * <p>POST /api/auth/logout — invalidate the token.
 * <p>GET  /api/auth/me     — validate token and return current user info.
 */
@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @RequestBody Map<String, Object> body) {

        if (body == null) {
            return error(HttpStatus.BAD_REQUEST, "Please provide credentials.");
        }

        String username = body.get("username") == null ? null
                : body.get("username").toString();
        String password = body.get("password") == null ? null
                : body.get("password").toString();

        if (username == null || username.isBlank()
                || password == null || password.isEmpty()) {
            return error(HttpStatus.BAD_REQUEST,
                    "Username and password are required.");
        }

        Map<String, Object> session = authService.login(username, password);
        if (session == null) {
            return error(HttpStatus.UNAUTHORIZED,
                    "Invalid username or password.");
        }

        return ResponseEntity.ok(session);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(
            @RequestHeader(value = "Authorization", required = false) String auth) {

        authService.logout(extractToken(auth));
        return ResponseEntity.ok(Map.of("message", "Logged out successfully."));
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(
            @RequestHeader(value = "Authorization", required = false) String auth) {

        Map<String, Object> session = authService.validate(extractToken(auth));
        if (session == null) {
            return error(HttpStatus.UNAUTHORIZED, "Invalid or expired token.");
        }
        return ResponseEntity.ok(session);
    }

    // Utility: extract "Bearer <token>" from Authorization header.
    static String extractToken(String authHeader) {
        if (authHeader == null) return null;
        if (authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim();
        }
        return authHeader.trim();
    }

    private ResponseEntity<Map<String, Object>> error(
            HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("message", message));
    }
}
