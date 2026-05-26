package me.qoomon.maven.gitversioning;

import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;

import static org.assertj.core.api.Assertions.assertThat;

class GitVersioningModelProcessorTest {

    @Test
    void versionPattern_matchesFourSegmentTag() {
        Matcher m = match("1.0.0.3");
        assertThat(m.group("version")).isEqualTo("1.0.0.3");
        assertThat(m.group("core")).isEqualTo("1.0.0");
        assertThat(m.group("major")).isEqualTo("1");
        assertThat(m.group("minor")).isEqualTo("0");
        assertThat(m.group("patch")).isEqualTo("0");
        assertThat(m.group("build")).isEqualTo("3");
        assertThat(m.group("label")).isNull();
    }

    @Test
    void versionPattern_matchesThreeSegmentTag_buildIsNull() {
        Matcher m = match("1.0.0");
        assertThat(m.group("version")).isEqualTo("1.0.0");
        assertThat(m.group("core")).isEqualTo("1.0.0");
        assertThat(m.group("build")).isNull();
        assertThat(m.group("label")).isNull();
    }

    @Test
    void versionPattern_matchesFourSegmentWithLabel() {
        Matcher m = match("1.2.3.4-RC1");
        assertThat(m.group("version")).isEqualTo("1.2.3.4-RC1");
        assertThat(m.group("core")).isEqualTo("1.2.3");
        assertThat(m.group("build")).isEqualTo("4");
        assertThat(m.group("label")).isEqualTo("RC1");
    }

    @Test
    void versionPattern_matchesThreeSegmentWithLabel_buildStillNull() {
        Matcher m = match("1.2.3-alpha");
        assertThat(m.group("version")).isEqualTo("1.2.3-alpha");
        assertThat(m.group("core")).isEqualTo("1.2.3");
        assertThat(m.group("build")).isNull();
        assertThat(m.group("label")).isEqualTo("alpha");
    }

    @Test
    void nextVersion_threeSegment() {
        assertNextVersion("1.2.3", "1.2.4");
    }

    @Test
    void nextVersion_fourSegment() {
        assertNextVersion("1.2.3.4", "1.2.3.5");
    }

    @Test
    void nextVersion_twoSegment() {
        assertNextVersion("1.2", "1.3");
    }

    @Test
    void nextVersion_singleSegment() {
        assertNextVersion("1", "2");
    }

    @Test
    void nextVersion_labelWithTrailingNum() {
        assertNextVersion("1.2.3-RC1", "1.2.3-RC2");
    }

    @Test
    void nextVersion_labelWithMultiDigitNum() {
        assertNextVersion("1.2.3-RC10", "1.2.3-RC11");
    }

    @Test
    void nextVersion_labelNoTrailingNum() {
        assertNextVersion("1.2.3-alpha", "1.2.3-alpha1");
    }

    @Test
    void nextVersion_snapshotLabel() {
        assertNextVersion("1.2.3-SNAPSHOT", "1.2.3-SNAPSHOT1");
    }

    @Test
    void nextVersion_fourSegmentWithLabel() {
        assertNextVersion("1.2.3.4-RC1", "1.2.3.4-RC2");
    }

    @Test
    void nextVersion_onlyTrailingDigitsInLabelAreBumped() {
        assertNextVersion("1.2.3-RC1-final", "1.2.3-RC1-final1");
    }

    private static void assertNextVersion(String input, String expected) {
        assertThat(GitVersioningModelProcessor.nextVersion(match(input)))
                .as("nextVersion(%s)", input)
                .isEqualTo(expected);
    }

    private static Matcher match(String input) {
        Matcher m = GitVersioningModelProcessor.VERSION_PATTERN.matcher(input);
        m.find();
        return m;
    }
}
