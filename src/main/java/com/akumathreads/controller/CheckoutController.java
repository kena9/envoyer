package com.akumathreads.controller;

import com.akumathreads.dto.CheckoutForm;
import com.akumathreads.dto.OrderItemRequest;
import com.akumathreads.exception.InsufficientStockException;
import com.akumathreads.model.DiscountCode;
import com.akumathreads.model.Order;
import com.akumathreads.model.SessionCart;
import com.akumathreads.model.User;
import com.akumathreads.service.DiscountCodeService;
import com.akumathreads.service.OrderService;
import com.akumathreads.service.StripeService;
import com.akumathreads.service.UserService;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
@Slf4j
public class CheckoutController {

    private final OrderService        orderService;
    private final UserService         userService;
    private final DiscountCodeService discountCodeService;
    private final StripeService       stripeService;

    private static final BigDecimal FREE_SHIPPING_THRESHOLD = new BigDecimal("75.00");
    private static final BigDecimal SHIPPING_RATE           = new BigDecimal("8.99");
    private static final BigDecimal TAX_RATE                = new BigDecimal("0.08");

    // ── GET /checkout ──────────────────────────────────────────────────────────

    @GetMapping("/checkout")
    @PreAuthorize("isAuthenticated()")
    public String checkoutPage(@AuthenticationPrincipal UserDetails principal,
                               HttpSession session, Model model) {
        SessionCart cart = resolveCart(session);
        if (cart.isEmpty()) return "redirect:/shop";

        CheckoutForm form = new CheckoutForm();
        form.setEmail(principal.getUsername());

        model.addAttribute("cart",                 cart);
        model.addAttribute("form",                 form);
        model.addAttribute("stripePublishableKey", stripeService.getPublishableKey());
        return "checkout";
    }

    // ── POST /checkout/payment-intent (AJAX) ──────────────────────────────────

    @PostMapping("/checkout/payment-intent")
    @PreAuthorize("isAuthenticated()")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createPaymentIntent(
            @RequestParam String email,
            @RequestParam(required = false, defaultValue = "") String couponCode,
            HttpSession session) {

        SessionCart cart = resolveCart(session);
        if (cart.isEmpty())
            return ResponseEntity.badRequest().body(Map.of("error", "Cart is empty"));

        BigDecimal subtotal = cart.getItems().stream()
                .map(e -> e.unitPrice().multiply(BigDecimal.valueOf(e.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal shipping = subtotal.compareTo(FREE_SHIPPING_THRESHOLD) >= 0
                ? BigDecimal.ZERO : SHIPPING_RATE;

        BigDecimal discount    = BigDecimal.ZERO;
        boolean    couponValid = false;
        String     resolvedCode = null;

        if (!couponCode.isBlank()) {
            DiscountCodeService.ValidationResult val = discountCodeService.validate(couponCode, subtotal);
            if (val.valid()) {
                DiscountCode dc = val.code();
                discount = dc.getType() == DiscountCode.DiscountType.PERCENT
                        ? subtotal.multiply(dc.getValue())
                               .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                        : dc.getValue().min(subtotal).setScale(2, RoundingMode.HALF_UP);
                couponValid  = true;
                resolvedCode = dc.getCode();
            }
        }

        BigDecimal taxBase = subtotal.subtract(discount).add(shipping).max(BigDecimal.ZERO);
        BigDecimal tax     = taxBase.multiply(TAX_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total   = taxBase.add(tax).setScale(2, RoundingMode.HALF_UP);

        if (total.compareTo(new BigDecimal("0.50")) < 0)
            return ResponseEntity.badRequest().body(Map.of("error", "Order total below minimum ($0.50)"));

        try {
            PaymentIntent intent = stripeService.createPaymentIntent(total, email);

            // Stash in session — POST /checkout verifies these
            session.setAttribute("pendingPaymentIntentId", intent.getId());
            session.setAttribute("pendingOrderTotal",      total);
            if (resolvedCode != null) {
                session.setAttribute("pendingCouponCode", resolvedCode);
                session.setAttribute("pendingDiscount",   discount);
            }

            Map<String, Object> resp = new HashMap<>();
            resp.put("clientSecret", intent.getClientSecret());
            resp.put("total",        total);
            resp.put("shipping",     shipping);
            resp.put("tax",          tax);
            resp.put("discount",     discount);
            resp.put("couponValid",  couponValid);
            return ResponseEntity.ok(resp);

        } catch (StripeException e) {
            log.error("[Checkout] PaymentIntent creation failed: {}", e.getMessage());
            return ResponseEntity.status(503).body(Map.of("error",
                    "Payment service unavailable. Please try again."));
        }
    }

    // ── POST /api/discount/validate (AJAX) ────────────────────────────────────

    @PostMapping("/api/discount/validate")
    @PreAuthorize("isAuthenticated()")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> validateDiscount(
            @RequestParam String code, HttpSession session) {

        SessionCart cart = resolveCart(session);
        BigDecimal subtotal = cart.getItems().stream()
                .map(e -> e.unitPrice().multiply(BigDecimal.valueOf(e.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        DiscountCodeService.ValidationResult result = discountCodeService.validate(code, subtotal);
        if (!result.valid())
            return ResponseEntity.ok(Map.of("valid", false, "error", result.error()));

        DiscountCode dc = result.code();
        BigDecimal discount = dc.getType() == DiscountCode.DiscountType.PERCENT
                ? subtotal.multiply(dc.getValue())
                       .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : dc.getValue().min(subtotal).setScale(2, RoundingMode.HALF_UP);

        return ResponseEntity.ok(Map.of(
                "valid",    true,
                "code",     dc.getCode(),
                "type",     dc.getType().name(),
                "value",    dc.getValue(),
                "savings",  discount,
                "discount", discount
        ));
    }

    // ── POST /checkout ─────────────────────────────────────────────────────────

    @PostMapping("/checkout")
    @PreAuthorize("isAuthenticated()")
    public String placeOrder(@Valid CheckoutForm form,
                             BindingResult bindingResult,
                             @AuthenticationPrincipal UserDetails principal,
                             HttpSession session, Model model) {

        SessionCart cart = resolveCart(session);
        if (cart.isEmpty()) return "redirect:/shop";

        if (bindingResult.hasErrors()) {
            addCheckoutModel(model, cart, form);
            return "checkout";
        }

        String paymentIntentId = form.getPaymentIntentId();
        if (paymentIntentId == null || paymentIntentId.isBlank()) {
            addCheckoutModel(model, cart, form);
            model.addAttribute("paymentError", "Payment was not completed. Please try again.");
            return "checkout";
        }

        // Must match the intent we created in this session
        String sessionIntentId = (String) session.getAttribute("pendingPaymentIntentId");
        if (!paymentIntentId.equals(sessionIntentId)) {
            log.warn("[Checkout] PI mismatch — form:{} session:{}", paymentIntentId, sessionIntentId);
            addCheckoutModel(model, cart, form);
            model.addAttribute("paymentError", "Payment session expired. Please try again.");
            return "checkout";
        }

        // Verify with Stripe and check the amount matches
        try {
            PaymentIntent intent = stripeService.retrieveAndVerify(
                    paymentIntentId,
                    (BigDecimal) session.getAttribute("pendingOrderTotal"));
            if (intent == null) {
                addCheckoutModel(model, cart, form);
                model.addAttribute("paymentError", "Payment could not be verified. Please contact support.");
                return "checkout";
            }
        } catch (StripeException e) {
            log.error("[Checkout] Stripe verification error: {}", e.getMessage());
            addCheckoutModel(model, cart, form);
            model.addAttribute("paymentError", "Payment verification failed. Please contact support.");
            return "checkout";
        }

        // Clear pending payment data from session
        session.removeAttribute("pendingPaymentIntentId");
        session.removeAttribute("pendingOrderTotal");
        String     pendingCoupon   = (String)     session.getAttribute("pendingCouponCode");
        BigDecimal pendingDiscount = (BigDecimal) session.getAttribute("pendingDiscount");
        session.removeAttribute("pendingCouponCode");
        session.removeAttribute("pendingDiscount");

        User user = userService.findByEmail(principal.getUsername())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));

        // Use session-verified discount (not the form field, which is client-sent)
        String     couponCode    = pendingCoupon   != null ? pendingCoupon   : form.getCouponCode();
        BigDecimal discountAmount = pendingDiscount != null ? pendingDiscount : BigDecimal.ZERO;

        List<OrderItemRequest> items = cart.getItems().stream()
                .map(e -> new OrderItemRequest(e.variantId(), e.quantity()))
                .collect(Collectors.toList());

        String fullAddress = (form.getAddress2() != null && !form.getAddress2().isBlank())
                ? form.getAddress() + ", " + form.getAddress2()
                : form.getAddress();

        try {
            Order order = orderService.placeOrder(
                    user.getId(), items,
                    form.getFullName(), fullAddress,
                    form.getCity(), form.getState(), form.getZip(),
                    couponCode, discountAmount, paymentIntentId);

            cart.clear();
            session.setAttribute("cart", cart);
            session.setAttribute("nextOrderCode", discountCodeService.generateNextOrderCode().getCode());

            return "redirect:/order/" + order.getId() + "/confirmation";

        } catch (InsufficientStockException ex) {
            addCheckoutModel(model, cart, form);
            model.addAttribute("stockError",
                    "One or more items ran out of stock. Please review your cart and try again.");
            return "checkout";
        }
    }

    // ── GET /order/{id}/confirmation ───────────────────────────────────────────

    @GetMapping("/order/{id}/confirmation")
    @PreAuthorize("isAuthenticated()")
    public String orderConfirmation(@PathVariable Long id,
                                    @AuthenticationPrincipal UserDetails principal,
                                    HttpSession session, Model model) {
        User user = userService.findByEmail(principal.getUsername())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));

        Order order = orderService.findOrderWithItemsForUser(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        String nextOrderCode = (String) session.getAttribute("nextOrderCode");
        session.removeAttribute("nextOrderCode");

        model.addAttribute("order",         order);
        model.addAttribute("nextOrderCode", nextOrderCode);
        return "order-confirmation";
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private void addCheckoutModel(Model model, SessionCart cart, CheckoutForm form) {
        model.addAttribute("cart",                 cart);
        model.addAttribute("form",                 form);
        model.addAttribute("stripePublishableKey", stripeService.getPublishableKey());
    }

    private SessionCart resolveCart(HttpSession session) {
        Object attr = session.getAttribute("cart");
        return attr instanceof SessionCart existing ? existing : new SessionCart();
    }
}
