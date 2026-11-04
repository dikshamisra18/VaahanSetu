package com.vaahansetu.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.vaahansetu.repository.UserRepository;

/**
 * Simple stateless-token authentication service.
 *
 * <p>Issues a UUID session token on login and validates it on subsequent
 * requests. Tokens are kept in memory; they are lost on server restart
 * (acceptable for development). A production implementation would use a
 * signed JWT or a persistent session store.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // token -> { userId, fullName, role }
    private final ConcurrentHashMap<String, Map<String, Object>> activeSessions =
            new ConcurrentHashMap<>();

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Authenticate a user. Returns a session token + user info on success,
     * or null on failure.
     */
    public Map<String, Object> login(String username, String password) {
        if (username == null || password == null) {
            return null;
        }

        Map<String, Object> user = userRepository.findByUsername(username.trim());
        if (user == null) {
            return null;
        }

        String hash = (String) user.get("passwordHash");
        if (!passwordEncoder.matches(password, hash)) {
            return null;
        }

        String token = UUID.randomUUID().toString();
        Map<String, Object> session = Map.of(
                "token",     token,
                "userId",    user.get("userId"),
                "fullName",  user.get("fullName"),
                "username",  user.get("username"),
                "role",      user.get("role")
        );
        activeSessions.put(token, session);
        return session;
    }

    /**
     * Validate a bearer token. Returns session info or null if invalid.
     */
    public Map<String, Object> validate(String token) {
        if (token == null) return null;
        return activeSessions.get(token);
    }

    /** Invalidate a token (logout). */
    public void logout(String token) {
        if (token != null) {
            activeSessions.remove(token);
        }
    }

    /**
     * Encode a plain-text password using BCrypt (utility for seeding).
     */
    public String encodePassword(String plain) {
        return passwordEncoder.encode(plain);
    }
}
