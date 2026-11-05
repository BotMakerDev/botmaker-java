package com.botmaker.java;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** What keeps this module a library any host can use: no UI, no host above it, one parser. */
class JavaLayersTest {

    private static final Path MAIN = Path.of("src/main/java");
    /** An import of anything but the JDK (its language model too, no UI), this module, the contract and JDT. */
    private static final Pattern ABOVE_THIS_MODULE = Pattern.compile(
            "(?m)^import (static )?+(?!(java|javax\\.lang\\.model|com\\.botmaker\\.java|com\\.botmaker\\.plugin\\.api"
                    + "|org\\.eclipse)\\.)");
    private static final Pattern NEWEST_JAVA = Pattern.compile("getJLSLatest|latestSupportedJavaVersion");

    @Test
    void nothingAboveThisModuleIsImported() throws IOException {
        List<Path> sources = sources();
        assertFalse(sources.isEmpty(), "run from the module's root");
        assertEquals(List.of(), sources.stream().filter(file -> ABOVE_THIS_MODULE.matcher(read(file)).find()).toList());
    }

    @Test
    void onlySourceParserCreatesAParser() throws IOException {
        assertEquals(List.of(MAIN.resolve("com/botmaker/java/grammar/SourceParser.java")),
                sources().stream().filter(file -> read(file).contains("ASTParser.newParser")).toList());
    }

    /** Every parse and tree takes the bot's level (2026-11-04): JDT's newest is named in one place. */
    @Test
    void onlyJavaLevelNamesJdtsNewestJava() throws IOException {
        assertEquals(List.of(MAIN.resolve("com/botmaker/java/parse/JavaLevel.java")),
                sources().stream().filter(file -> NEWEST_JAVA.matcher(read(file)).find()).toList());
    }

    /** JDT copies within one level only; a copy into a tree of another goes through {@code JavaLevel.copy}. */
    @Test
    void onlyJavaLevelCopiesASubtree() throws IOException {
        assertEquals(List.of(MAIN.resolve("com/botmaker/java/parse/JavaLevel.java")),
                sources().stream().filter(file -> read(file).contains("ASTNode.copySubtree(")).toList());
    }

    private static List<Path> sources() throws IOException {
        try (Stream<Path> files = Files.walk(MAIN)) {
            return files.filter(file -> file.toString().endsWith(".java")).toList();
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new IllegalStateException(file.toString(), e);
        }
    }
}
