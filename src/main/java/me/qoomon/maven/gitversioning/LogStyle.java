package me.qoomon.maven.gitversioning;

import java.lang.reflect.Method;

/**
 * Styling for log messages, delegating to Maven's own message builder.
 * <p>
 * Maven's message builder API is not exported to core extensions and differs between Maven versions:
 * <ul>
 *     <li>Maven &lt; 3.10: {@code org.apache.maven.shared.utils.logging.MessageUtils#buffer()} (maven-shared-utils)</li>
 *     <li>Maven 3.10+: {@code org.apache.maven.jline.MessageUtils#builder()} (maven-jline)</li>
 * </ul>
 * Therefore it is accessed via reflection, so the extension does not depend on either of them.
 * Using Maven's builder keeps Maven's color handling and style customizations ({@code -Dstyle.*}).
 * If none of them is available, minimal built-in ANSI styling is used as fallback.
 */
final class LogStyle {

    private static final LogStyle INSTANCE = new LogStyle(LogStyle.class.getClassLoader());

    private final MavenMessageBuilder mavenMessageBuilder;
    private final FallbackAnsi fallbackAnsi;

    /**
     * @param classLoader class loader to look up Maven's message builder API
     */
    LogStyle(ClassLoader classLoader) {
        this(classLoader, FallbackAnsi.isMavenColorEnabled(classLoader));
    }

    /**
     * @param classLoader          class loader to look up Maven's message builder API
     * @param fallbackColorEnabled whether built-in styling applies colors, if Maven's message builder is not available
     */
    LogStyle(ClassLoader classLoader, boolean fallbackColorEnabled) {
        this.mavenMessageBuilder = MavenMessageBuilder.resolve(classLoader);
        this.fallbackAnsi = new FallbackAnsi(fallbackColorEnabled);
    }

    static String strong(String text) {
        return INSTANCE.style(Style.STRONG, text);
    }

    static String mojo(String text) {
        return INSTANCE.style(Style.MOJO, text);
    }

    static String project(String text) {
        return INSTANCE.style(Style.PROJECT, text);
    }

    String style(Style style, String text) {
        if (mavenMessageBuilder != null) {
            try {
                return mavenMessageBuilder.style(style, text);
            } catch (Throwable ignore) {
                // fall through to built-in styling
            }
        }
        return fallbackAnsi.style(style, text);
    }

    boolean usesMavenMessageBuilder() {
        return mavenMessageBuilder != null;
    }

    enum Style {
        STRONG("strong", "\u001B[1m"),
        MOJO("mojo", "\u001B[32m"),
        PROJECT("project", "\u001B[36m");

        private final String methodName;
        private final String ansiCode;

        Style(String methodName, String ansiCode) {
            this.methodName = methodName;
            this.ansiCode = ansiCode;
        }
    }

    /**
     * Reflective access to Maven's message builder.
     */
    private static final class MavenMessageBuilder {

        private final Method factory;
        private final Method[] styleMethods;
        // null: builder's toString()
        private final Method finish;

        private MavenMessageBuilder(Method factory, Method[] styleMethods, Method finish) {
            this.factory = factory;
            this.styleMethods = styleMethods;
            this.finish = finish;
        }

        String style(Style style, String text) throws ReflectiveOperationException {
            Object builder = factory.invoke(null);
            styleMethods[style.ordinal()].invoke(builder, text);
            return finish != null ? (String) finish.invoke(builder) : builder.toString();
        }

        static MavenMessageBuilder resolve(ClassLoader classLoader) {
            MavenMessageBuilder builder = resolve(classLoader, "org.apache.maven.shared.utils.logging.MessageUtils", "buffer", null);
            if (builder == null) {
                builder = resolve(classLoader, "org.apache.maven.jline.MessageUtils", "builder", "build");
            }
            return builder;
        }

        private static MavenMessageBuilder resolve(ClassLoader classLoader, String messageUtilsClassName, String factoryMethodName, String finishMethodName) {
            try {
                Class<?> messageUtilsClass = Class.forName(messageUtilsClassName, true, classLoader);
                Method factory = messageUtilsClass.getMethod(factoryMethodName);
                // resolve methods on the declared (public) builder interface, implementations may not be accessible
                Class<?> builderType = factory.getReturnType();
                Method[] styleMethods = new Method[Style.values().length];
                for (Style style : Style.values()) {
                    styleMethods[style.ordinal()] = builderType.getMethod(style.methodName, Object.class);
                }
                // toString() can not be resolved on the builder interface, if not redeclared there
                Method finish = finishMethodName != null ? builderType.getMethod(finishMethodName) : null;
                return new MavenMessageBuilder(factory, styleMethods, finish);
            } catch (Throwable ignore) {
                return null;
            }
        }
    }

    /**
     * Built-in ANSI styling with Maven's default styles, used if Maven's message builder is not available.
     */
    private static final class FallbackAnsi {

        private static final String RESET = "\u001B[m";

        private final boolean colorEnabled;

        FallbackAnsi(boolean colorEnabled) {
            this.colorEnabled = colorEnabled;
        }

        String style(Style style, String text) {
            return colorEnabled ? style.ansiCode + text + RESET : text;
        }

        static boolean isMavenColorEnabled(ClassLoader classLoader) {
            String[] messageUtilsClassNames = {
                    "org.apache.maven.shared.utils.logging.MessageUtils", // Maven < 3.10
                    "org.apache.maven.jline.MessageUtils", // Maven 3.10+
            };
            for (String className : messageUtilsClassNames) {
                try {
                    Class<?> messageUtilsClass = Class.forName(className, true, classLoader);
                    return (boolean) messageUtilsClass.getMethod("isColorEnabled").invoke(null);
                } catch (Throwable ignore) {
                    // try next
                }
            }
            return false;
        }
    }
}
