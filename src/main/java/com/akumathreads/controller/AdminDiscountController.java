package com.akumathreads.controller;

import com.akumathreads.model.DiscountCode;
import com.akumathreads.service.DiscountCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Controller
@RequestMapping("/admin/discounts")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminDiscountController {

    private final DiscountCodeService service;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("codes", service.findAll());
        model.addAttribute("newCode", new DiscountCode());
        return "admin/discounts";
    }

    @PostMapping("/create")
    public String create(@RequestParam String code,
                         @RequestParam String type,
                         @RequestParam BigDecimal value,
                         @RequestParam(required = false) Integer usageLimit,
                         @RequestParam(required = false) BigDecimal minOrderValue,
                         @RequestParam(required = false) String description,
                         @RequestParam(required = false) String expiresAt,
                         RedirectAttributes ra) {
        DiscountCode dc = new DiscountCode(
                code,
                DiscountCode.DiscountType.valueOf(type),
                value,
                usageLimit,
                description);
        dc.setMinOrderValue(minOrderValue);
        if (expiresAt != null && !expiresAt.isBlank()) {
            dc.setExpiresAt(LocalDateTime.parse(expiresAt + "T23:59:59"));
        }
        service.save(dc);
        ra.addFlashAttribute("successMsg", "Code \"" + dc.getCode() + "\" created.");
        return "redirect:/admin/discounts";
    }

    @PostMapping("/{id}/toggle")
    @ResponseBody
    public ResponseEntity<Map<String, String>> toggle(@PathVariable Long id) {
        service.toggleActive(id);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
