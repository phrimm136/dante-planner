package org.danteplanner.backend.planner.validation;

import com.networknt.schema.regex.RegularExpression;
import com.networknt.schema.regex.RegularExpressionFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class EcmaAnchoredRegexFactory implements RegularExpressionFactory {

    private static final Pattern TRAILING_DOLLAR = Pattern.compile("(?<!\\\\)((?:\\\\\\\\)*)\\$\\z");
    private static final String END_OF_INPUT = "$1" + Matcher.quoteReplacement("\\z");

    @Override
    public RegularExpression getRegularExpression(String regex) {
        Pattern compiled = Pattern.compile(rewrite(regex));
        return value -> compiled.matcher(value).find();
    }

    static String rewrite(String regex) {
        return TRAILING_DOLLAR.matcher(regex).replaceFirst(END_OF_INPUT);
    }
}
