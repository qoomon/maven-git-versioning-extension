package me.qoomon.gitversioning.commons;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class StringUtilTest {

    @Test
    void substituteText() {
        // Given
        String givenText = "${type}tale";
        Map<String, String> givenSubstitutionMap = new HashMap<>();
        givenSubstitutionMap.put("type", "fairy");

        // When
        String outputText = StringUtil.substituteText(givenText, givenSubstitutionMap);

        // Then
        assertThat(outputText).isEqualTo("fairytale");
    }

    @Test
    void substituteText_missingValue() {

        // Given
        String givenText = "${missing}tale";
        Map<String, String> givenSubstitutionMap = new HashMap<>();

        // When
        String outputText = StringUtil.substituteText(givenText, givenSubstitutionMap);

        // Then
        assertThat(outputText).isEqualTo("${missing}tale");
    }

    @Test
    void substituteText_escape_replacement_value() {

        // Given
        String givenText = "${version}";
        Map<String, String> givenSubstitutionMap = new HashMap<>();
        givenSubstitutionMap.put("version", "${something}");

        // When
        String outputText = StringUtil.substituteText(givenText, givenSubstitutionMap);

        // Then
        assertThat(outputText).isEqualTo("${something}");
    }


    @Test
    void valueGroupMap() {

        // Given
        String givenRegex = "(one) (two) (three)";
        String givenText = "one two three";

        // When
        Map<String, String> valueMap = StringUtil.valueGroupMap(givenText, givenRegex);

        // Then
        assertThat(valueMap).contains(entry("1", "one"), entry("2", "two"), entry("3", "three"));
    }

    @Test
    void valueGroupMap_nested() {

        // Given
        String givenRegex = "(one) (two (three))";
        String givenText = "one two three";

        // When
        Map<String, String> valueMap = StringUtil.valueGroupMap(givenText, givenRegex);

        // Then
        assertThat(valueMap).contains(entry("1", "one"), entry("2", "two three"), entry("3", "three"));
    }

    @Test
    void valueGroupMap_namedGroup() {

        // Given
        String givenRegex = "(?<first>one) (?<second>two) (?<third>three)";
        String givenText = "one two three";

        // When
        Map<String, String> valueMap = StringUtil.valueGroupMap(givenText, givenRegex);

        // Then
        assertThat(valueMap).contains(entry("1", "one"), entry("2", "two"), entry("3", "three"));
        assertThat(valueMap).contains(entry("first", "one"), entry("second", "two"), entry("third", "three"));
    }

    @Test
    void valueGroupMap_namedGroupNested() {

        // Given
        String givenRegex = "(?<first>one) (?<second>two (?<third>three))";
        String givenText = "one two three";

        // When
        Map<String, String> valueMap = StringUtil.valueGroupMap(givenText, givenRegex);

        // Then
        assertThat(valueMap).contains(entry("1", "one"), entry("2", "two three"), entry("3", "three"));
        assertThat(valueMap).contains(entry("first", "one"), entry("second", "two three"), entry("third", "three"));
    }
}
