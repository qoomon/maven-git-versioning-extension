// @formatter:off
/**
 * Copyright (C) 2016 Matthieu Brouillard [http://oss.brouillard.fr/jgitver-maven-plugin] (matthieu@brouillard.fr)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// @formatter:on
package fr.brouillard.oss.jgitver;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Works in conjunction with JGitverModelProcessor.
 */
@XmlRootElement(name = "workingConfiguration")
@XmlAccessorType(XmlAccessType.FIELD)
public class JGitverModelProcessorWorkingConfiguration {
    @XmlElement(name = "calculatedVersion")
    private String calculatedVersion;

    @XmlElement(name = "multiModuleProjectDirectory")
    private File multiModuleProjectDirectory;

    @XmlElement(name = "newProjectVersions")
    private Map<GAV, String> newProjectVersions = new HashMap<>();

    public JGitverModelProcessorWorkingConfiguration() {
    }

    public JGitverModelProcessorWorkingConfiguration(String calculatedVersion, File multiModuleProjectDirectory) {
        this.calculatedVersion = calculatedVersion;
        this.multiModuleProjectDirectory = multiModuleProjectDirectory;
    }

    public String getCalculatedVersion() {
        return calculatedVersion;
    }

    public void setCalculatedVersion(String calculatedVersion) {
        this.calculatedVersion = calculatedVersion;
    }

    public File getMultiModuleProjectDirectory() {
        return multiModuleProjectDirectory;
    }

    public void setMultiModuleProjectDirectory(File multiModuleProjectDirectory) {
        this.multiModuleProjectDirectory = multiModuleProjectDirectory;
    }

    public Map<GAV, String> getNewProjectVersions() {
        return newProjectVersions;
    }

    public void setNewProjectVersions(Map<GAV, String> newProjectVersions) {
        this.newProjectVersions = newProjectVersions;
    }

    /**
     * Serializes as a String the given configuration object. 
     * @param workingConfiguration the object to serialize
     * @return a non null String representation of the given object serialized 
     * @throws JAXBException in case the given object could not be serialized by JAXB
     * @throws IOException if the serialized form cannot be written
     * @see JGitverModelProcessorWorkingConfiguration#serializeFrom(String)
     */
    public static String serializeTo(JGitverModelProcessorWorkingConfiguration workingConfiguration) throws
            JAXBException, IOException {
        JAXBContext jaxbContext = JAXBContext.newInstance(JGitverModelProcessorWorkingConfiguration.class, GAV.class);
        Marshaller marshaller = jaxbContext.createMarshaller();

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(byteArrayOutputStream)) {
            marshaller.marshal(workingConfiguration, bufferedOutputStream);
        }

        return byteArrayOutputStream.toString();
    }

    /**
     * De-serializes the given string as a {@link JGitverModelProcessorWorkingConfiguration}.
     * @param content the string to de-serialize
     * @return a non null configuration object
     * @throws JAXBException if the given string could not be interpreted by JAXB 
     * @throws IOException if the content of the serialized object could not be read in memory
     */
    public static JGitverModelProcessorWorkingConfiguration serializeFrom(String content) throws JAXBException,
            IOException {
        JAXBContext jaxbContext = JAXBContext.newInstance(JGitverModelProcessorWorkingConfiguration.class, GAV.class);
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();

        JGitverModelProcessorWorkingConfiguration workingConfiguration;
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(content.getBytes());

        try (BufferedInputStream bufferedInputStream = new BufferedInputStream(byteArrayInputStream)) {
            workingConfiguration = (JGitverModelProcessorWorkingConfiguration) unmarshaller.unmarshal(bufferedInputStream);
        }

        return workingConfiguration;
    }
}
