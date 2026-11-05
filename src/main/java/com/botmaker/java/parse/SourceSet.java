package com.botmaker.java.parse;

import com.botmaker.java.grammar.SourceParser;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A read-only view of a bot's sources: what this library asks of a project, so that it holds none. The holder
 * of the project implements it; nothing here edits a file or keeps a tree.
 */
public interface SourceSet {

    /** Every source file of the bot, as the paths {@link #unit} and {@link #text} take. */
    Stream<Path> files();

    /** The tree the holder has already parsed for {@code file}; empty for a file it has not. */
    Optional<CompilationUnit> unit(Path file);

    /** The current text of {@code file}, which is ahead of the disk while an edit is unsaved. */
    Optional<String> text(Path file);

    /** The classpath a tree of this bot binds against. */
    List<String> classpath();

    /** The root of the bot's sources; {@code null} when it has none on disk. */
    Path sourceRoot();

    /** The Java the bot is written in: its pom's {@code maven.compiler.release}, read by the holder. */
    JavaLevel level();

    /**
     * The tree of {@code file}, parsed with bindings when the holder has none yet. A holder that keeps trees
     * keeps this one too, so a second ask parses nothing. By default it is {@link #unit}: no parse.
     */
    default Optional<CompilationUnit> parsedUnit(Path file) {
        return unit(file);
    }

    /** A parser that resolves bindings against this bot's classpath and sources. */
    default ASTParser boundParser() {
        return SourceParser.bound(level(), classpath(), sourceRoot());
    }

    /**
     * The qualified name that a library bound to the bot claims for {@code simpleName}, if any. An import asks
     * for it after the bot's own sources and before the JDK, so a library's {@code Point} wins over
     * {@code java.awt.Point}. Empty by default.
     */
    default Optional<String> ownerOf(String simpleName) {
        return Optional.empty();
    }

    /**
     * The source of a fresh value that a library bound to the bot declares for the type {@code canonicalName},
     * if any. A new slot of that type is filled with it. Empty by default.
     */
    default Optional<String> freshValue(String canonicalName) {
        return Optional.empty();
    }

    /** The class a source file is named for: {@code Movement} for {@code Movement.java}. */
    static String className(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }
}
