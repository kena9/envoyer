package com.akumathreads.service;

import com.akumathreads.model.PasswordResetToken;
import com.akumathreads.model.User;
import com.akumathreads.repository.PasswordResetTokenRepository;
import com.akumathreads.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Business logic for user account management.
 *
 * <p>Class-level {@code readOnly = true} optimises all reads; every write
 * method overrides with {@code readOnly = false, rollbackFor = Exception.class}.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserService {

    private final UserRepository             userRepository;
    private final PasswordEncoder            passwordEncoder;
    private final PasswordResetTokenRepository resetTokenRepo;

    // ── Read operations ──────────────────────────────────────────────────────

    public boolean emailExists(String email) {
        return userRepository.existsByEmail(email);
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    // ── Write operations ─────────────────────────────────────────────────────

    /**
     * Registers a new customer account.
     */
    @Transactional(readOnly = false, rollbackFor = Exception.class)
    public User register(String name, String email, String rawPassword) {
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already in use: " + email);
        }
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(User.Role.CUSTOMER);
        return userRepository.save(user);
    }

    // ── Password reset ────────────────────────────────────────────────────────

    /**
     * Creates a one-time password reset token for the given email (if the user exists).
     * Deletes any prior token for this user first.
     *
     * @return the token string, or {@code null} if no account matches the email
     */
    @Transactional(readOnly = false, rollbackFor = Exception.class)
    public String createPasswordResetToken(String email) {
        Optional<User> userOpt = userRepository.findByEmail(email.toLowerCase().strip());
        if (userOpt.isEmpty()) return null;

        User user = userOpt.get();
        // Invalidate any existing token for this user
        resetTokenRepo.deleteByUserId(user.getId());

        PasswordResetToken token = new PasswordResetToken(user);
        resetTokenRepo.save(token);
        return token.getToken();
    }

    /**
     * Validates a reset token and returns the associated user if it's valid and unexpired.
     */
    public Optional<User> validateResetToken(String token) {
        return resetTokenRepo.findByToken(token)
                .filter(t -> !t.isExpired())
                .map(PasswordResetToken::getUser);
    }

    /**
     * Resets the user's password and deletes the used token.
     *
     * @return {@code true} on success, {@code false} if the token is invalid/expired
     */
    @Transactional(readOnly = false, rollbackFor = Exception.class)
    public boolean resetPassword(String token, String newRawPassword) {
        return resetTokenRepo.findByToken(token)
                .filter(t -> !t.isExpired())
                .map(t -> {
                    User user = t.getUser();
                    user.setPasswordHash(passwordEncoder.encode(newRawPassword));
                    userRepository.save(user);
                    resetTokenRepo.delete(t);
                    return true;
                })
                .orElse(false);
    }

    /** Housekeeping: remove expired tokens (can be called from a scheduled task). */
    @Transactional(readOnly = false, rollbackFor = Exception.class)
    public void purgeExpiredTokens() {
        resetTokenRepo.deleteAllExpired(LocalDateTime.now());
    }

    // ── Account management ────────────────────────────────────────────────────

    @Transactional(readOnly = false, rollbackFor = Exception.class)
    public void softDeleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
        userRepository.delete(user);
    }

    @Transactional(readOnly = false, rollbackFor = Exception.class)
    public User promoteToAdmin(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
        user.setRole(User.Role.ADMIN);
        return userRepository.save(user);
    }

    @Transactional(readOnly = false, rollbackFor = Exception.class)
    public User updateName(Long userId, String newName) {
        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("Name must not be blank.");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
        user.setName(newName.strip());
        return userRepository.save(user);
    }
}
