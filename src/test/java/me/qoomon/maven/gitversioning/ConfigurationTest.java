package me.qoomon.maven.gitversioning;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class ConfigurationTest {

    @Test
    void xmlUnmarshaller_empty() throws IOException {
        // given
        String configXml =   "<gitVersioning>\n" +
                "</gitVersioning>\n";

        // when
        Configuration config = new XmlMapper()
                .readValue(configXml, Configuration.class);

        // then
        assertAll(
                () -> assertThat(config.preferTags).isNull(),
                () -> assertThat(config.commit).isNull(),
                () -> assertThat(config.branch).isEmpty(),
                () -> assertThat(config.tag).isEmpty()
        );
    }

    @Test
    void xmlUnmarshaller_commitConfigOnly() throws IOException {
        // given
        String configXml = "" +
                "<gitVersioning>\n" +
                "    <commit>\n" +
                "        <versionFormat>commit1-format</versionFormat>\n" +
                "    </commit>\n" +
                "</gitVersioning>\n";

        // when
        Configuration config = new XmlMapper()
                .readValue(configXml, Configuration.class);

        // then
        assertAll(
                () -> assertThat(config.commit)
                        .satisfies(commitConfig -> assertThat(commitConfig.versionFormat).isEqualTo("commit1-format")),
                () -> assertThat(config.branch).isEmpty(),
                () -> assertThat(config.tag).isEmpty()
                );
    }

    @Test
    void xmlUnmarshaller_branchConfigsOnly() throws IOException {
        // given
        String configXml = "" +
                "<gitVersioning>\n" +
                "    <branch>\n" +
                "        <pattern>branch1-pattern</pattern>\n" +
                "        <versionFormat>branch1-format</versionFormat>\n" +
                "    </branch>\n" +
                "    <branch>\n" +
                "        <pattern>branch2-pattern</pattern>\n" +
                "        <versionFormat>branch2-format</versionFormat>\n" +
                "    </branch>\n" +
                "</gitVersioning>\n";

        // when
        Configuration config = new XmlMapper()
                .readValue(configXml, Configuration.class);

        // then
        assertAll(
                () -> assertThat(config.commit).isNull(),
                () -> assertThat(config.branch)
                        .satisfies(branchConfigs -> assertAll(
                                () -> assertThat(branchConfigs).hasSize(2),
                                () -> assertThat(branchConfigs.get(0)).satisfies(branchConfig -> assertAll(
                                        () -> assertThat(branchConfig.pattern).isEqualTo("branch1-pattern"),
                                        () -> assertThat(branchConfig.versionFormat).isEqualTo("branch1-format")
                                )),
                                () -> assertThat(branchConfigs.get(1)).satisfies(branchConfig -> assertAll(
                                        () -> assertThat(branchConfig.pattern).isEqualTo("branch2-pattern"),
                                        () -> assertThat(branchConfig.versionFormat).isEqualTo("branch2-format")
                                ))
                        )),
                () -> assertThat(config.tag).isEmpty()
        );
    }

    @Test
    void xmlUnmarshaller_branchConfigsOnlyWithProperties() throws IOException {
        // given
        String configXml = "" +
                "<gitVersioning>\n" +
                "    <branch>\n" +
                "        <pattern>branch1-pattern</pattern>\n" +
                "        <versionFormat>branch1-format</versionFormat>\n" +
                "        <property>\n" +
                "            <name>my.property</name>\n" +
                "            <valueFormat>my.property.format</valueFormat>\n" +
                "        </property>\n" +
                "    </branch>\n" +
                "    <branch>\n" +
                "        <pattern>branch2-pattern</pattern>\n" +
                "        <versionFormat>branch2-format</versionFormat>\n" +
                "        <property>\n" +
                "            <name>my.first.property</name>\n" +
                "            <valueFormat>my.first.property.format</valueFormat>\n" +
                "        </property>\n" +
                "        <property>\n" +
                "            <name>my.second.property</name>\n" +
                "            <valueFormat>my.second.property.format</valueFormat>\n" +
                "        </property>\n" +
                "    </branch>\n" +
                "</gitVersioning>\n";

        // when
        Configuration config = new XmlMapper()
                .readValue(configXml, Configuration.class);

        // then
        assertAll(
                () -> assertThat(config.commit).isNull(),
                () -> assertThat(config.branch)
                        .satisfies(branchConfigs -> assertAll(
                                () -> assertThat(branchConfigs).hasSize(2),
                                () -> assertThat(branchConfigs.get(0)).satisfies(branchConfig -> assertAll(
                                        () -> assertThat(branchConfig.pattern).isEqualTo("branch1-pattern"),
                                        () -> assertThat(branchConfig.versionFormat).isEqualTo("branch1-format"),
                                        () -> assertThat(branchConfig.property).hasSize(1),
                                        () -> assertThat(branchConfig.property.get(0)).satisfies(branchPropertyConfig -> assertAll(
                                                () -> assertThat(branchPropertyConfig.name).isEqualTo("my.property"),
                                                () -> assertThat(branchPropertyConfig.valueFormat).isEqualTo("my.property.format")
                                        ))
                                )),
                                () -> assertThat(branchConfigs.get(1)).satisfies(branchConfig -> assertAll(
                                        () -> assertThat(branchConfig.pattern).isEqualTo("branch2-pattern"),
                                        () -> assertThat(branchConfig.versionFormat).isEqualTo("branch2-format"),
                                        () -> assertThat(branchConfig.property).hasSize(2),
                                        () -> assertThat(branchConfig.property.get(0)).satisfies(branchPropertyConfig -> assertAll(
                                                () -> assertThat(branchPropertyConfig.name).isEqualTo("my.first.property"),
                                                () -> assertThat(branchPropertyConfig.valueFormat).isEqualTo("my.first.property.format")
                                        )),
                                        () -> assertThat(branchConfig.property.get(1)).satisfies(branchPropertyConfig -> assertAll(
                                                () -> assertThat(branchPropertyConfig.name).isEqualTo("my.second.property"),
                                                () -> assertThat(branchPropertyConfig.valueFormat).isEqualTo("my.second.property.format")
                                        ))
                                ))
                        )),
                () -> assertThat(config.tag).isEmpty()
        );
    }

    @Test
    void xmlUnmarshaller_tagsConfigsOnly() throws IOException {
        // given
        String configXml = "" +
                "<gitVersioning>\n" +
                "    <tag>\n" +
                "        <pattern>tag1-pattern</pattern>\n" +
                "        <versionFormat>tag1-format</versionFormat>\n" +
                "    </tag>\n" +
                "    <tag>\n" +
                "        <pattern>tag2-pattern</pattern>\n" +
                "        <versionFormat>tag2-format</versionFormat>\n" +
                "    </tag>\n" +
                "</gitVersioning>\n";

        // when
        Configuration config = new XmlMapper()
                .readValue(configXml, Configuration.class);

        // then
        assertAll(
                () -> assertThat(config.commit).isNull(),
                () -> assertThat(config.branch).isEmpty(),
                () -> assertThat(config.tag)
                        .satisfies(tagConfigs -> assertAll(
                                () -> assertThat(tagConfigs).hasSize(2),
                                () -> assertThat(tagConfigs.get(0)).satisfies(tagConfig -> assertAll(
                                        () -> assertThat(tagConfig.pattern).isEqualTo("tag1-pattern"),
                                        () -> assertThat(tagConfig.versionFormat).isEqualTo("tag1-format")
                                )),
                                () -> assertThat(tagConfigs.get(1)).satisfies(tagConfig -> assertAll(
                                        () -> assertThat(tagConfig.pattern).isEqualTo("tag2-pattern"),
                                        () -> assertThat(tagConfig.versionFormat).isEqualTo("tag2-format")
                                ))
                        ))
        );
    }

    @Test
    void xmlUnmarshaller() throws IOException {
        // given
        String configXml = "" +
                "<gitVersioning>\n" +
                "    <preferTags>true</preferTags>\n" +
                "    <commit>\n" +
                "        <versionFormat>commit1-format</versionFormat>\n" +
                "    </commit>\n" +
                "    <branch>\n" +
                "        <pattern>branch1-pattern</pattern>\n" +
                "        <versionFormat>branch1-format</versionFormat>\n" +
                "    </branch>\n" +
                "    <branch>\n" +
                "        <pattern>branch2-pattern</pattern>\n" +
                "        <versionFormat>branch2-format</versionFormat>\n" +
                "    </branch>\n" +
                "    <tag>\n" +
                "        <pattern>tag1-pattern</pattern>\n" +
                "        <versionFormat>tag1-format</versionFormat>\n" +
                "    </tag>\n" +
                "    <tag>\n" +
                "        <pattern>tag2-pattern</pattern>\n" +
                "        <versionFormat>tag2-format</versionFormat>\n" +
                "    </tag>\n" +
                "</gitVersioning>\n";

        // when
        Configuration config = new XmlMapper()
                .readValue(configXml, Configuration.class);

        // then
        assertAll(
                () -> assertThat(config.preferTags).isTrue(),
                () -> assertThat(config.commit)
                        .satisfies(commitConfig -> assertThat(commitConfig.versionFormat).isEqualTo("commit1-format")),
                () -> assertThat(config.branch)
                        .satisfies(branchConfigs -> assertAll(
                                () -> assertThat(branchConfigs).hasSize(2),
                                () -> assertThat(branchConfigs.get(0)).satisfies(branchConfig -> assertAll(
                                        () -> assertThat(branchConfig.pattern).isEqualTo("branch1-pattern"),
                                        () -> assertThat(branchConfig.versionFormat).isEqualTo("branch1-format")
                                )),
                                () -> assertThat(branchConfigs.get(1)).satisfies(branchConfig -> assertAll(
                                        () -> assertThat(branchConfig.pattern).isEqualTo("branch2-pattern"),
                                        () -> assertThat(branchConfig.versionFormat).isEqualTo("branch2-format")
                                ))
                        )),
                () -> assertThat(config.tag)
                        .satisfies(tagConfigs -> assertAll(
                                () -> assertThat(tagConfigs).hasSize(2),
                                () -> assertThat(tagConfigs.get(0)).satisfies(tagConfig -> assertAll(
                                        () -> assertThat(tagConfig.pattern).isEqualTo("tag1-pattern"),
                                        () -> assertThat(tagConfig.versionFormat).isEqualTo("tag1-format")
                                )),
                                () -> assertThat(tagConfigs.get(1)).satisfies(tagConfig -> assertAll(
                                        () -> assertThat(tagConfig.pattern).isEqualTo("tag2-pattern"),
                                        () -> assertThat(tagConfig.versionFormat).isEqualTo("tag2-format")
                                ))
                        ))
        );
    }
}
