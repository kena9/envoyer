package com.akumathreads.controller;

import com.akumathreads.model.SiteContent;
import com.akumathreads.service.SiteContentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin/content")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminContentController {

    private final SiteContentService siteContentService;

    // ── GET /admin/content ────────────────────────────────────────────────────

    @GetMapping
    public String contentPage(Model model) {
        // Group all entries by page for the template
        List<SiteContent> all = siteContentService.getAllForAdmin();

        Map<String, List<SiteContent>> grouped = new LinkedHashMap<>();
        grouped.put("Global", siteContentService.getByPage("Global"));
        grouped.put("Home",   siteContentService.getByPage("Home"));
        grouped.put("About",  siteContentService.getByPage("About"));

        model.addAttribute("grouped", grouped);
        return "admin/content";
    }

    // ── POST /admin/content/update (AJAX) ─────────────────────────────────────
    // Expects JSON body: { "key": "home.hero.eyebrow", "value": "..." }

    @PostMapping("/update")
    @ResponseBody
    public ResponseEntity<Map<String, String>> update(@RequestBody Map<String, String> body) {
        String key   = body.get("key");
        String value = body.get("value");

        if (key == null || key.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("status", "error", "message", "Key is required"));
        }

        boolean updated = siteContentService.update(key, value);
        if (!updated) {
            return ResponseEntity.badRequest()
                    .body(Map.of("status", "error", "message", "Unknown content key: " + key));
        }

        return ResponseEntity.ok(Map.of("status", "ok", "message", "Saved"));
    }
}
