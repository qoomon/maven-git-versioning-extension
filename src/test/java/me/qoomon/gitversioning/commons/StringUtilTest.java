package me.qoomon.gitversioning.commons;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class StringUtilTest {

    @Test
    void substituteText() {
        // Given
        String givenText = "${type}tale";
        Map<String, Supplier<String>> givenSubstitutionMap = new HashMap<>();
        givenSubstitutionMap.put("type", () -> "fairy");

        // When
        String outputText = StringUtil.substituteText(givenText, givenSubstitutionMap);

        // Then
        assertThat(outputText).isEqualTo("fairytale");
    }

    @Test
    void substituteText_missingValue() {

        // Given
        String givenText = "${missing}tale";
        Map<String, Supplier<String>> givenSubstitutionMap = new HashMap<>();

        // When
        String outputText = StringUtil.substituteText(givenText, givenSubstitutionMap);

        // Then
        assertThat(outputText).isEqualTo("${missing}tale");
    }

    @Test
    void substituteText_handle_replacement_value_with_placeholder_syntax() {

        // Given
        String givenText = "${version}";
        Map<String, Supplier<String>> givenSubstitutionMap = new HashMap<>();
        givenSubstitutionMap.put("version", () -> "${something}");

        // When
        String outputText = StringUtil.substituteText(givenText, givenSubstitutionMap);

        // Then
        assertThat(outputText).isEqualTo("${something}");
    }

    @Test
    void substituteText_default_value() {

        // Given
        String givenText = "${foo:-xxx}";
        Map<String, Supplier<String>> givenSubstitutionMap = emptyMap();

        // When
        String outputText = StringUtil.substituteText(givenText, givenSubstitutionMap);

        // Then
        assertThat(outputText).isEqualTo("xxx");
    }

    @Test
    void substituteText_overwrite_value() {

        // Given
        String givenText = "${foo:+xxx}";
        Map<String, Supplier<String>> givenSubstitutionMap = new HashMap<>();
        givenSubstitutionMap.put("foo", () -> "aaa");

        // When
        String outputText = StringUtil.substituteText(givenText, givenSubstitutionMap);

        // Then
        assertThat(outputText).isEqualTo("xxx");
    }

    @Test
    void substituteText_function_combination_slug_uppercase() {

        // Given
        String givenText = "${foo:+a/b:slug:uppercase}";
        Map<String, Supplier<String>> givenSubstitutionMap = new HashMap<>();
        givenSubstitutionMap.put("foo", () -> "aaa");

        // When
        String outputText = StringUtil.substituteText(givenText, givenSubstitutionMap);

        // Then
        assertThat(outputText).isEqualTo("A-B");
    }

    @Test
    void substituteText_function_word_lowercase() {

        // Given
        String givenText = "${foo:word:lowercase}";
        Map<String, Supplier<String>> givenSubstitutionMap = new HashMap<>();
        givenSubstitutionMap.put("foo", () -> "PR-56+/ii");

        // When
        String outputText = StringUtil.substituteText(givenText, givenSubstitutionMap);

        // Then
        assertThat(outputText).isEqualTo("pr_56_ii");
    }

    @Test
    void substituteText_function_slug() {

        // Given
        String givenText = "${foo:slug}";
        Map<String, Supplier<String>> givenSubstitutionMap = new HashMap<>();
        givenSubstitutionMap.put("foo", () -> "PR-56+/ii_7");

        // When
        String outputText = StringUtil.substituteText(givenText, givenSubstitutionMap);

        // Then
        assertThat(outputText).isEqualTo("PR-56-ii_7");
    }

    @Test
    void substituteText_function_slug_dot() {

        // Given
        String givenText = "${foo:slug+dot}";
        Map<String, Supplier<String>> givenSubstitutionMap = new HashMap<>();
        givenSubstitutionMap.put("foo", () -> "my-release/2.5");

        // When
        String outputText = StringUtil.substituteText(givenText, givenSubstitutionMap);

        // Then
        assertThat(outputText).isEqualTo("my-release-2.5");
    }

    @Test
    void substituteText_function_slug_hyphen() {

        // Given
        String givenText = "${foo:slug+hyphen}";
        Map<String, Supplier<String>> givenSubstitutionMap = new HashMap<>();
        givenSubstitutionMap.put("foo", () -> "my_release/2.5");

        // When
        String outputText = StringUtil.substituteText(givenText, givenSubstitutionMap);

        // Then
        assertThat(outputText).isEqualTo("my-release-2-5");
    }

    @Test
    void substituteText_function_slug_hyphen_dot() {

        // Given
        String givenText = "${foo:slug+hyphen+dot}";
        Map<String, Supplier<String>> givenSubstitutionMap = new HashMap<>();
        givenSubstitutionMap.put("foo", () -> "my_release/2.5");

        // When
        String outputText = StringUtil.substituteText(givenText, givenSubstitutionMap);

        // Then
        assertThat(outputText).isEqualTo("my-release-2.5");
    }

    @Test
    void substituteText_function_word_dot() {

        // Given
        String givenText = "${foo:word+dot}";
        Map<String, Supplier<String>> givenSubstitutionMap = new HashMap<>();
        givenSubstitutionMap.put("foo", () -> "release/2.5");

        // When
        String outputText = StringUtil.substituteText(givenText, givenSubstitutionMap);

        // Then
        assertThat(outputText).isEqualTo("release_2.5");
    }

    @Test
    void substituteText_function_next() {

        // Given
        String givenText = "${foo:next}";
        Map<String, Supplier<String>> givenSubstitutionMap = new HashMap<>();
        givenSubstitutionMap.put("foo", () -> "alpha.56-rc.12");

        // When
        String outputText = StringUtil.substituteText(givenText, givenSubstitutionMap);

        // Then
        assertThat(outputText).isEqualTo("alpha.56-rc.13");
    }

    @Test
    void substituteText_function_next_without_number_adds_dot_1() {

        // Given
        String givenText = "${foo:next}";
        Map<String, Supplier<String>> givenSubstitutionMap = new HashMap<>();
        givenSubstitutionMap.put("foo", () -> "alpha.56-rc.12-abc");

        // When
        String outputText = StringUtil.substituteText(givenText, givenSubstitutionMap);

        // Then
        assertThat(outputText).isEqualTo("alpha.56-rc.12-abc.1");
    }

    @Test
    void substituteText_function_incrementlast() {

        // Given
        String givenText = "${foo:incrementlast}";
        Map<String, Supplier<String>> givenSubstitutionMap = new HashMap<>();
        givenSubstitutionMap.put("foo", () -> "alpha.56-rc.12");

        // When
        String outputText = StringUtil.substituteText(givenText, givenSubstitutionMap);

        // Then
        assertThat(outputText).isEqualTo("alpha.56-rc.13");
    }

    @Test
    void substituteText_function_incrementlast_without_number_does_nothing() {

        // Given
        String givenText = "${foo:incrementlast}";
        Map<String, Supplier<String>> givenSubstitutionMap = new HashMap<>();
        givenSubstitutionMap.put("foo", () -> "alpha");

        // When
        String outputText = StringUtil.substituteText(givenText, givenSubstitutionMap);

        // Then
        assertThat(outputText).isEqualTo("alpha");
    }

    @Test
    void substituteText_function_incrementlast_without_number_at_end() {

        // Given
        String givenText = "${foo:incrementlast}";
        Map<String, Supplier<String>> givenSubstitutionMap = new HashMap<>();
        givenSubstitutionMap.put("foo", () -> "alpha.9-special");

        // When
        String outputText = StringUtil.substituteText(givenText, givenSubstitutionMap);

        // Then
        assertThat(outputText).isEqualTo("alpha.10-special");
    }

    @Test
    void substituteText_function_value_escaping() {

        // Given
        String givenText = "${foo:+word::lowercase}";
        Map<String, Supplier<String>> givenSubstitutionMap = new HashMap<>();
        givenSubstitutionMap.put("foo", () -> "PR-56+/ii");

        // When
        String outputText = StringUtil.substituteText(givenText, givenSubstitutionMap);

        // Then
        assertThat(outputText).isEqualTo("word:lowercase");
    }

    @Test
    void substituteText_function_value_escaping_with_function() {

        // Given
        String givenText = "${foo:+WORD:::lowercase}";
        Map<String, Supplier<String>> givenSubstitutionMap = new HashMap<>();
        givenSubstitutionMap.put("foo", () -> "word");

        // When
        String outputText = StringUtil.substituteText(givenText, givenSubstitutionMap);

        // Then
        assertThat(outputText).isEqualTo("word:");
    }


    @Test
    void valueGroupMap() {

        // Given
        Pattern givenRegex = Pattern.compile("(one) (two) (three)");
        String givenText = "one two three";

        // When
        Map<String, String> valueMap = StringUtil.patternGroupValues(givenRegex, givenText);

        // Then
        assertThat(valueMap).contains(entry("1", "one"), entry("2", "two"), entry("3", "three"));
    }

    @Test
    void valueGroupMap_nested() {

        // Given
        Pattern givenRegex = Pattern.compile("(one) (two (three))");
        String givenText = "one two three";

        // When
        Map<String, String> valueMap = StringUtil.patternGroupValues(givenRegex, givenText);

        // Then
        assertThat(valueMap).contains(entry("1", "one"), entry("2", "two three"), entry("3", "three"));
    }

    @Test
    void valueGroupMap_namedGroup() {

        // Given
        Pattern givenRegex = Pattern.compile("(?<first>one) (?<second>two) (?<third>three)");
        String givenText = "one two three";

        // When
        Map<String, String> valueMap = StringUtil.patternGroupValues(givenRegex, givenText);

        // Then
        assertThat(valueMap).contains(entry("1", "one"), entry("2", "two"), entry("3", "three"));
        assertThat(valueMap).contains(entry("first", "one"), entry("second", "two"), entry("third", "three"));
    }

    @Test
    void valueGroupMap_namedGroupNested() {

        // Given
        Pattern givenRegex = Pattern.compile("(?<first>one) (?<second>two (?<third>three))");
        String givenText = "one two three";

        // When
        Map<String, String> valueMap = StringUtil.patternGroupValues(givenRegex, givenText);

        // Then
        assertThat(valueMap).contains(entry("1", "one"), entry("2", "two three"), entry("3", "three"));
        assertThat(valueMap).contains(entry("first", "one"), entry("second", "two three"), entry("third", "three"));
    }
}
