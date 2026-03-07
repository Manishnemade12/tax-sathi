package com.taxsathi.service;

import com.taxsathi.dto.TaxBuddyRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * TaxBuddyService — AI-powered ITR strategy generation using Groq LLM.
 *
 * Flow:
 *  1. Determine the correct ITR form based on user's profile (pure Java logic)
 *  2. Build smart filing alerts based on profile details
 *  3. Compose a structured prompt and call the Groq chat completion API
 *  4. Sanitize the AI response (strip markdown artifacts)
 *  5. Return a structured response with strategy text + ITR form + alerts
 *
 * Mirrors Go's TaxBuddyHandler and all helper functions (determineITR, buildAlerts,
 * buildContextSummary, sanitizeTaxBuddyOutput, callGroq).
 */
@Service
public class TaxBuddyService {

    private static final Logger log = LoggerFactory.getLogger(TaxBuddyService.class);
    private static final String GROQ_CHAT_URL = "https://api.groq.com/openai/v1/chat/completions";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${groq.api-key}")
    private String groqApiKey;

    @Value("${groq.model}")
    private String groqModel;

    public TaxBuddyService(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Generates a personalized TaxBuddy strategy.
     * Returns a map containing: strategy, itr_form, primary_reason, smart_alerts
     */
    public Map<String, Object> generateStrategy(TaxBuddyRequest req) {
        if (req.getAge() <= 0) throw new IllegalArgumentException("age is required");
        if (req.getResStatus() == null || req.getResStatus().isBlank())
            throw new IllegalArgumentException("residential status is required");

        ItrVerdict verdict = determineITR(req);
        String alerts = buildAlerts(req);
        String contextSummary = buildContextSummary(req);

        if (groqApiKey == null || groqApiKey.isBlank()) {
            throw new IllegalArgumentException("GROQ_API_KEY is not configured. Please add it to your .env file.");
        }

        String systemPrompt = """
                Role: You are "TaxBuddy," an intelligent, empathetic, and witty AI Tax Assistant helping with Indian taxes for FY 2025-26.

                Core directives:
                1) Context First: Start by acknowledging backend-provided facts.
                2) The Why Behind ITR: Explain specific reason for ITR choice.
                3) One Step at a Time: Ask exactly ONE follow-up question.
                4) Be Proactive:
                   - If senior citizen (60+), mention Section 80TTB deduction of ₹50,000.
                   - If agriculture income exists, explain Partial Integration simply.

                Output rules:
                - Return plain text only (no markdown symbols like ###, *, **, -, backticks).
                - Keep this exact readable structure:
                \tYour Personalized Tax Strategy
                \tThe Verdict: ...
                \tBig Wins: ...
                \tSmart Alerts: ...

                \tLet's Get Started
                \t... exactly one follow-up question ...

                \tDisclaimer: I provide AI-guided strategy for informational purposes. Please verify final filings with a CA.""";

        String userPrompt = String.format("""
                Use this backend context (do not override these facts):
                %s

                Backend verdict:
                - ITR form: %s
                - Primary reason: %s
                - Smart alerts: %s

                Now generate the final user-facing strategy in the exact required format.""",
                contextSummary, verdict.form(), verdict.reason(), alerts);

        String rawStrategy = callGroq(systemPrompt, userPrompt);
        String strategy = sanitizeOutput(rawStrategy);

        return Map.of(
                "success", true,
                "data", Map.of(
                        "strategy", strategy,
                        "itr_form", verdict.form(),
                        "primary_reason", verdict.reason(),
                        "smart_alerts", alerts
                )
        );
    }

    // ─── ITR Form determination ─────────────────────────────────────────────

    private ItrVerdict determineITR(TaxBuddyRequest req) {
        if (req.isHasBusiness())
            return new ItrVerdict("ITR-3",
                    "Since you have business/freelancing income, you need a return meant for business/professional income.");
        if (req.isIsDirector())
            return new ItrVerdict("ITR-2",
                    "Since you are a company director, you need the more detailed ITR form that captures director disclosures.");
        if (req.isHasCapGains())
            return new ItrVerdict("ITR-2",
                    "Since you reported capital gains from stocks/mutual funds/property, ITR-1 is not applicable.");
        if ("NRI".equalsIgnoreCase(req.getResStatus()) || "RNOR".equalsIgnoreCase(req.getResStatus()))
            return new ItrVerdict("ITR-2",
                    "Since your residential status is not resident, you need ITR-2 for the required disclosures.");
        if (req.getEstIncome() > 5_000_000)
            return new ItrVerdict("ITR-2",
                    "Since estimated annual income is above ₹50L, the law requires a more detailed return.");
        return new ItrVerdict("ITR-1",
                "Based on your current profile, basic resident individual filing conditions fit ITR-1.");
    }

    private String buildAlerts(TaxBuddyRequest req) {
        List<String> alerts = new ArrayList<>();
        if (req.getEstIncome() > 5_000_000)
            alerts.add("Schedule AL is typically required because income exceeds ₹50L");
        if (req.getAge() >= 60)
            alerts.add("Senior citizen: check Section 80TTB benefit up to ₹50,000 on eligible interest");
        if (req.isHasAgri())
            alerts.add("Agricultural income may trigger partial integration for rate calculation");
        return alerts.isEmpty()
                ? "No high-risk filing alert detected from current answers"
                : String.join("; ", alerts);
    }

    private String buildContextSummary(TaxBuddyRequest req) {
        return String.format("""
                - Age: %d
                - Residential status: %s
                - Has business/freelancing income: %b
                - Has capital gains: %b
                - Estimated annual income: ₹%.0f
                - Has agriculture income: %b
                - Director in a company: %b""",
                req.getAge(), req.getResStatus(), req.isHasBusiness(),
                req.isHasCapGains(), req.getEstIncome(), req.isHasAgri(), req.isIsDirector());
    }

    // ─── Groq API call ──────────────────────────────────────────────────────

    private String callGroq(String systemPrompt, String userPrompt) {
        Map<String, Object> payload = Map.of(
                "model", groqModel,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                ),
                "temperature", 0.5,
                "max_tokens", 1200
        );

        String jsonBody;
        try {
            jsonBody = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize Groq request", e);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GROQ_CHAT_URL))
                .header("Authorization", "Bearer " + groqApiKey.trim())
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Groq API call failed: " + e.getMessage(), e);
        }

        if (response.statusCode() == 401) {
            throw new IllegalArgumentException("Invalid or expired GROQ_API_KEY. Please check your .env file.");
        }
        if (response.statusCode() == 429) {
            throw new IllegalArgumentException("Groq quota exceeded for model " + groqModel + ". Please check your billing.");
        }
        if (response.statusCode() >= 400) {
            throw new RuntimeException("Groq API Error (" + response.statusCode() + "): " + response.body());
        }

        try {
            JsonNode parsed = objectMapper.readTree(response.body());
            JsonNode choices = parsed.path("choices");
            if (choices.isEmpty() || choices.get(0).path("message").path("content").asText().isBlank()) {
                throw new RuntimeException("Empty Groq response for model " + groqModel);
            }
            return choices.get(0).path("message").path("content").asText().trim();
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse Groq response", e);
        }
    }

    // ─── Output sanitizer ───────────────────────────────────────────────────

    private String sanitizeOutput(String raw) {
        String text = raw.strip();

        // Replace known markdown heading/formatting patterns
        Map<String, String> replacements = Map.of(
                "### 📝 Your Personalized Tax Strategy", "Your Personalized Tax Strategy",
                "### 💬 Let's Get Started", "Let's Get Started",
                "**Disclaimer:**", "Disclaimer:",
                "**The Verdict:**", "The Verdict:",
                "**Big Wins:**", "Big Wins:",
                "**Smart Alerts:**", "Smart Alerts:",
                "---", ""
        );
        for (Map.Entry<String, String> e : replacements.entrySet()) {
            text = text.replace(e.getKey(), e.getValue());
        }

        // Strip leading * bullets and collapse blank lines
        String[] lines = text.split("\n");
        List<String> cleaned = new ArrayList<>();
        boolean seenTitle = false;
        for (String line : lines) {
            line = line.strip().replaceAll("^\\*", "").strip();
            if (line.isEmpty()) {
                if (!cleaned.isEmpty() && !cleaned.get(cleaned.size() - 1).isEmpty()) {
                    cleaned.add("");
                }
                continue;
            }
            if ("Your Personalized Tax Strategy".equalsIgnoreCase(line)) {
                if (seenTitle) continue;
                seenTitle = true;
            }
            cleaned.add(line);
        }
        // Remove trailing blank
        while (!cleaned.isEmpty() && cleaned.get(cleaned.size() - 1).isEmpty()) {
            cleaned.remove(cleaned.size() - 1);
        }
        return String.join("\n", cleaned);
    }

    // ─── Value object ────────────────────────────────────────────────────────

    private record ItrVerdict(String form, String reason) {}
}
