/*
 * Copyright The Dongting Project
 *
 * The Dongting Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.github.dtprj.dongting.dist;

import com.github.dtprj.dongting.log.Slf4jFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.logging.LogRecord;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DistLogConfig {
    private static final String DIST_LOG_CONFIG_FILE = "dongting.logging.config.file";
    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)}");
    private static volatile boolean julLoaded;

    private DistLogConfig() {
    }

    public static void init() {
        if (Slf4jFactory.slf4jExists()) {
            return;
        }
        loadConfig();
    }

    static void loadConfig() {
        String configFile = System.getProperty(DIST_LOG_CONFIG_FILE);
        if (configFile == null || configFile.isEmpty()) {
            configFile = System.getProperty("java.util.logging.config.file");
        }
        if (configFile == null || configFile.isEmpty()) {
            return;
        }
        try {
            byte[] data = Files.readAllBytes(Path.of(configFile));
            String configText = new String(data, StandardCharsets.UTF_8);
            configText = substituteSystemProperties(configText);
            try (ByteArrayInputStream in = new ByteArrayInputStream(configText.getBytes(StandardCharsets.UTF_8))) {
                LogManager.getLogManager().readConfiguration(in);
            }
            julLoaded = true;
        } catch (Throwable e) {
            System.err.println("Failed to initialize JDK logging from " + configFile + ": " + e);
            e.printStackTrace();
        }
    }

    public static void close() {
        if (!julLoaded) {
            return;
        }
        julLoaded = false;
        try {
            Logger root = Logger.getLogger("");
            for (Handler h : root.getHandlers()) {
                try {
                    h.close();
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }

    static String substituteSystemProperties(String configText) {
        Matcher matcher = VAR_PATTERN.matcher(configText);
        StringBuilder sb = new StringBuilder(configText.length());
        while (matcher.find()) {
            String replacement = resolveValue(matcher.group(1));
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    static String resolveValue(String expression) {
        String key = expression;
        String defaultValue = "";
        int index = expression.indexOf(":-");
        if (index >= 0) {
            key = expression.substring(0, index);
            defaultValue = expression.substring(index + 2);
        }
        String value = System.getProperty(key);
        if (value == null || value.isEmpty()) {
            value = System.getenv(key);
        }
        if (value == null || value.isEmpty()) {
            value = defaultValue;
        }
        return escapePropertyValue(value);
    }

    static String escapePropertyValue(String value) {
        return value.replace("\\", "\\\\");
    }

    public static class MainFileHandler extends FileHandler {
        public MainFileHandler() throws IOException, SecurityException {
            super();
        }
    }

    public static class StatsFileHandler extends FileHandler {
        public StatsFileHandler() throws IOException, SecurityException {
            super();
        }
    }

    public static class MainFormatter extends BaseFormatter {
        public MainFormatter() {
            super(true);
        }
    }

    public static class StatsFormatter extends BaseFormatter {
        public StatsFormatter() {
            super(false);
        }
    }

    public static class BaseFormatter extends Formatter {
        private static final ZoneId ZONE = ZoneId.systemDefault();
        private static final String LINE_SEP = System.lineSeparator();

        private static final String PAD_INFO = "INFO ";
        private static final String PAD_FINE = "FINE ";
        private static final String PAD_SEVERE = "SEVERE"; // len=6
        private static final String PAD_FINEST = "FINEST"; // len=6
        private static final String PAD_FINER = "FINER"; // len=5
        private static final String PAD_CONFIG = "CONFIG"; // len=6

        private final boolean showLoggerName;

        protected BaseFormatter(boolean showLoggerName) {
            this.showLoggerName = showLoggerName;
        }

        @Override
        public String format(LogRecord record) {
            StringBuilder sb = new StringBuilder(160);
            formatTimestamp(record.getMillis(), sb);
            sb.append(' ');
            sb.append('[').append(Thread.currentThread().getName()).append("] ");
            if (showLoggerName) {
                sb.append(padLevel(record.getLevel())).append(' ');
                shortLoggerName(record.getLoggerName(), sb);
            } else {
                sb.append(record.getLevel().getName());
            }
            sb.append(" - ").append(formatMessage(record)).append(LINE_SEP);
            appendThrowable(record, sb);
            return sb.toString();
        }

        private static void formatTimestamp(long millis, StringBuilder sb) {
            Instant instant = Instant.ofEpochMilli(millis);
            // convert to system default zone fields
            // use java.time for correctness (DST, leap seconds, etc.)
            // but avoid DateTimeFormatter overhead by manual formatting
            var zdt = instant.atZone(ZONE);
            pad4(zdt.getYear(), sb);
            sb.append('-');
            pad2(zdt.getMonthValue(), sb);
            sb.append('-');
            pad2(zdt.getDayOfMonth(), sb);
            sb.append(' ');
            pad2(zdt.getHour(), sb);
            sb.append(':');
            pad2(zdt.getMinute(), sb);
            sb.append(':');
            pad2(zdt.getSecond(), sb);
            sb.append('.');
            int nanos = zdt.getNano();
            pad3(nanos / 1_000_000, sb);
        }

        private static void pad4(int value, StringBuilder sb) {
            sb.append((char) ('0' + value / 1000));
            sb.append((char) ('0' + value / 100 % 10));
            sb.append((char) ('0' + value / 10 % 10));
            sb.append((char) ('0' + value % 10));
        }

        private static void pad2(int value, StringBuilder sb) {
            sb.append((char) ('0' + value / 10));
            sb.append((char) ('0' + value % 10));
        }

        private static void pad3(int value, StringBuilder sb) {
            sb.append((char) ('0' + value / 100));
            sb.append((char) ('0' + value / 10 % 10));
            sb.append((char) ('0' + value % 10));
        }

        private static String padLevel(Level level) {
            if (level == Level.INFO) return PAD_INFO;
            if (level == Level.WARNING) return Level.WARNING.getName();
            if (level == Level.SEVERE) return PAD_SEVERE;
            if (level == Level.FINE) return PAD_FINE;
            if (level == Level.FINER) return PAD_FINER;
            if (level == Level.FINEST) return PAD_FINEST;
            if (level == Level.CONFIG) return PAD_CONFIG;
            String name = level.getName();
            if (name.length() >= 5) return name;
            return name + " ".repeat(5 - name.length());
        }

        private static void shortLoggerName(String loggerName, StringBuilder sb) {
            if (loggerName == null || loggerName.isEmpty()) {
                sb.append("root");
                return;
            }
            int idx = loggerName.lastIndexOf('.');
            sb.append(idx < 0 ? loggerName : loggerName.substring(idx + 1));
        }

        private static void appendThrowable(LogRecord record, StringBuilder sb) {
            Throwable t = record.getThrown();
            if (t == null) {
                return;
            }
            StringWriter sw = new StringWriter(256);
            t.printStackTrace(new PrintWriter(sw, true));
            sb.append(sw);
        }
    }
}
