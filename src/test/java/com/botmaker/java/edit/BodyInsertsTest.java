package com.botmaker.java.edit;

import com.botmaker.java.edit.helpers.AstRewriteHelper;
import com.botmaker.java.grammar.SourceParser;
import com.botmaker.java.parse.JavaLevel;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.junit.jupiter.api.Test;

import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class BodyInsertsTest {

    private static String body(String... lines) {
        StringBuilder text = new StringBuilder("class A {\n    void m() {\n");
        for (String line : lines) text.append(line.isEmpty() ? "" : "        ").append(line).append('\n');
        return text.append("    }\n}\n").toString();
    }

    /** {@code source} with a call to {@code added()} put on line {@code childIndex} of {@code m}'s body. */
    private static String inserted(String source, int childIndex) {
        return rewritten(source, (rewriter, body) -> {
            AST ast = body.getAST();
            MethodInvocation call = ast.newMethodInvocation();
            call.setName(ast.newSimpleName("added"));
            BodyInserts.insert(rewriter, body, childIndex, ast.newExpressionStatement(call), source);
        });
    }

    private static String rewritten(String source, BiConsumer<ASTRewrite, Block> edit) {
        CompilationUnit unit = SourceParser.parse(JavaLevel.LATEST, source);
        Block body = ((TypeDeclaration) unit.types().getFirst()).getMethods()[0].getBody();
        ASTRewrite rewriter = ASTRewrite.create(unit.getAST());
        edit.accept(rewriter, body);
        String result = AstRewriteHelper.applyRewrite(rewriter, source);
        assertFalse(SourceParser.hasSyntaxErrors(SourceParser.parse(JavaLevel.LATEST, result)), result);
        return result;
    }

    @Test
    void aStatementAfterACommentBetweenTwoGoesAfterIt() {
        assertEquals(body("a();", "// note", "added();", "b();"), inserted(body("a();", "// note", "b();"), 2));
        assertEquals(body("a();", "added();", "// note", "b();"), inserted(body("a();", "// note", "b();"), 1),
                "before the comment stays before it");
    }

    @Test
    void aStatementAfterACommentThatIsTheLastLineGoesLast() {
        assertEquals(body("a();", "// note", "added();"), inserted(body("a();", "// note"), 2));
    }

    @Test
    void aStatementAfterACommentThatIsTheOnlyLineGoesUnderIt() {
        assertEquals(body("// note", "added();"), inserted(body("// note"), 1));
        assertEquals(body("added();", "// note"), inserted(body("// note"), 0));
    }

    @Test
    void aStatementAfterAFirstCommentGoesBetweenItAndTheStatement() {
        assertEquals(body("// note", "added();", "b();"), inserted(body("// note", "b();"), 1));
        assertEquals(body("// a", "added();", "// b", "b();"), inserted(body("// a", "// b", "b();"), 1),
                "right under its comment, not under the next one");
        assertEquals(body("x();", "// a", "added();", "// b", "b();"),
                inserted(body("x();", "// a", "// b", "b();"), 2));
    }

    @Test
    void aStatementAfterAnEndOfLineCommentGoesOnTheNextLine() {
        assertEquals(body("a(); // tail", "added();", "b();"), inserted(body("a(); // tail", "b();"), 2));
    }

    @Test
    void aStatementMovedPastTheCommentUnderItLandsUnderTheComment() {
        String source = body("a();", "// note", "/* two */", "b();");
        String moved = rewritten(source, (rewriter, body) -> {
            Statement a = (Statement) body.statements().getFirst();
            ListRewrite list = rewriter.getListRewrite(body, Block.STATEMENTS_PROPERTY);
            Statement copy = (Statement) JavaLevel.copy(body.getAST(), a);
            list.remove(a, null);
            BodyInserts.insert(rewriter, body, 3, copy, source);
        });
        assertEquals(body("// note", "/* two */", "a();", "b();"), moved);
    }
}
