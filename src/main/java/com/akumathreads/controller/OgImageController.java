package com.akumathreads.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Serves a branded 1200×630 Open Graph image at /og-image.png
 *
 * PNG is required — Facebook, Instagram, X/Twitter, Discord, WhatsApp,
 * iMessage, and LinkedIn do NOT render SVG for og:image. An SVG at that
 * endpoint produces a blank card everywhere that matters.
 *
 * Also retains the legacy /og-image.svg endpoint for any bookmark or
 * crawler that may have the old URL cached.
 */
@RestController
public class OgImageController {

    private static final int W = 1200;
    private static final int H = 630;

    // ── PNG — the one social scrapers actually render ──────────────────────
    @GetMapping(value = "/og-image.png", produces = "image/png")
    public ResponseEntity<byte[]> ogImagePng(
            @RequestParam(required = false, defaultValue = "") String title)
            throws IOException {

        String[] lines = splitTitle(sanitise(title));

        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Background
        g.setColor(new Color(0x0a, 0x0a, 0x0a));
        g.fillRect(0, 0, W, H);

        // Top crimson accent stripe
        g.setColor(new Color(0xe5, 0x09, 0x14));
        g.fillRect(0, 0, W, 8);

        // Corner marks (decorative, top-left)
        g.setColor(new Color(0x1a, 0x1a, 0x1a));
        g.setStroke(new BasicStroke(2));
        g.drawLine(48, 60, 48, 100);
        g.drawLine(48, 60, 88, 60);

        // "OLLY" — white
        g.setFont(loadImpact(96));
        g.setColor(Color.WHITE);
        g.drawString("OLLY", 80, 180);

        // "THREADS" — crimson
        g.setColor(new Color(0xe5, 0x09, 0x14));
        g.drawString("THREADS", 80, 280);

        // Divider
        g.setColor(new Color(0x22, 0x22, 0x22));
        g.setStroke(new BasicStroke(1));
        g.drawLine(80, 320, W - 80, 320);

        // Product title lines
        g.setFont(loadImpact(52));
        g.setColor(Color.WHITE);
        g.drawString(lines[0], 80, 390);
        if (!lines[1].isEmpty()) g.drawString(lines[1], 80, 450);

        // Tagline
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 18));
        g.setColor(new Color(0x55, 0x55, 0x55));
        g.drawString("ORIGINAL INK  ·  PRINT ON DEMAND  ·  NO RESTOCKS", 80, 530);

        // Handle
        g.setColor(new Color(0x33, 0x33, 0x33));
        g.drawString("@OLIVER_JIN_WANG", 80, 590);

        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header("Cache-Control", "public, max-age=86400")
                .body(baos.toByteArray());
    }

    // ── SVG — kept for backwards compatibility only ────────────────────────
    // Social scrapers ignore SVG; this path is only hit by cached old URLs.
    @GetMapping(value = "/og-image.svg", produces = "image/svg+xml")
    public ResponseEntity<byte[]> ogImageSvgLegacy(
            @RequestParam(required = false, defaultValue = "") String title) {
        String safe = sanitise(title);
        String[] lines = splitTitle(safe);
        String svg = buildSvg(lines[0], lines[1]);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("image/svg+xml"))
                .header("Cache-Control", "public, max-age=86400")
                .body(svg.getBytes(StandardCharsets.UTF_8));
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private static String sanitise(String title) {
        String s = title.replaceAll("<[^>]*>", "").strip();
        if (s.length() > 48) s = s.substring(0, 45) + "...";
        return s.isEmpty() ? "Original Anime Art — Wearable" : s;
    }

    private static String[] splitTitle(String title) {
        String[] words = title.split("\\s+");
        StringBuilder l1 = new StringBuilder(), l2 = new StringBuilder();
        for (String w : words) {
            if (l1.length() + w.length() < 25) { if (!l1.isEmpty()) l1.append(" "); l1.append(w); }
            else                                { if (!l2.isEmpty()) l2.append(" "); l2.append(w); }
        }
        return new String[]{ l1.toString().toUpperCase(), l2.toString().toUpperCase() };
    }

    /** Use Impact if available on the JVM host, fall back to a bold sans-serif. */
    private static Font loadImpact(int size) {
        Font f = new Font("Impact", Font.BOLD, size);
        // If Impact isn't installed, Java falls back to "Dialog" — use Arial Narrow Bold instead
        return f.getFamily().equals("Impact") ? f : new Font("Arial Narrow", Font.BOLD, size);
    }

    private static String buildSvg(String l1, String l2) {
        return String.format("""
                <svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" viewBox="0 0 %d %d">
                  <rect width="%d" height="%d" fill="#0a0a0a"/>
                  <rect x="0" y="0" width="%d" height="8" fill="#e50914"/>
                  <text x="80" y="180" font-family="Impact,Arial Narrow Bold,sans-serif" font-size="96" fill="#ffffff">OLLY</text>
                  <text x="80" y="280" font-family="Impact,Arial Narrow Bold,sans-serif" font-size="96" fill="#e50914">THREADS</text>
                  <line x1="80" y1="320" x2="%d" y2="320" stroke="#222" stroke-width="1"/>
                  <text x="80" y="390" font-family="Impact,Arial Narrow Bold,sans-serif" font-size="52" fill="#ffffff">%s</text>
                  <text x="80" y="450" font-family="Impact,Arial Narrow Bold,sans-serif" font-size="52" fill="#ffffff">%s</text>
                  <text x="80" y="530" font-family="monospace" font-size="18" fill="#555">ORIGINAL INK · PRINT ON DEMAND · NO RESTOCKS</text>
                  <text x="80" y="590" font-family="monospace" font-size="16" fill="#333">@OLIVER_JIN_WANG</text>
                </svg>""",
                W, H, W, H, W, H, W, W - 80,
                escXml(l1), escXml(l2));
    }

    private static String escXml(String s) {
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
    }
}
