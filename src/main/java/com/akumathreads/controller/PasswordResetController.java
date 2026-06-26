package com.akumathreads.controller;

import com.akumathreads.service.EmailService;
import com.akumathreads.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Handles the forgot-password / reset-password flow.
 *
 * <p>POST /forgot-password → creates token, emails link (always shows same success message
 * regardless of whether the account exists, to prevent user enumeration).
 *
 * <p>GET  /reset-password?token=…  → shows the new-password form.
 * <p>POST /reset-password          → validates token, hashes + saves new password.
 */
@Controller
@RequiredArgsConstructor
public class PasswordResetController {

    private final UserService  userService;
    private final EmailService emailService;

    // ── Forgot password ───────────────────────────────────────────────────────

    @GetMapping("/forgot-password")
    public String forgotPasswordPage() {
        return "auth/forgot-password";
    }

    @PostMapping("/forgot-password")
    public String sendResetLink(@RequestParam String email,
                                RedirectAttributes ra) {
        // Create token (null = email not found — we don't tell the user which)
        String token = userService.createPasswordResetToken(email);
        if (token != null) {
            emailService.sendPasswordResetEmail(email, token);
        }
        // Always show the same message to prevent user enumeration
        ra.addFlashAttribute("successMsg",
                "If an account with that email exists, you'll receive a reset link shortly.");
        return "redirect:/forgot-password";
    }

    // ── Reset password ────────────────────────────────────────────────────────

    @GetMapping("/reset-password")
    public String resetPasswordPage(@RequestParam String token, Model model) {
        // Validate the token before showing the form
        boolean valid = userService.validateResetToken(token).isPresent();
        model.addAttribute("token", token);
        model.addAttribute("valid", valid);
        return "auth/reset-password";
    }

    @PostMapping("/reset-password")
    public String doReset(@RequestParam String token,
                          @RequestParam String password,
                          @RequestParam String confirmPassword,
                          RedirectAttributes ra) {

        if (!password.equals(confirmPassword)) {
            ra.addFlashAttribute("errorMsg", "Passwords do not match.");
            return "redirect:/reset-password?token=" + token;
        }

        if (password.length() < 8) {
            ra.addFlashAttribute("errorMsg", "Password must be at least 8 characters.");
            return "redirect:/reset-password?token=" + token;
        }

        boolean ok = userService.resetPassword(token, password);
        if (!ok) {
            ra.addFlashAttribute("errorMsg",
                    "This reset link is invalid or has expired. Please request a new one.");
            return "redirect:/forgot-password";
        }

        ra.addFlashAttribute("successMsg",
                "Password updated! You can now log in with your new password.");
        return "redirect:/login";
    }
}
