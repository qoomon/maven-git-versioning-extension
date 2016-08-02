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
package fr.brouillard.oss.jgitver.cfg;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.apache.maven.MavenExecutionException;
import org.codehaus.plexus.logging.Logger;

public class ConfigurationLoader {
    /**
     * Loads a Configuration object from the root directory.
     * @param rootDirectory the root directory of the maven project
     * @param logger the logger to report activity
     * @return a non null Configuration object from the file $rootDirectory/.mvn/jgitver.config.xml
     *     or a default one with default values if the configuration file does not exist
     * @throws MavenExecutionException if the file exists but cannot be read correctly
     */
    public static Configuration loadFromRoot(File rootDirectory, Logger logger) throws MavenExecutionException {
        JAXBContext jaxbContext;
        Unmarshaller unmarshaller;
        File extensionMavenCoreDirectory = new File(rootDirectory, ".mvn");
        File configurationXml = new File(extensionMavenCoreDirectory, "jgitver.config.xml");
        if (!configurationXml.canRead()) {
            logger.debug("no configuration file found under " + configurationXml + ", looking under backwards-compatible file name");
            configurationXml = new File(extensionMavenCoreDirectory, "jgtiver.config.xml");
            if (!configurationXml.canRead()) {
                logger.debug("no configuration file found under " + configurationXml + ", using defaults");
                return new Configuration();
            }
        }
        try {
            logger.info("using jgitver configuration file: " + configurationXml);
            jaxbContext = JAXBContext.newInstance(Configuration.class);
            unmarshaller = jaxbContext.createUnmarshaller();
            Configuration c = (Configuration) unmarshaller.unmarshal(new FileInputStream(configurationXml));
            return c;
        } catch (JAXBException | FileNotFoundException ex) {
            throw new MavenExecutionException("cannot read configuration file " + configurationXml, ex);
        }
    }
    
//    public static void main(String[] args) throws Exception {
//        JAXBContext context = JAXBContext.newInstance(Configuration.class);
//        Marshaller marshaller = context.createMarshaller();
//        StringWriter sw = new StringWriter();
//        marshaller.marshal(new Configuration(), sw);
//        
//        System.out.println(sw.toString());
//    }
}
