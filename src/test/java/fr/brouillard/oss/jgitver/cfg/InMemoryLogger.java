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
