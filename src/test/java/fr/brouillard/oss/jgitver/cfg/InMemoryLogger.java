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

import org.codehaus.plexus.logging.AbstractLogger;
import org.codehaus.plexus.logging.Logger;

public class InMemoryLogger extends  AbstractLogger {
    private final StringBuffer sb;
    private final String ls = System.getProperty("line.separator");
    
    public InMemoryLogger() {
        super(Logger.LEVEL_DEBUG, "InMemory");
        sb = new StringBuffer();
    }

    @Override
    public void debug(String message, Throwable throwable) {
        if (isDebugEnabled()) {
            log(message, throwable);
        }
    }

    @Override
    public void info(String message, Throwable throwable) {
        if (isInfoEnabled()) {
            log(message, throwable);
        }
    }

    @Override
    public void warn(String message, Throwable throwable) {
        if (isWarnEnabled()) {
            log(message, throwable);
        }
    }

    @Override
    public void error(String message, Throwable throwable) {
        if (isErrorEnabled()) {
            log(message, throwable);
        }
    }

    @Override
    public void fatalError(String message, Throwable throwable) {
        if (isFatalErrorEnabled()) {
            log(message, throwable);
        }
    }

    @Override
    public Logger getChildLogger(String name) {
        return this;
    }

    private synchronized void log(String message, Throwable throwable) {
        sb.append(message).append(ls);
        
        if (throwable != null) {
            sb.append("    ").append(throwable.toString());
        }
    }
    
    @Override
    public String toString() {
        return sb.toString();
    }
}
