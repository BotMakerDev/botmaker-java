package com.botmaker.java.edit.handlers;

import com.botmaker.java.grammar.SourceParser;
import com.botmaker.java.parse.JavaLevel;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Typed Java a bot's Java cannot hold is refused by its version, not as a typo and not by javac later. */
class StatementSourceLevelTest {

    private static final String SOURCE = "class A {\n    void run(int y) {\n        int x = 1;\n    }\n}\n";
    private static final String SWITCH = "int x = switch (y) { case 1 -> 2; default -> 3; };";

    @Test
    void codeOnlyANewerJavaReadsIsRefusedNamingTheBotsVersion() {
        JavaLevel.TooOld refused = assertThrows(JavaLevel.TooOld.class,
                () -> StatementSourceHandler.replace(SOURCE, firstStatement(JavaLevel.of("11")), SWITCH));
        assertEquals("That code needs a newer Java; this bot is on Java 11, so nothing was changed.",
                refused.getMessage());
    }

    @Test
    void theSameCodeIsWrittenInABotThatHoldsIt() {
        String written = StatementSourceHandler.replace(SOURCE, firstStatement(JavaLevel.of("25")), SWITCH);
        assertTrue(written.contains(SWITCH), written);
    }

    private static Statement firstStatement(JavaLevel level) {
        CompilationUnit unit = SourceParser.parse(level, SOURCE);
        MethodDeclaration run = ((TypeDeclaration) unit.types().getFirst()).getMethods()[0];
        return (Statement) run.getBody().statements().getFirst();
    }
}
