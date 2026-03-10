package com.sahr.logging;

import java.util.Locale;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public final class SahrLogging {
    private static final String LEVEL_PROPERTY = "sahr.log.level";
    private static final String FILE_PROPERTY = "sahr.log.file";
    private static final String FORMAT_PROPERTY = "java.util.logging.SimpleFormatter.format";

    private SahrLogging() {
    }

    public static void configure() {
        String format = "%1$tF %1$tT.%1$tL %4$s [%2$s] %5$s%6$s%n";
        System.setProperty(FORMAT_PROPERTY, format);

        Level level = parseLevel(System.getProperty(LEVEL_PROPERTY, "INFO"));
        Logger root = Logger.getLogger("");
        root.setLevel(level);
        for (Handler handler : root.getHandlers()) {
            if (handler instanceof ConsoleHandler) {
                handler.setLevel(level);
            }
        }

        setElkLoggingLevel(System.getProperty("sahr.log.elk.level", "SEVERE"));
        setOwlApiLoggingLevel(System.getProperty("sahr.log.owlapi.level", "WARNING"));

        String logFile = System.getProperty(FILE_PROPERTY);
        if (logFile != null && !logFile.isBlank()) {
            try {
                FileHandler fileHandler = new FileHandler(logFile, true);
                fileHandler.setLevel(level);
                fileHandler.setFormatter(new SimpleFormatter());
                root.addHandler(fileHandler);
            } catch (Exception ex) {
                root.log(Level.WARNING, "Failed to configure file logging: " + ex.getMessage(), ex);
            }
        }
    }

    private static void setElkLoggingLevel(String raw) {
        Level level = parseLevel(raw);
        String prefix = "org.semanticweb.elk";
        java.util.logging.LogManager manager = java.util.logging.LogManager.getLogManager();
        java.util.Enumeration<String> names = manager.getLoggerNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            if (name != null && name.startsWith(prefix)) {
                Logger logger = Logger.getLogger(name);
                logger.setLevel(level);
                for (Handler handler : logger.getHandlers()) {
                    handler.setLevel(level);
                }
            }
        }
        Logger elkRoot = Logger.getLogger(prefix);
        elkRoot.setLevel(level);
    }

    private static void setOwlApiLoggingLevel(String raw) {
        Level level = parseLevel(raw);
        String loggerName = "org.semanticweb.owlapi.rdf.rdfxml.parser.TripleLogger";
        Logger logger = Logger.getLogger(loggerName);
        logger.setLevel(level);
        for (Handler handler : logger.getHandlers()) {
            handler.setLevel(level);
        }
    }

    private static Level parseLevel(String raw) {
        String normalized = raw == null ? "INFO" : raw.trim().toUpperCase(Locale.ROOT);
        try {
            return Level.parse(normalized);
        } catch (IllegalArgumentException ex) {
            return Level.INFO;
        }
    }
}
