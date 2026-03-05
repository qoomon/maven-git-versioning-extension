package me.qoomon.gitversioning.commons;

import org.slf4j.Logger;
import org.slf4j.Marker;

import java.util.LinkedList;
import java.util.List;

/**
 * This class is designed to accumulate logs and trigger then only when {@link #finish(Runnable, Runnable)} is called.
 * This class is mainly used when you want to perform an action before and after creating logs, if and only if logs are generated.
 */
public class DeferredLogger implements Logger {
    private final Logger parent;
    private final List<Runnable> acceptedLogs = new LinkedList<>();

    public DeferredLogger(final Logger parent) {
        this.parent = parent;
    }

    @Override
    public String getName() {
        return parent.getName();
    }

    // region trace
    @Override
    public boolean isTraceEnabled() {
        return parent.isTraceEnabled();
    }

    @Override
    public void trace(final String msg) {
        if (!isTraceEnabled()) return;

        acceptedLogs.add(() -> parent.trace(msg));
    }

    @Override
    public void trace(final String format, final Object arg) {
        if (!isTraceEnabled()) return;

        acceptedLogs.add(() -> parent.trace(format, arg));
    }

    @Override
    public void trace(final String format, final Object arg1, final Object arg2) {
        if (!isTraceEnabled()) return;

        acceptedLogs.add(() -> parent.trace(format, arg1, arg2));
    }

    @Override
    public void trace(final String format, final Object... arguments) {
        if (!isTraceEnabled()) return;

        acceptedLogs.add(() -> parent.trace(format, arguments));
    }

    @Override
    public void trace(final String msg, Throwable t) {
        if (!isTraceEnabled()) return;

        acceptedLogs.add(() -> parent.trace(msg, t));
    }

    @Override
    public boolean isTraceEnabled(final Marker marker) {
        return parent.isTraceEnabled(marker);
    }

    @Override
    public void trace(final Marker marker, final String msg) {
        if (!isTraceEnabled(marker)) return;

        acceptedLogs.add(() -> parent.trace(marker, msg));
    }

    @Override
    public void trace(final Marker marker, final String format, final Object arg) {
        if (!isTraceEnabled(marker)) return;

        acceptedLogs.add(() -> parent.trace(marker, format, arg));
    }

    @Override
    public void trace(final Marker marker, final String format, final Object arg1, final Object arg2) {
        if (!isTraceEnabled(marker)) return;

        acceptedLogs.add(() -> parent.trace(marker, format, arg1, arg2));
    }

    @Override
    public void trace(final Marker marker, final String format, final Object... argArray) {
        if (!isTraceEnabled(marker)) return;

        acceptedLogs.add(() -> parent.trace(marker, format, argArray));
    }

    @Override
    public void trace(final Marker marker, final String msg, Throwable t) {
        if (!isTraceEnabled(marker)) return;

        acceptedLogs.add(() -> parent.trace(marker, msg, t));
    }
    // endregion

    // region debug
    @Override
    public boolean isDebugEnabled() {
        return parent.isDebugEnabled();
    }

    @Override
    public void debug(final String msg) {
        if (!isDebugEnabled()) return;

        acceptedLogs.add(() -> parent.debug(msg));
    }

    @Override
    public void debug(final String format, final Object arg) {
        if (!isDebugEnabled()) return;

        acceptedLogs.add(() -> parent.debug(format, arg));
    }

    @Override
    public void debug(final String format, final Object arg1, final Object arg2) {
        if (!isDebugEnabled()) return;

        acceptedLogs.add(() -> parent.debug(format, arg1, arg2));
    }

    @Override
    public void debug(final String format, final Object... arguments) {
        if (!isDebugEnabled()) return;

        acceptedLogs.add(() -> parent.debug(format, arguments));
    }

    @Override
    public void debug(final String msg, Throwable t) {
        if (!isDebugEnabled()) return;

        acceptedLogs.add(() -> parent.debug(msg, t));
    }

    @Override
    public boolean isDebugEnabled(final Marker marker) {
        return parent.isDebugEnabled(marker);
    }

    @Override
    public void debug(final Marker marker, final String msg) {
        if (!isDebugEnabled(marker)) return;

        acceptedLogs.add(() -> parent.debug(marker, msg));
    }

    @Override
    public void debug(final Marker marker, final String format, final Object arg) {
        if (!isDebugEnabled(marker)) return;

        acceptedLogs.add(() -> parent.debug(marker, format, arg));
    }

    @Override
    public void debug(final Marker marker, final String format, final Object arg1, final Object arg2) {
        if (!isDebugEnabled(marker)) return;

        acceptedLogs.add(() -> parent.debug(marker, format, arg1, arg2));
    }

    @Override
    public void debug(final Marker marker, final String format, final Object... arguments) {
        if (!isDebugEnabled(marker)) return;

        acceptedLogs.add(() -> parent.debug(marker, format, arguments));
    }

    @Override
    public void debug(final Marker marker, final String msg, Throwable t) {
        if (!isDebugEnabled(marker)) return;

        acceptedLogs.add(() -> parent.debug(marker, msg, t));
    }
    // endregion

    // region info
    @Override
    public boolean isInfoEnabled() {
        return parent.isInfoEnabled();
    }

    @Override
    public void info(final String msg) {
        if (!isInfoEnabled()) return;

        acceptedLogs.add(() -> parent.info(msg));
    }

    @Override
    public void info(final String format, final Object arg) {
        if (!isInfoEnabled()) return;

        acceptedLogs.add(() -> parent.info(format, arg));
    }

    @Override
    public void info(final String format, final Object arg1, final Object arg2) {
        if (!isInfoEnabled()) return;

        acceptedLogs.add(() -> parent.info(format, arg1, arg2));
    }

    @Override
    public void info(final String format, final Object... arguments) {
        if (!isInfoEnabled()) return;

        acceptedLogs.add(() -> parent.info(format, arguments));
    }

    @Override
    public void info(final String msg, Throwable t) {
        if (!isInfoEnabled()) return;

        acceptedLogs.add(() -> parent.info(msg, t));
    }

    @Override
    public boolean isInfoEnabled(final Marker marker) {
        return parent.isInfoEnabled(marker);
    }

    @Override
    public void info(final Marker marker, final String msg) {
        if (!isInfoEnabled(marker)) return;

        acceptedLogs.add(() -> parent.info(marker, msg));
    }

    @Override
    public void info(final Marker marker, final String format, final Object arg) {
        if (!isInfoEnabled(marker)) return;

        acceptedLogs.add(() -> parent.info(marker, format, arg));
    }

    @Override
    public void info(final Marker marker, final String format, final Object arg1, final Object arg2) {
        if (!isInfoEnabled(marker)) return;

        acceptedLogs.add(() -> parent.info(marker, format, arg1, arg2));
    }

    @Override
    public void info(final Marker marker, final String format, final Object... arguments) {
        if (!isInfoEnabled(marker)) return;

        acceptedLogs.add(() -> parent.info(marker, format, arguments));
    }

    @Override
    public void info(final Marker marker, final String msg, Throwable t) {
        if (!isInfoEnabled(marker)) return;

        acceptedLogs.add(() -> parent.info(marker, msg, t));
    }
    // endregion

    // region warn
    @Override
    public boolean isWarnEnabled() {
        return parent.isWarnEnabled();
    }

    @Override
    public void warn(final String msg) {
        if (!isWarnEnabled()) return;

        acceptedLogs.add(() -> parent.warn(msg));
    }

    @Override
    public void warn(final String format, final Object arg) {
        if (!isWarnEnabled()) return;

        acceptedLogs.add(() -> parent.warn(format, arg));
    }

    @Override
    public void warn(final String format, final Object... arguments) {
        if (!isWarnEnabled()) return;

        acceptedLogs.add(() -> parent.warn(format, arguments));
    }

    @Override
    public void warn(final String format, final Object arg1, final Object arg2) {
        if (!isWarnEnabled()) return;

        acceptedLogs.add(() -> parent.warn(format, arg1, arg2));
    }

    @Override
    public void warn(final String msg, Throwable t) {
        if (!isWarnEnabled()) return;

        acceptedLogs.add(() -> parent.warn(msg, t));
    }

    @Override
    public boolean isWarnEnabled(final Marker marker) {
        return parent.isWarnEnabled(marker);
    }

    @Override
    public void warn(final Marker marker, final String msg) {
        if (!isWarnEnabled(marker)) return;

        acceptedLogs.add(() -> parent.warn(marker, msg));
    }

    @Override
    public void warn(final Marker marker, final String format, final Object arg) {
        if (!isWarnEnabled(marker)) return;

        acceptedLogs.add(() -> parent.warn(marker, format, arg));
    }

    @Override
    public void warn(final Marker marker, final String format, final Object arg1, final Object arg2) {
        if (!isWarnEnabled(marker)) return;

        acceptedLogs.add(() -> parent.warn(marker, format, arg1, arg2));
    }

    @Override
    public void warn(final Marker marker, final String format, final Object... arguments) {
        if (!isWarnEnabled(marker)) return;

        acceptedLogs.add(() -> parent.warn(marker, format, arguments));
    }

    @Override
    public void warn(final Marker marker, final String msg, Throwable t) {
        if (!isWarnEnabled(marker)) return;

        acceptedLogs.add(() -> parent.warn(marker, msg, t));
    }
    // endregion

    // region error
    @Override
    public boolean isErrorEnabled() {
        return parent.isErrorEnabled();
    }

    @Override
    public void error(final String msg) {
        if (!isErrorEnabled()) return;

        acceptedLogs.add(() -> parent.error(msg));
    }

    @Override
    public void error(final String format, final Object arg) {
        if (!isErrorEnabled()) return;

        acceptedLogs.add(() -> parent.error(format, arg));
    }

    @Override
    public void error(final String format, final Object arg1, final Object arg2) {
        if (!isErrorEnabled()) return;

        acceptedLogs.add(() -> parent.error(format, arg1, arg2));
    }

    @Override
    public void error(final String format, final Object... arguments) {
        if (!isErrorEnabled()) return;

        acceptedLogs.add(() -> parent.error(format, arguments));
    }

    @Override
    public void error(final String msg, Throwable t) {
        if (!isErrorEnabled()) return;

        acceptedLogs.add(() -> parent.error(msg, t));
    }

    @Override
    public boolean isErrorEnabled(final Marker marker) {
        return parent.isErrorEnabled(marker);
    }

    @Override
    public void error(final Marker marker, final String msg) {
        if (!isErrorEnabled(marker)) return;

        acceptedLogs.add(() -> parent.error(marker, msg));
    }

    @Override
    public void error(final Marker marker, final String format, final Object arg) {
        if (!isErrorEnabled(marker)) return;

        acceptedLogs.add(() -> parent.error(marker, format, arg));
    }

    @Override
    public void error(final Marker marker, final String format, final Object arg1, final Object arg2) {
        if (!isErrorEnabled(marker)) return;

        acceptedLogs.add(() -> parent.error(marker, format, arg1, arg2));
    }

    @Override
    public void error(final Marker marker, final String format, final Object... arguments) {
        if (!isErrorEnabled(marker)) return;

        acceptedLogs.add(() -> parent.error(marker, format, arguments));
    }

    @Override
    public void error(final Marker marker, final String msg, Throwable t) {
        if (!isErrorEnabled(marker)) return;

        acceptedLogs.add(() -> parent.error(marker, msg, t));
    }
    // endregion

    /**
     * Method used to finalize logging with all accumulated logs
     * @param preExecution runnable executed before triggering accumulated logs (if and only if accumulated logs are not empty)
     * @param postExecution runnable executed after triggering accumulated logs (if and only if accumulated logs are not empty)
     */
    public void finish(final Runnable preExecution, final Runnable postExecution) {
        if (acceptedLogs.isEmpty()) return;

        preExecution.run();
        final LinkedList<Runnable> currentsLogs = new LinkedList<>(acceptedLogs);
        for (final Runnable acceptedLog : currentsLogs) {
            acceptedLog.run();
        }
        acceptedLogs.removeAll(currentsLogs);
        postExecution.run();
    }
}
