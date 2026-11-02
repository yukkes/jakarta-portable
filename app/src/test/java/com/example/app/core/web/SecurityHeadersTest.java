package com.example.app.core.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SecurityHeadersTest {

    @Test
    void cspForbidsInlineScriptAndStyle() {
        String csp = SecurityHeaders.CONTENT_SECURITY_POLICY;

        assertTrue(csp.contains("script-src 'self'"));
        assertTrue(csp.contains("style-src 'self'"));
        // Every UI script and style is an external file, so 'unsafe-inline' is not needed.
        assertFalse(csp.contains("unsafe-inline"));
        assertFalse(csp.contains("unsafe-eval"));
        assertTrue(csp.contains("frame-ancestors 'none'"));
        assertTrue(csp.contains("base-uri 'none'"));
        assertTrue(csp.contains("form-action 'self'"));
    }

    @Test
    void responseHeadersCoverTheThreeBaselineHeaders() {
        Map<String, String> headers = SecurityHeaders.responseHeaders();

        assertEquals(SecurityHeaders.CONTENT_SECURITY_POLICY, headers.get("Content-Security-Policy"));
        assertEquals("nosniff", headers.get("X-Content-Type-Options"));
        assertEquals("no-referrer", headers.get("Referrer-Policy"));
        assertEquals(3, headers.size());
    }

    /**
     * Payara and GlassFish add no headers to static files, so the pages declare the policy in {@code <meta>}.
     * That value must equal the header policy minus frame-ancestors (ignored in meta; frame-guard.js
     * covers it instead).
     */
    @ParameterizedTest
    @ValueSource(strings = {"index.html", "login.html"})
    void pagesDeclareTheHeaderPolicyInMetaTags(String page) throws IOException {
        String html = Files.readString(Path.of("src/main/webapp", page), StandardCharsets.UTF_8);

        String expected = Arrays.stream(SecurityHeaders.CONTENT_SECURITY_POLICY.split(";"))
                .map(String::trim)
                .filter(directive -> !directive.startsWith("frame-ancestors"))
                .collect(Collectors.joining("; "));
        assertEquals(expected, metaContent(html, "http-equiv=\"Content-Security-Policy\""));
        assertEquals(SecurityHeaders.REFERRER_POLICY, metaContent(html, "name=\"referrer\""));

        // The frame guard must run before anything renders, so it is the first script in the document.
        int guard = html.indexOf("<script src=\"frame-guard.js\"></script>");
        assertTrue(guard >= 0 && guard < html.indexOf("</head>"), "frame-guard.js is not in <head>");
        assertEquals(guard, html.indexOf("<script"), "frame-guard.js is not the first script");
    }

    private static String metaContent(String html, String attribute) {
        Matcher meta = Pattern.compile("<meta " + Pattern.quote(attribute) + " content=\"([^\"]*)\">")
                .matcher(html);
        assertTrue(meta.find(), "no <meta " + attribute + "> in the page");
        return meta.group(1);
    }
}
