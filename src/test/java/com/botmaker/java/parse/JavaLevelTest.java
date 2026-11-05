package com.botmaker.java.parse;

import org.eclipse.jdt.core.JavaCore;
import com.botmaker.java.grammar.SourceParser;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTMatcher;
import org.eclipse.jdt.core.dom.Expression;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A pom's {@code maven.compiler.release} as the level JDT reads and writes the bot at (2026-11-04). */
class JavaLevelTest {

    @Test
    void absentOrMalformedIsTheNewest() {
        assertEquals(JavaLevel.LATEST, JavaLevel.of((String) null));
        assertEquals(JavaLevel.LATEST, JavaLevel.of(""));
        assertEquals(JavaLevel.LATEST, JavaLevel.of("${java.version}"));
        assertEquals(JavaLevel.LATEST, JavaLevel.of("1.8"));
        assertEquals(JavaLevel.LATEST, JavaLevel.of("017"));
        assertEquals(AST.getJLSLatest(), JavaLevel.LATEST.feature());
    }

    @Test
    void aboveWhatJdtReadsIsItsNewest() {
        assertEquals(JavaLevel.LATEST, JavaLevel.of("999"));
    }

    @Test
    void belowWhatJdtReadsIsItsOldest() {
        assertEquals(AST.getAllSupportedVersions().getFirst(), JavaLevel.of("5").feature());
    }

    @Test
    void aSupportedReleaseIsItself() {
        JavaLevel eleven = JavaLevel.of(" 11 ");
        assertEquals(11, eleven.feature());
        assertEquals(11, eleven.jls());
        assertEquals(11, eleven.newAst().apiLevel());
        assertEquals("11", eleven.compilerOptions().get(JavaCore.COMPILER_SOURCE));
        assertEquals("1.8", JavaLevel.of("8").compilerOptions().get(JavaCore.COMPILER_SOURCE));
        assertEquals(JavaCore.ENABLED, eleven.compilerOptions().get(JavaCore.COMPILER_DOC_COMMENT_SUPPORT));
    }

    @Test
    void aFeatureTheLevelLacksIsRefusedByName() {
        JavaLevel eleven = JavaLevel.of("11");
        assertFalse(eleven.has(JavaLevel.Feature.RECORDS));
        assertTrue(JavaLevel.of("25").has(JavaLevel.Feature.RECORDS));
        JavaLevel.TooOld refused = assertThrows(JavaLevel.TooOld.class, () -> eleven.require(JavaLevel.Feature.RECORDS));
        assertEquals("A record needs Java 16; this bot is on Java 11, so nothing was changed.", refused.getMessage());
    }

    /** JDT's own copy throws across two levels; a value built at the newest lands in a bot's tree on Java 25. */
    @Test
    void aFragmentAtTheNewestCopiesIntoAnOlderTree() {
        Expression call = SourceParser.expression(JavaLevel.LATEST, "List.of(a.b(), \"x\", 1 + 2)");
        AST bot = JavaLevel.of("25").newAst();
        Expression copied = JavaLevel.copy(bot, call);
        assertSame(bot, copied.getAST());
        assertTrue(copied.subtreeMatch(new ASTMatcher(), call));
    }

    @Test
    void codeTheOlderTreeCannotHoldIsRefused() {
        Expression rule = SourceParser.expression(JavaLevel.LATEST, "switch (x) { case 1 -> 2; default -> 3; }");
        assertThrows(JavaLevel.TooOld.class, () -> JavaLevel.copy(JavaLevel.of("11").newAst(), rule));
    }
}
