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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.*;

public class DistLogConfigTest {

    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("distlogconfig-test");
    }

    @AfterEach
    void tearDown() throws IOException {
        System.clearProperty("dongting.logging.config.file");
        System.clearProperty("java.util.logging.config.file");
        System.clearProperty("testKey");
        System.clearProperty("testKey2");
        System.clearProperty("testKey3");
        LogManager.getLogManager().reset();
        LogManager.getLogManager().readConfiguration();
        deleteRecursively(tempDir);
    }

    private void deleteRecursively(Path dir) throws IOException {
        if (Files.exists(dir)) {
            Files.walk(dir)
                    .sorted((a, b) -> b.compareTo(a))
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    // --- Formatter tests ---

    @Test
    void mainFormatterBasicFormat() {
        DistLogConfig.MainFormatter fmt = new DistLogConfig.MainFormatter();
        LogRecord record = new LogRecord(Level.INFO, "hello world");
        record.setLoggerName("com.github.dtprj.dongting.dist.TestClass");
        record.setMillis(1700000000000L);

        String result = fmt.format(record);

        assertTrue(result.contains("[main]"));
        assertTrue(result.contains("INFO"));
        assertTrue(result.contains("TestClass"));
        assertTrue(result.contains("hello world"));
        assertTrue(result.endsWith(System.lineSeparator()));
        assertTrue(result.contains(" - "));
    }

    @Test
    void mainFormatterShortLoggerName() {
        DistLogConfig.MainFormatter fmt = new DistLogConfig.MainFormatter();
        LogRecord record = new LogRecord(Level.INFO, "test");
        record.setLoggerName("com.foo.bar.MyClass");

        assertTrue(fmt.format(record).contains("MyClass"));
    }

    @Test
    void mainFormatterNullLoggerName() {
        DistLogConfig.MainFormatter fmt = new DistLogConfig.MainFormatter();
        LogRecord record = new LogRecord(Level.INFO, "test");
        record.setLoggerName(null);

        assertTrue(fmt.format(record).contains("root"));
    }

    @Test
    void mainFormatterEmptyLoggerName() {
        DistLogConfig.MainFormatter fmt = new DistLogConfig.MainFormatter();
        LogRecord record = new LogRecord(Level.INFO, "test");
        record.setLoggerName("");

        assertTrue(fmt.format(record).contains("root"));
    }

    @Test
    void mainFormatterSimpleLoggerName() {
        DistLogConfig.MainFormatter fmt = new DistLogConfig.MainFormatter();
        LogRecord record = new LogRecord(Level.INFO, "test");
        record.setLoggerName("RootLogger");

        assertTrue(fmt.format(record).contains("RootLogger"));
    }

    @Test
    void mainFormatterLevelPadding() {
        DistLogConfig.MainFormatter fmt = new DistLogConfig.MainFormatter();
        LogRecord record = new LogRecord(Level.INFO, "test");
        record.setLoggerName("test");

        assertTrue(fmt.format(record).contains("INFO  "));
    }

    @Test
    void mainFormatterWarnNoPadding() {
        DistLogConfig.MainFormatter fmt = new DistLogConfig.MainFormatter();
        LogRecord record = new LogRecord(Level.WARNING, "test");
        record.setLoggerName("test");

        assertTrue(fmt.format(record).contains("WARNING"));
    }

    @Test
    void mainFormatterWithThrowable() {
        DistLogConfig.MainFormatter fmt = new DistLogConfig.MainFormatter();
        LogRecord record = new LogRecord(Level.SEVERE, "error");
        record.setLoggerName("test");
        record.setThrown(new RuntimeException("test exception"));

        String result = fmt.format(record);
        assertTrue(result.contains("RuntimeException"));
        assertTrue(result.contains("test exception"));
    }

    @Test
    void mainFormatterNoThrowable() {
        DistLogConfig.MainFormatter fmt = new DistLogConfig.MainFormatter();
        LogRecord record = new LogRecord(Level.INFO, "ok");
        record.setLoggerName("test");

        assertFalse(fmt.format(record).contains("Exception"));
    }

    @Test
    void mainFormatterNestedMessage() {
        DistLogConfig.MainFormatter fmt = new DistLogConfig.MainFormatter();
        LogRecord record = new LogRecord(Level.INFO, "param {0} and {1}");
        record.setLoggerName("test");
        record.setParameters(new Object[]{"a", "b"});

        assertTrue(fmt.format(record).contains("param a and b"));
    }

    @Test
    void statsFormatterBasicFormat() {
        DistLogConfig.StatsFormatter fmt = new DistLogConfig.StatsFormatter();
        LogRecord record = new LogRecord(Level.INFO, "stat value");
        record.setLoggerName("com.github.dtprj.dongting.perf.SomeStat");
        record.setMillis(1700000000000L);

        String result = fmt.format(record);
        assertTrue(result.contains("[main]"));
        assertTrue(result.contains("INFO"));
        assertTrue(result.contains("stat value"));
        assertFalse(result.contains("SomeStat"));
        assertTrue(result.endsWith(System.lineSeparator()));
    }

    @Test
    void statsFormatterWithThrowable() {
        DistLogConfig.StatsFormatter fmt = new DistLogConfig.StatsFormatter();
        LogRecord record = new LogRecord(Level.WARNING, "stat error");
        record.setThrown(new IllegalStateException("bad state"));

        String result = fmt.format(record);
        assertTrue(result.contains("IllegalStateException"));
        assertTrue(result.contains("bad state"));
    }

    @Test
    void timestampFormat() {
        DistLogConfig.MainFormatter fmt = new DistLogConfig.MainFormatter();
        LogRecord record = new LogRecord(Level.INFO, "test");
        record.setLoggerName("test");
        record.setMillis(1700000000000L);

        String line = fmt.format(record).split(System.lineSeparator())[0];
        assertTrue(line.matches(".*\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}.*"));
    }

    @Test
    void nullRecordThrows() {
        assertThrows(NullPointerException.class, () -> new DistLogConfig.MainFormatter().format(null));
    }

    // --- substituteSystemProperties tests ---

    @Test
    void substituteNoVars() {
        assertEquals("plain text", DistLogConfig.substituteSystemProperties("plain text"));
    }

    @Test
    void substituteSystemProperty() {
        System.setProperty("testKey", "hello");
        assertEquals("hello", DistLogConfig.substituteSystemProperties("${testKey}"));
    }

    @Test
    void substituteMultipleVars() {
        System.setProperty("testKey", "hello");
        System.setProperty("testKey2", "world");
        assertEquals("hello-world", DistLogConfig.substituteSystemProperties("${testKey}-${testKey2}"));
    }

    @Test
    void substituteVarMixedWithText() {
        System.setProperty("testKey", "value");
        assertEquals("prefix-value-suffix", DistLogConfig.substituteSystemProperties("prefix-${testKey}-suffix"));
    }

    @Test
    void substituteEnvVar() {
        String path = System.getenv("PATH");
        if (path != null) {
            String result = DistLogConfig.substituteSystemProperties("${PATH}");
            assertEquals(DistLogConfig.escapePropertyValue(path), result);
        }
    }

    @Test
    void substituteSystemPropertyPrecedenceOverEnv() {
        System.setProperty("PATH", "sysPath");
        try {
            assertEquals("sysPath", DistLogConfig.substituteSystemProperties("${PATH}"));
        } finally {
            System.clearProperty("PATH");
        }
    }

    // --- resolveValue tests ---

    @Test
    void resolveFromSystemProperty() {
        System.setProperty("testKey", "sysVal");
        assertEquals("sysVal", DistLogConfig.resolveValue("testKey"));
    }

    @Test
    void resolveFromEnvWhenNoSystemProperty() {
        String path = System.getenv("PATH");
        if (path != null) {
            assertEquals(DistLogConfig.escapePropertyValue(path), DistLogConfig.resolveValue("PATH"));
        }
    }

    @Test
    void resolveDefaultWhenMissing() {
        assertEquals("fallback", DistLogConfig.resolveValue("nonExistentKey12345:-fallback"));
    }

    @Test
    void resolveEmptyDefault() {
        assertEquals("", DistLogConfig.resolveValue("nonExistentKey12345:"));
    }

    @Test
    void resolveNoDefaultMissing() {
        assertEquals("", DistLogConfig.resolveValue("nonExistentKey12345"));
    }

    @Test
    void resolveSystemPropertyTakesPrecedence() {
        System.setProperty("testKey", "fromSys");
        assertEquals("fromSys", DistLogConfig.resolveValue("testKey"));
    }

    @Test
    void resolveEmptySystemPropertyFallsToEnv() {
        System.setProperty("PATH", "");
        try {
            String path = System.getenv("PATH");
            if (path != null) {
                assertEquals(DistLogConfig.escapePropertyValue(path), DistLogConfig.resolveValue("PATH"));
            }
        } finally {
            System.clearProperty("PATH");
        }
    }

    // --- escapePropertyValue tests ---

    @Test
    void escapeNoBackslash() {
        assertEquals("normal", DistLogConfig.escapePropertyValue("normal"));
    }

    @Test
    void escapeSingleBackslash() {
        assertEquals("a\\\\b", DistLogConfig.escapePropertyValue("a\\b"));
    }

    @Test
    void escapeMultipleBackslashes() {
        assertEquals("\\\\\\\\", DistLogConfig.escapePropertyValue("\\\\"));
    }

    // --- loadConfig tests ---

    @Test
    void loadConfigNoConfigFile() {
        assertDoesNotThrow(DistLogConfig::loadConfig);
    }

    @Test
    void loadConfigEmptyDongtingProperty() {
        System.setProperty("dongting.logging.config.file", "");
        assertDoesNotThrow(DistLogConfig::loadConfig);
    }

    @Test
    void loadConfigNonExistentFile() {
        System.setProperty("dongting.logging.config.file", "/non/existent/path.properties");
        assertDoesNotThrow(DistLogConfig::loadConfig);
    }

    @Test
    void loadConfigValidFile() throws IOException {
        Path configFile = tempDir.resolve("test.properties");
        Files.writeString(configFile, "handlers=java.util.logging.ConsoleHandler\n.level=INFO\n");
        System.setProperty("dongting.logging.config.file", configFile.toString());

        assertDoesNotThrow(DistLogConfig::loadConfig);
        assertEquals(Level.INFO, LogManager.getLogManager().getLogger("").getLevel());
    }

    @Test
    void loadConfigJULFallback() throws IOException {
        Path configFile = tempDir.resolve("jul.properties");
        Files.writeString(configFile, "handlers=java.util.logging.ConsoleHandler\n.level=WARNING\n");
        System.setProperty("java.util.logging.config.file", configFile.toString());

        assertDoesNotThrow(DistLogConfig::loadConfig);
        assertEquals(Level.WARNING, LogManager.getLogManager().getLogger("").getLevel());
    }

    @Test
    void loadConfigDongtingTakesPrecedenceOverJUL() throws IOException {
        Path dtConfig = tempDir.resolve("dt.properties");
        Files.writeString(dtConfig, "handlers=java.util.logging.ConsoleHandler\n.level=INFO\n");
        Path julConfig = tempDir.resolve("jul.properties");
        Files.writeString(julConfig, "handlers=java.util.logging.ConsoleHandler\n.level=SEVERE\n");

        System.setProperty("dongting.logging.config.file", dtConfig.toString());
        System.setProperty("java.util.logging.config.file", julConfig.toString());

        DistLogConfig.loadConfig();
        assertEquals(Level.INFO, LogManager.getLogManager().getLogger("").getLevel());
    }

    @Test
    void loadConfigWithVariableSubstitution() throws IOException {
        Path logDir = tempDir.resolve("logs");
        Files.createDirectories(logDir);
        Path configFile = tempDir.resolve("var.properties");
        System.setProperty("LOG_DIR", logDir.toString().replace("\\", "/"));
        String content = "handlers=java.util.logging.ConsoleHandler\n" +
                ".level=FINE\n" +
                "java.util.logging.ConsoleHandler.level=${LOG_LEVEL:-INFO}\n";
        Files.writeString(configFile, content);
        System.setProperty("dongting.logging.config.file", configFile.toString());

        DistLogConfig.loadConfig();
        assertEquals(Level.FINE, LogManager.getLogManager().getLogger("").getLevel());
        assertEquals(Level.INFO, LogManager.getLogManager().getLogger("").getHandlers()[0].getLevel());
    }

    @Test
    void loadConfigInvalidContentDoesNotThrow() throws IOException {
        Path configFile = tempDir.resolve("bad.properties");
        Files.writeString(configFile, "this is not valid === properties\n");
        System.setProperty("dongting.logging.config.file", configFile.toString());
        assertDoesNotThrow(DistLogConfig::loadConfig);
    }
}
