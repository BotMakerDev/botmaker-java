package com.botmaker.java.parse;

import com.botmaker.java.grammar.BotAnnotation;
import com.botmaker.java.grammar.SourceParser;
import com.botmaker.java.types.ProjectAnalyzer;
import org.eclipse.jdt.core.dom.CompilationUnit;

import java.nio.file.Path;
import java.util.List;

/**
 * How a bot's source is parsed when the host reads what it declares: against the project's resolved
 * classpath and source root, so a type, an annotation and a constant are what javac says they are.
 *
 * <p><b>Why bindings now (2026-09-24).</b> The value readers parsed syntax only, so that a bot whose pom did
 * not resolve still showed its parameters, and paid for it with names: a type was whatever its spelling
 * ended in. Binding recovery keeps the first property — an unresolved type comes back <em>recovered</em>,
 * not missing, and the unit still parses — and {@link BotAnnotation} and {@link SourceNames} fall back to the
 * unit's imports for exactly that case. What the classpath gives is the exact class.
 *
 * <p>{@link #SYNTAX} is the parser for text the host has just been handed and is about to rewrite, and for a
 * caller with no project: the same tree, no bindings, and every reader still exact through the imports.
 *
 * @param level      the Java the bot is written in
 * @param classpath  the project's resolved jars, or {@code null} for no bindings at all
 * @param sourceRoot the bot's {@code src/main/java}, or {@code null}; its other types resolve through it
 */
public record BotParser(JavaLevel level, List<String> classpath, Path sourceRoot) {

    /** Syntax only: no classpath, no bindings, JDT's newest Java. */
    public static final BotParser SYNTAX = new BotParser(JavaLevel.LATEST, null, null);

    public BotParser {
        level = level == null ? JavaLevel.LATEST : level;
        classpath = classpath == null ? null : List.copyOf(classpath);
    }

    /** A parser for a bot with no pom: JDT's newest Java. */
    public BotParser(List<String> classpath, Path sourceRoot) {
        this(JavaLevel.LATEST, classpath, sourceRoot);
    }

    /** The parser for {@code sources}, or {@link #SYNTAX} with none. On the thread its holder may be read from. */
    public static BotParser of(SourceSet sources) {
        return sources == null ? SYNTAX : new BotParser(sources.level(), sources.classpath(), sources.sourceRoot());
    }

    /** Whether units from this parser carry bindings. */
    public boolean binds() {
        return classpath != null;
    }

    /**
     * {@code source} as a unit. {@code file} names it for the binding resolver, which needs a unit name to
     * resolve a source's own declarations; {@code null} is fine for a unit that declares nothing another
     * reads.
     */
    public CompilationUnit parse(Path file, String source) {
        if (!binds()) return SourceParser.parse(level, source);
        return ProjectAnalyzer.createCompilationUnit(level, classpath, source, sourceRoot,
                file == null ? null : file.toAbsolutePath().toString());
    }

    /**
     * {@code source} as a unit with no bindings — for text the host is about to rewrite, which it reads
     * through its imports. The one syntax-only configuration for a bot's source; it was
     * {@code JavaParameterSource.parse} until 2026-09-27. At JDT's newest Java: a caller that knows the bot's
     * reads at {@link #syntax(JavaLevel, String)}.
     */
    public static CompilationUnit syntax(String source) {
        return syntax(JavaLevel.LATEST, source);
    }

    /** {@code source} as a unit with no bindings, read at the bot's {@code level}. */
    public static CompilationUnit syntax(JavaLevel level, String source) {
        return SourceParser.parse(level, source);
    }
}
