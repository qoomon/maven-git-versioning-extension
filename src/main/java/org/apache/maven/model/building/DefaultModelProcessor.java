package org.apache.maven.model.building;

import me.qoomon.maven.gitversioning.GitVersioningModelProcessor;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.DefaultModelReader;
import org.apache.maven.model.locator.DefaultModelLocator;
import org.eclipse.sisu.Typed;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.Map;

/**
 * WORKAROUND
 * Workaround to replace origin {@link ModelProcessor} component with {@link GitVersioningModelProcessor}.
 * This is need because maven 3.6.2 has broken component replacement mechanism.
 */
@Named
@Singleton
@Typed(ModelProcessor.class)
public class DefaultModelProcessor implements ModelProcessor {

    @Inject
    private DefaultModelLocator locator;

    @Inject
    private DefaultModelReader reader;

    @Inject
    private GitVersioningModelProcessor modelProcessor;

    @Override
    public File locatePom(File projectDirectory) {
        return locator.locatePom(projectDirectory);
    }

    @Override
    public Model read(File input, Map<String, ?> options) throws IOException {
        final Model projectModel = reader.read(input, options);
        return modelProcessor.processModel(projectModel, options);
    }

    @Override
    public Model read(Reader input, Map<String, ?> options) throws IOException {
        final Model projectModel = reader.read(input, options);
        return modelProcessor.processModel(projectModel, options);
    }

    @Override
    public Model read(InputStream input, Map<String, ?> options) throws IOException {
        final Model projectModel = reader.read(input, options);
        return modelProcessor.processModel(projectModel, options);
    }
}