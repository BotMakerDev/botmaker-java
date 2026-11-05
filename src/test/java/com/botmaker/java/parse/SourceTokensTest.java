package com.botmaker.java.parse;

import org.eclipse.jdt.core.compiler.ITerminalSymbols;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class SourceTokensTest {

    private static List<String> names(String source) {
        return SourceTokens.of(JavaLevel.LATEST, source).stream()
                .filter(token -> token.symbol() == ITerminalSymbols.TokenNameIdentifier)
                .map(SourceTokens.Token::text).toList();
    }

    @Test
    void aCommentAStringAndATextBlockAreNoTokensOfCode() {
        assertEquals(List.of("a", "b"), names("""
                a // one
                /* two */
                "three" b
                String.class
                """.replace("String.class", "") + "\"\"\"\n four\n\"\"\""));
    }

    @Test
    void whatCannotBeReadIsSkippedAndTheRestRead() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            assertEquals(List.of("a", "b"), names("a \"open\nb"));
            assertEquals(List.of("a"), names("a \"open"));
            assertEquals(List.of("a", "b"), names("a # b"));
        });
    }

    @Test
    void anOffsetIsWhereTheTokenStarts() {
        assertEquals(4, SourceTokens.of(JavaLevel.LATEST, "/**/name").getFirst().start());
    }
}
