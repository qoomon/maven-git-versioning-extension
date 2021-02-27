package me.qoomon.gitversioning.commons;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StringUtil {

    public static String substituteText(String text, Map<String, String> replacements) {
        StringBuffer result = new StringBuffer();
        Pattern placeholderPattern = Pattern.compile("\\$\\{(?<key>[^}:]+)(?::(?<modifier>[-+])(?<value>[^}]*))?}");
        Matcher placeholderMatcher = placeholderPattern.matcher(text);
        while (placeholderMatcher.find()) {
            String placeholderKey = placeholderMatcher.group("key");
            String replacement = replacements.get(placeholderKey);
            String placeholderModifier = placeholderMatcher.group("modifier");
            if(placeholderModifier != null){
                if (placeholderModifier.equals("-") && replacement == null) {
                    replacement = placeholderMatcher.group("value");
                }
                if (placeholderModifier.equals("+") && replacement != null) {
                    replacement = placeholderMatcher.group("value");
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
     * @param text  to parse
     * @param regex pattern
     * @return a map of group-index and group-name to matching value
     */
    public static Map<String, String> valueGroupMap(String text, String regex) {
        Map<String, String> result = new HashMap<>();
        Pattern groupPattern = Pattern.compile(regex);
        Matcher groupMatcher = groupPattern.matcher(text);
        if (groupMatcher.find()) {
            // add group index to value entries
            for (int i = 1; i <= groupMatcher.groupCount(); i++) {
                result.put(String.valueOf(i), groupMatcher.group(i));
            }

            for (String groupName : patternGroupNames(groupPattern)) {
                result.put(groupName, groupMatcher.group(groupName));
            }
        }
        return result;
    }

    private static Set<String> patternGroupNames(Pattern groupPattern) {
        Set<String> groupNames = new HashSet<>();
        Pattern groupNamePattern = Pattern.compile("\\(\\?<(?<name>[a-zA-Z][a-zA-Z0-9]*)>");
        Matcher groupNameMatcher = groupNamePattern.matcher(groupPattern.toString());

        // add group name to value Entries
        while (groupNameMatcher.find()) {
            String groupName = groupNameMatcher.group("name");
            groupNames.add(groupName);
        }
        return groupNames;
    }
}
