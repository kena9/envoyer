package com.akumathreads.service;

import com.akumathreads.model.SiteContent;
import com.akumathreads.repository.SiteContentRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SiteContentService {

    private final SiteContentRepository repo;

    // ── Public API ────────────────────────────────────────────────────────────

    /** Returns value for key, or the provided fallback if key not found. */
    public String get(String key, String fallback) {
        return repo.findByContentKey(key)
                .map(SiteContent::getContentValue)
                .orElse(fallback);
    }

    /** Returns all content entries as a flat Map<key, value> for Thymeleaf.
     *  Cached — evicted any time a content entry is updated via the admin editor. */
    @Cacheable("siteContent")
    @Transactional(readOnly = true)
    public Map<String, String> getAllAsMap() {
        Map<String, String> map = new LinkedHashMap<>();
        for (SiteContent sc : repo.findAll()) {
            map.put(sc.getContentKey(), sc.getContentValue());
        }
        return map;
    }

    /** Returns all content entries ordered by page then id — used in admin UI. */
    @Transactional(readOnly = true)
    public List<SiteContent> getAllForAdmin() {
        return repo.findAll();
    }

    /** Returns all entries for a given page group. */
    @Transactional(readOnly = true)
    public List<SiteContent> getByPage(String page) {
        return repo.findByPageOrderByIdAsc(page);
    }

    /** Update a single content entry by key. Returns false if key doesn't exist. */
    @CacheEvict(value = "siteContent", allEntries = true)
    @Transactional
    public boolean update(String key, String value) {
        return repo.findByContentKey(key).map(sc -> {
            sc.setContentValue(value == null ? "" : value.trim());
            repo.save(sc);
            return true;
        }).orElse(false);
    }

    // ── Default content seeding ───────────────────────────────────────────────
    // Runs once on startup. Uses findOrCreate so restarts never wipe content.

    @PostConstruct
    @Transactional
    public void seedDefaults() {
        seed("global.ticker.drop",
                "NEW DROP — OLLY THREADS SERIES 01",
                "Ticker — Drop announcement",      "Global", false);

        seed("global.ticker.shipping",
                "FREE SHIPPING OVER $75",
                "Ticker — Shipping message",       "Global", false);

        seed("global.ticker.info",
                "PRINT-ON-DEMAND × NO RESTOCKS",
                "Ticker — Brand info",             "Global", false);

        seed("global.ticker.handle",
                "ART BY @OLIVER_JIN_WANG — ORIGINAL INK",
                "Ticker — Artist handle",          "Global", false);

        seed("home.hero.eyebrow",
                "Drop 01 — Limited Series",
                "Home — Hero eyebrow label",       "Home",   false);

        seed("home.artist.body",
                "Every piece starts as original ink on paper. No AI art. No stock assets. " +
                "Pure anime-inspired illustration — independent and intentional.",
                "Home — Artist section body text", "Home",   true);

        seed("home.cta.subtext",
                "Original artwork on premium clothing",
                "Home — Bottom CTA sub-text",      "Home",   false);

        seed("home.cta.heading",
                "Art Made by Hand.\nWorn by You.",
                "Home — Bottom CTA heading",       "Home",   true);

        seed("about.facts.age",
                "20",
                "About — Quick Facts: Age",         "About",  false);

        seed("about.facts.medium",
                "Ink on paper",
                "About — Quick Facts: Medium",      "About",  false);

        seed("about.facts.style",
                "Anime-inspired",
                "About — Quick Facts: Style",       "About",  false);

        seed("about.facts.founded",
                "Olly Threads, 2024",
                "About — Quick Facts: Founded",     "About",  false);

        seed("about.facts.fulfillment",
                "Print on demand",
                "About — Quick Facts: Fulfillment", "About",  false);

        seed("about.hero.eyebrow",
                "The Artist Behind the Brand",
                "About — Hero eyebrow label",      "About",  false);

        seed("about.story.p1",
                "It started young — a stack of manga volumes borrowed from a friend, a cheap sketchbook, " +
                "and an obsession with understanding how a single line could communicate weight, speed, and emotion. " +
                "Before he could drive, Oliver was filling notebooks trying to reverse-engineer the visual " +
                "language of the art he loved.",
                "About — Story paragraph 1",       "About",  true);

        seed("about.story.p2",
                "What began as copying panels evolved into studying composition — the geometry behind a " +
                "fight-scene freeze-frame, the way negative space builds tension, how ink pressure alone " +
                "changes a character's entire attitude. By his mid-teens, the style had stopped being " +
                "an imitation and started being something original.",
                "About — Story paragraph 2",       "About",  true);

        seed("about.quote",
                "I don't copy other people's characters. I draw characters that could exist in their worlds.",
                "About — Artist quote (blockquote)", "About", true);

        seed("about.why.p1",
                "The jump from sketchbook to apparel wasn't about money — it was about medium. A drawing lives " +
                "on a page or a screen. A garment lives in the world: on the subway, at a show, in the background " +
                "of someone else's photo. That kind of presence felt like a worthy challenge for the work.",
                "About — Why clothing paragraph 1", "About", true);

        seed("about.why.p2",
                "Olly Threads launched print-on-demand deliberately. No overstock, no waste, no warehouse. " +
                "Every unit is made because someone specifically wanted it — and that makes each piece " +
                "matter more than if there were a thousand of them sitting in a box somewhere.",
                "About — Why clothing paragraph 2", "About", true);

        seed("about.process.p1",
                "Every design starts on paper. Not a tablet — actual paper, with a physical pen that commits " +
                "to every line. The imperfection of that process is the point: the pressure variation, the " +
                "slight wobble, the decisions you can't perfectly undo. That's what makes it alive.",
                "About — Process paragraph 1",     "About",  true);

        seed("about.process.p2",
                "After scanning and cleanup, the artwork goes on premium-weight blanks through Printful. " +
                "Oliver reviews every mockup. If the line doesn't translate the same way on fabric, it " +
                "doesn't go to print.",
                "About — Process paragraph 2",     "About",  true);

        seed("about.cta.tagline",
                "Original artwork on premium clothing",
                "About — CTA tagline",             "About",  false);

        // ── Testimonials (editable from admin content editor) ─────────────────
        seed("testimonial_1_text",
                "\"Quality is unreal. Got the hoodie in 8 days and the art looks exactly like the original. " +
                "Nobody else is making anime clothing that feels this premium.\"",
                "Testimonial 1 — Quote",           "Home",   true);
        seed("testimonial_1_name",
                "Marcus T.",
                "Testimonial 1 — Customer name",   "Home",   false);
        seed("testimonial_1_source",
                "— Verified buyer, Shadow Hoodie",
                "Testimonial 1 — Source line",     "Home",   false);

        seed("testimonial_2_text",
                "\"Oliver is one of the few artists who actually puts real thought into every design. " +
                "This is not fast fashion — it's wearable art. Already on my third order.\"",
                "Testimonial 2 — Quote",           "Home",   true);
        seed("testimonial_2_name",
                "Priya S.",
                "Testimonial 2 — Customer name",   "Home",   false);
        seed("testimonial_2_source",
                "— Verified buyer, 3 orders",
                "Testimonial 2 — Source line",     "Home",   false);

        seed("testimonial_3_text",
                "\"Saw it on TikTok, bought it same night. The piece I got is literally a limited run — " +
                "they told me at checkout it's edition 12 of 50. That's crazy collectible.\"",
                "Testimonial 3 — Quote",           "Home",   true);
        seed("testimonial_3_name",
                "Jake R.",
                "Testimonial 3 — Customer name",   "Home",   false);
        seed("testimonial_3_source",
                "— Verified buyer, TikTok to store",
                "Testimonial 3 — Source line",     "Home",   false);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void seed(String key, String defaultValue, String label, String page, boolean multiline) {
        if (!repo.existsByContentKey(key)) {
            repo.save(new SiteContent(key, defaultValue, label, page, multiline));
        }
    }
}
