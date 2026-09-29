package org.danteplanner.backend.planner.validation.sinner;

import org.danteplanner.backend.planner.validation.ContentPath;
import org.danteplanner.backend.planner.validation.FieldResult;

import java.math.BigInteger;
import java.util.Optional;
import java.util.regex.Pattern;

public record SinnerKey(String raw, int sinner) {

    static final int FIRST_SINNER = 1;
    static final int LAST_SINNER = 12;

    private static final Pattern SINNER_NUMBER = Pattern.compile("[+-]?[0-9]+");
    private static final BigInteger FIRST = BigInteger.valueOf(FIRST_SINNER);
    private static final BigInteger LAST = BigInteger.valueOf(LAST_SINNER);

    public static FieldResult<Optional<SinnerKey>> parse(String raw, ContentPath containerPath) {
        return number(raw)
                .map(number -> inRange(number)
                        ? FieldResult.of(Optional.of(new SinnerKey(raw, number.intValue())))
                        : FieldResult.<Optional<SinnerKey>>rejected(Optional.empty(),
                                SinnerViolations.keyOutOfRange(containerPath, raw, number)))
                .orElseGet(() -> FieldResult.rejected(Optional.empty(),
                        SinnerViolations.keyNotANumber(containerPath, raw)));
    }

    public String padded() {
        return paddedOf(sinner);
    }

    static String paddedOf(int sinner) {
        return String.format("%02d", sinner);
    }

    private static Optional<BigInteger> number(String raw) {
        return SINNER_NUMBER.matcher(raw).matches() ? Optional.of(new BigInteger(raw)) : Optional.empty();
    }

    private static boolean inRange(BigInteger number) {
        return number.compareTo(FIRST) >= 0 && number.compareTo(LAST) <= 0;
    }
}
