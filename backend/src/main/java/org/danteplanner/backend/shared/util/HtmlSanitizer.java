package org.danteplanner.backend.shared.util;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

public final class HtmlSanitizer {

    private HtmlSanitizer() {
    }

    public static String sanitize(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }

        // Safelist.none() removes all HTML tags, keeping only text content.
        return Jsoup.clean(input, Safelist.none());
    }
}
