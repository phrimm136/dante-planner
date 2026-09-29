package org.danteplanner.backend.planner.validation;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record EncodedGiftId(int enhancement, GiftId base) {

    private static final Pattern ENHANCED = Pattern.compile("^([12]?)(9\\d{3})$");
    private static final int UNENHANCED = 0;

    public static EncodedGiftId parse(String encoded) {
        Matcher matcher = ENHANCED.matcher(encoded);
        return matcher.matches()
                ? new EncodedGiftId(enhancementOf(matcher.group(1)), new GiftId(matcher.group(2)))
                : new EncodedGiftId(UNENHANCED, new GiftId(encoded));
    }

    private static int enhancementOf(String prefix) {
        return prefix.isEmpty() ? UNENHANCED : Integer.parseInt(prefix);
    }
}
