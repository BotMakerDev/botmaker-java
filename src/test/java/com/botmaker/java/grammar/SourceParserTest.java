package com.botmaker.java.grammar;

import com.botmaker.java.parse.JavaLevel;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.junit.jupiter.api.Test;

import static com.botmaker.java.parse.JavaLevel.LATEST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Text to tree has one entry (2026-11-04): a fragment comes back only when it is well-formed, whole. */
class SourceParserTest {

    private static final JavaLevel JAVA_11 = JavaLevel.of("11");

    @Test
    void anExpressionIsWholeOrNothing() {
        assertInstanceOf(MethodInvocation.class, SourceParser.expression(LATEST, "  Duration.ofSeconds(5) "));
        assertNull(SourceParser.expression(LATEST, "not java"), "a made-up name is no expression");
        assertNull(SourceParser.expression(LATEST, "1 + 2 junk"), "trailing text is refused");
        assertNull(SourceParser.expression(LATEST, "   "));
        assertNull(SourceParser.expression(LATEST, null));
    }

    @Test
    void anExpressionMovesIntoTheAstItIsFor() {
        AST ast = LATEST.newAst();
        Expression moved = SourceParser.expression(ast, "switch (x) { case 1 -> \"a\"; default -> \"b\"; }");
        assertNotNull(moved, "the newest Java is read");
        assertSame(ast, moved.getAST());
    }

    @Test
    void anExpressionIsReadAtTheLevelOfTheAstItIsFor() {
        assertNull(SourceParser.expression(JAVA_11.newAst(), "switch (x) { case 1 -> \"a\"; default -> \"b\"; }"),
                "Java 11 has no switch expression");
    }

    @Test
    void typesAndStatementsParse() {
        assertEquals("Map<String,int[]>", SourceParser.type(LATEST, "Map<String, int[]>").toString());
        assertNull(SourceParser.type(LATEST, "List<"));
        assertEquals(2, SourceParser.statements(LATEST, "int a = 1; a++;").statements().size());
        assertNull(SourceParser.statements(LATEST, "int a = ;"));
        assertInstanceOf(CastExpression.class, SourceParser.expression(LATEST, "(int) x"));
    }

    @Test
    void recordIsANameBeforeRecords() {
        assertFalse(SourceParser.hasSyntaxErrors(SourceParser.parse(JAVA_11, "class A { int record = 1; }")));
    }

    @Test
    void aRecordDeclarationNeedsJava16() {
        String source = "record Point(int x, int y) {}";
        assertTrue(SourceParser.hasSyntaxErrors(SourceParser.parse(JAVA_11, source)), "Java 11 has no records");
        assertFalse(SourceParser.hasSyntaxErrors(SourceParser.parse(JavaLevel.of("25"), source)));
    }
}
