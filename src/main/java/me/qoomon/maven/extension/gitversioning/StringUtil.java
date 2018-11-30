package me.qoomon.maven.extension.gitversioning;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StringUtil {

    public static String substituteText(String text, Map<String, String> substitutionMap) {
        String result = text;

        final Pattern placeholderPattern = Pattern.compile("\\$\\{(.+?)}");
        final Matcher placeholderMatcher = placeholderPattern.matcher(text);
        while (placeholderMatcher.find()) {
            String substitutionKey = placeholderMatcher.group(1);
            String substitutionValue = substitutionMap.get(substitutionKey);
            result = result.replaceAll("\\$\\{" + substitutionKey + "}", substitutionValue);
        }

        return result;
    }

    public static String removePrefix(String string, String prefix) {

        String prefixRegex = prefix;
        if(!prefix.startsWith("$")){
            prefixRegex = "$" + Pattern.quote(prefix);
        }

        return string.replaceFirst(prefixRegex, "");
    }

    /**
     * @param regex pattern
     * @param text to parse
     * @return a map of group-index and group-name to matching value
     */
    public static Map<String, String> getRegexGroupValueMap(String regex, String text) {
        Map<String, String> result = new HashMap<>();
        Pattern groupPattern = Pattern.compile(regex);
        Matcher groupMatcher = groupPattern.matcher(text);
        if (groupMatcher.find()) {
            // add group index to value entries
            for (int i = 0; i <= groupMatcher.groupCount(); i++) {
                result.put(String.valueOf(i), groupMatcher.group(i));
            }

            // determine group names
            Pattern groupNamePattern = Pattern.compile("\\(\\?<(?<name>[a-zA-Z][a-zA-Z0-9]*)>");
            Matcher groupNameMatcher = groupNamePattern.matcher(groupPattern.toString());

            // add group name to value Entries
            while (groupNameMatcher.find()) {
                String groupName = groupNameMatcher.group("name");
                result.put(groupName, groupMatcher.group(groupName));
            }
        }
        return result;
    }
}
