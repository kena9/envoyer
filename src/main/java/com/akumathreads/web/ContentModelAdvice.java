package com.akumathreads.web;

import com.akumathreads.service.SiteContentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Map;

/**
 * Injects global model attributes into every Thymeleaf model automatically.
 *
 * <p>Templates access values via:
 * <ul>
 *   <li>{@code ${content['key']}} — site content from the admin editor</li>
 *   <li>{@code ${baseUrl}} — e.g. https://ollythreads.com (from app.base-url)</li>
 *   <li>{@code ${canonicalUrl}} — full canonical URL for the current request</li>
 * </ul>
 */
@ControllerAdvice
@RequiredArgsConstructor
public class ContentModelAdvice {

    private final SiteContentService siteContentService;

    /** Configured in application.properties / environment variables. */
    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    /** Site content map — all rows from the site_content table keyed by content_key. */
    @ModelAttribute("content")
    public Map<String, String> content() {
        return siteContentService.getAllAsMap();
    }

    /**
     * Absolute base URL (no trailing slash).
     * Used in og:image, og:url, twitter:image, and JSON-LD to emit absolute URLs.
     */
    @ModelAttribute("baseUrl")
    public String baseUrl() {
        return baseUrl;
    }

    /**
     * Canonical URL for the current request — base URL + path (no query string).
     * Controllers can override by adding their own "canonicalUrl" to the model
     * (e.g. a product page that wants /product/{id} regardless of ref params).
     */
    @ModelAttribute("canonicalUrl")
    public String canonicalUrl(HttpServletRequest request) {
        return baseUrl + request.getRequestURI();
    }
}
