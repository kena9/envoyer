package com.akumathreads.controller;

import com.akumathreads.service.EmailService;
import com.akumathreads.service.NewsletterService;
import com.akumathreads.service.PushNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Admin page to send drop announcements + push notifications.
 * Reachable at /admin/notifications — requires ADMIN role.
 */
@Controller
@RequestMapping("/admin/notifications")
@RequiredArgsConstructor
public class AdminNotificationController {

    private final NewsletterService      newsletterService;
    private final EmailService           emailService;
    private final PushNotificationService pushService;

    @GetMapping
    public String page(Model model) {
        model.addAttribute("subscriberCount", newsletterService.countActive());
        return "admin/notifications";
    }

    /**
     * Send a drop/deal announcement to all subscribers.
     *
     * @param channel  "email", "push", or "both"
     * @param subject  email subject line
     * @param message  plain-text announcement body (converted to simple HTML for email)
     */
    @PostMapping("/send")
    public String send(@RequestParam String channel,
                       @RequestParam String subject,
                       @RequestParam String message,
                       RedirectAttributes ra) {

        List<String> emails = newsletterService.getAllActiveEmails();

        if (emails.isEmpty() && channel.contains("email")) {
            ra.addFlashAttribute("warnMsg", "No active email subscribers.");
        }

        boolean sentEmail = false;
        boolean sentPush  = false;

        if ("email".equals(channel) || "both".equals(channel)) {
            String htmlBody = toHtml(message);
            emailService.sendDropAnnouncement(emails, subject, htmlBody);
            sentEmail = true;
        }

        if ("push".equals(channel) || "both".equals(channel)) {
            pushService.sendToAll(subject, message);
            sentPush = true;
        }

        String result = buildResultMessage(sentEmail, sentPush, emails.size());
        ra.addFlashAttribute("successMsg", result);
        return "redirect:/admin/notifications";
    }

    private String toHtml(String text) {
        return "<div style=\"font-family:monospace; max-width:520px; margin:40px auto; color:#111;\">"
                + "<p style=\"font-size:1rem; line-height:1.7; color:#333;\">"
                + text.replace("\n", "<br/>")
                + "</p></div>";
    }

    private String buildResultMessage(boolean email, boolean push, int emailCount) {
        StringBuilder sb = new StringBuilder("Sent");
        if (email) sb.append(" to ").append(emailCount).append(" email subscriber(s)");
        if (email && push) sb.append(" +");
        if (push) sb.append(" push notification to all browser subscribers");
        sb.append(".");
        return sb.toString();
    }
}
