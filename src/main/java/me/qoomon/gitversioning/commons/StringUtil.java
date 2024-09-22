package me.qoomon.gitversioning.commons;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class StringUtil {

    private static final Pattern END_NUMBERS = Pattern.compile("\\d+$");
    private static final Pattern LAST_NUMBERS = Pattern.compile("(\\d+)(?=\\D*$)");
    private static final Pattern PLACEHOLDER_PATTERN;
    private static final Map<String, UnaryOperator<String>> FUNCTIONS;

    static {
        final Map<String, UnaryOperator<String>> functions = new HashMap<>();
        functions.put("slug", str -> str.replaceAll("[^\\w-]+", "-").replaceAll("-{2,}", "-"));
        functions.put("slug+dot", str -> str.replaceAll("[^\\w.-]+", "-").replaceAll("-{2,}", "-"));
        functions.put("next", StringUtil::next);
        functions.put("incrementlast", StringUtil::incrementLast);
        functions.put("uppercase", str -> str.toUpperCase(Locale.ROOT));
        functions.put("lowercase", str -> str.toLowerCase(Locale.ROOT));
        functions.put("word", str -> str.replaceAll("\\W+", "_").replaceAll("_{2,}", "_"));
        functions.put("word+dot", str -> str.replaceAll("[^\\w.]+", "_").replaceAll("_{2,}", "_"));
        FUNCTIONS = functions.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                e -> str -> str == null ? null : e.getValue().apply(str))
        );
        final String functionsAlternatives = FUNCTIONS.keySet().stream().map(Pattern::quote).collect(Collectors.joining("|:", "(?<functions>(?::", ")+)?"));
        PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{(?<key>[^}:]+)(?::(?<modifier>[-+])(?<value>(?:::|[^:}])*))?" + functionsAlternatives + "}");
    }

    public static String substituteText(String text, Map<String, Supplier<String>> replacements) {
        StringBuffer result = new StringBuffer();
        Matcher placeholderMatcher = PLACEHOLDER_PATTERN.matcher(text);
        while (placeholderMatcher.find()) {
            String placeholderKey = placeholderMatcher.group("key");
            Supplier<String> replacementSupplier = replacements.get(placeholderKey);
            String replacement = replacementSupplier != null ? replacementSupplier.get() : null;
            String placeholderModifier = placeholderMatcher.group("modifier");
            if (placeholderModifier != null) {
                if (placeholderModifier.equals("-") && replacement == null) {
                    replacement = placeholderMatcher.group("value").replace("::", ":");
                }
                if (placeholderModifier.equals("+") && replacement != null) {
                    replacement = placeholderMatcher.group("value").replace("::", ":");
                }
            }
            String functionNames = placeholderMatcher.group("functions");
            if (functionNames != null) {
                for (String functionName : functionNames.substring(1).split(":")) {
                    replacement = FUNCTIONS.get(functionName).apply(replacement);
                }
            }
            if (replacement != null) {
                // avoid group name replacement behaviour of replacement parameter value
                placeholderMatcher.appendReplacement(result, "");
                result.append(replacement);
            }
        }
        placeholderMatcher.appendTail(result);
        return result.toString();
    }

    /**
     * @param pattern pattern
     * @param text    to parse
     * @return a map of group-index and group-name to matching value
     */
    public static Map<String, String> patternGroupValues(Pattern pattern, String text) {
        Map<String, String> result = new HashMap<>();

        Matcher groupMatcher = pattern.matcher(text);
        if (groupMatcher.find()) {
            // group index values
            for (int i = 1; i <= groupMatcher.groupCount(); i++) {
                result.put(String.valueOf(i), groupMatcher.group(i));
            }
            // group name values
            for (String groupName : patternGroupNames(pattern)) {
                result.put(groupName, groupMatcher.group(groupName));
            }
        }

        return result;
    }

    public static Set<String> patternGroups(Pattern pattern) {
        Set<String> groups = new HashSet<>();

        // group indexes
        for (int groupIndex = 1; groupIndex <= patternGroupCount(pattern); groupIndex++) {
            groups.add(String.valueOf(groupIndex));
        }
        // group names
        groups.addAll(patternGroupNames(pattern));

        return groups;
    }

    public static int patternGroupCount(Pattern pattern) {
        return pattern.matcher("").groupCount();
    }

    public static Set<String> patternGroupNames(Pattern pattern) {
        Set<String> groups = new HashSet<>();

        // group names
        Pattern groupNamePattern = Pattern.compile("\\(\\?<(?<name>[a-zA-Z][a-zA-Z0-9]*)>");
        Matcher groupNameMatcher = groupNamePattern.matcher(pattern.toString());
        while (groupNameMatcher.find()) {
            String groupName = groupNameMatcher.group("name");
            groups.add(groupName);
        }

        return groups;
    }

    public static String next(String value) {
        final Matcher matcher = END_NUMBERS.matcher(value);
        if (matcher.find()) {
            return matcher.replaceAll(matchResult -> String.valueOf(Long.parseLong(matchResult.group()) + 1L));
        }
        return value + ".1";
    }

    public static String incrementLast(String value) {
        final Matcher matcher = LAST_NUMBERS.matcher(value);
        if (matcher.find()) {
            return matcher.replaceFirst(matchResult -> String.valueOf(Long.parseLong(matchResult.group()) + 1L));
        }
        return value;
    }

    private StringUtil() {
    }
}
