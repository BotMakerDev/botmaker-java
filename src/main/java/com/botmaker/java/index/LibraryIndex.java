package com.botmaker.java.index;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The classes of a bot's libraries, read from their jars: what type lookups and menus ask after the bot's own
 * sources. Its holder scans and caches the jars; a reader only asks.
 */
public interface LibraryIndex {

    /** Every indexed class. */
    List<LibraryType> getAllTypes();

    /** The class with {@code simpleName}, when one is indexed. */
    Optional<LibraryType> findBySimpleName(String simpleName);

    /** The class with {@code qualifiedName}, when it is indexed. */
    Optional<LibraryType> findByQualifiedName(String qualifiedName);

    /** Whether {@code type} was read out of one of {@code jars} (absolute, normalised paths). */
    boolean isIn(LibraryType type, Set<Path> jars);

    /** The classes with at least one public static method that returns a value. */
    List<LibraryType> getStaticUtilityTypes();

    /** How many classes are indexed. A change means the index was rebuilt. */
    int totalTypes();
}
