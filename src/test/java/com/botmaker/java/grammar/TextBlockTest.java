package com.botmaker.java.grammar;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.CompilationUnit;
import com.botmaker.java.parse.JavaLevel;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.TextBlock;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.botmaker.java.parse.JavaLevel.LATEST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/** A string of several lines is written as a text block holding exactly it; one line stays a plain literal. */
class TextBlockTest {

    private static final AST AST_ = AST.newAST(AST.getJLSLatest(), false);

    @Test
    void aTextBlockHoldsExactlyTheValueItWasWrittenFrom() {
        for (String value : List.of("a\nb", "Loot fills over 8 h.\n\nIts badge shows on Home.", "end space \nnext",
                "quotes \"\"\" and a \\ backslash\nand \"one\"", "tab\there\nlast line\n")) {
            Expression written = JdkLiterals.node(AST_, String.class, value).orElseThrow();
            TextBlock block = assertInstanceOf(TextBlock.class, written, value);
            assertEquals(value, JdkLiterals.value(block), block.getEscapedValue());
        }
    }

    /** A run of quotes longer than three never closes the block early: the file still parses to the same value. */
    @Test
    void aRunOfQuotesStaysInsideTheBlock() {
        for (String value : List.of("four \"\"\"\"\nquotes", "five \"\"\"\"\"\nand six \"\"\"\"\"\"", "a\nends in \"",
                "a\nends in \"\"", "\"\"\"\"\nstarts")) {
            String escaped = JdkLiterals.textBlock(AST_, value).getEscapedValue();
            CompilationUnit unit = SourceParser.parse(LATEST, "class X { String s = " + escaped + "; }");
            assertFalse(SourceParser.hasSyntaxErrors(unit), escaped);
            TypeDeclaration type = (TypeDeclaration) unit.types().getFirst();
            VariableDeclarationFragment field =
                    (VariableDeclarationFragment) type.getFields()[0].fragments().getFirst();
            TextBlock read = assertInstanceOf(TextBlock.class, field.getInitializer(), escaped);
            assertEquals(value, JdkLiterals.value(read), escaped);
        }
    }

    /** A bot before Java 15 holds no text block: one copied into its tree, alone or as an argument, is a literal. */
    @Test
    void aTextBlockCopiedIntoAJava11TreeIsAPlainLiteral() {
        AST java11 = JavaLevel.of("11").newAst();
        String value = "Loot fills over 8 h.\n\nIts \"badge\" shows.";
        JavaValue alone = JavaValue.built(JdkLiterals.textBlock(AST_, value), List.of());
        StringLiteral copied = assertInstanceOf(StringLiteral.class, alone.copyInto(java11));
        assertEquals(value, copied.getLiteralValue());

        MethodInvocation call = AST_.newMethodInvocation();
        call.setName(AST_.newSimpleName("say"));
        call.arguments().add(JdkLiterals.textBlock(AST_, value));
        MethodInvocation copiedCall = assertInstanceOf(MethodInvocation.class,
                JavaValue.built(call, List.of()).copyInto(java11));
        assertEquals(value, assertInstanceOf(StringLiteral.class, copiedCall.arguments().getFirst()).getLiteralValue());
        assertInstanceOf(TextBlock.class, alone.copyInto(LATEST.newAst()), "from Java 15 it stays a block");
    }

    @Test
    void oneLineStaysAPlainString() {
        assertInstanceOf(StringLiteral.class, JdkLiterals.node(AST_, String.class, "Tavern").orElseThrow());
    }
}
