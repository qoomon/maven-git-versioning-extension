package me.qoomon.maven.gitversioning;

import me.qoomon.maven.gitversioning.LogStyle.Style;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LogStyleTest {

    private static final String SHARED_UTILS_PACKAGE = "org.apache.maven.shared.utils.";
    private static final String JLINE_PACKAGE = "org.apache.maven.jline.";

    @Test
    void mavenMessageBuilder() {
        // Given (maven-shared-utils is on the test classpath via maven-core)
        LogStyle logStyle = new LogStyle(LogStyleTest.class.getClassLoader());

        // When
        String styled = logStyle.style(Style.MOJO, "text");

        // Then
        assertThat(logStyle.usesMavenMessageBuilder()).isTrue();
        assertThat(styled).contains("text");
    }

    @Test
    void fallback_noMavenApiAvailable() {
        // Given
        LogStyle logStyle = new LogStyle(hidingClassLoader(SHARED_UTILS_PACKAGE, JLINE_PACKAGE));

        // When
        String styled = logStyle.style(Style.MOJO, "text");

        // Then
        assertThat(logStyle.usesMavenMessageBuilder()).isFalse();
        assertThat(styled).isEqualTo("text");
    }

    @Test
    void fallback_colorEnabled() {
        // Given
        LogStyle logStyle = new LogStyle(hidingClassLoader(SHARED_UTILS_PACKAGE, JLINE_PACKAGE), true);

        // When
        String strong = logStyle.style(Style.STRONG, "text");
        String mojo = logStyle.style(Style.MOJO, "text");
        String project = logStyle.style(Style.PROJECT, "text");

        // Then
        assertThat(logStyle.usesMavenMessageBuilder()).isFalse();
        assertThat(strong).isEqualTo("\u001B[1mtext\u001B[m");
        assertThat(mojo).isEqualTo("\u001B[32mtext\u001B[m");
        assertThat(project).isEqualTo("\u001B[36mtext\u001B[m");
    }

    private static ClassLoader hidingClassLoader(String... hiddenPackages) {
        List<String> hidden = Arrays.asList(hiddenPackages);
        return new ClassLoader(LogStyleTest.class.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (hidden.stream().anyMatch(name::startsWith)) {
                    throw new ClassNotFoundException(name);
                }
                return super.loadClass(name, resolve);
            }
        };
    }
}
