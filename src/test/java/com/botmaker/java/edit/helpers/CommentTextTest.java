package com.botmaker.java.edit.helpers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommentTextTest {

    private static String written(String text) {
        return CommentText.written(text, false);
    }

    @Test
    void oneLineIsALineCommentAndKeepsWhatMeansNothingThere() {
        assertEquals("// renamed", written("renamed"));
        assertEquals("// a */ b", written("a */ b"));
    }

    @Test
    void codeOnItsLineKeepsItABlockComment() {
        assertEquals("/* renamed */", CommentText.written("renamed", true));
        assertEquals("/* a * / b */", CommentText.written("a */ b", true));
    }

    @Test
    void aLineBreakMakesABlockCommentThatItsTextCannotClose() {
        assertEquals("/* one\ntwo */", written("one\ntwo"));
        assertEquals("/* * /\n} */", written("*/\n}"));
        assertEquals("/* ** /\n* / */", written("**/\n*/"));
    }

    @Test
    void aLoneCarriageReturnEndsALineCommentToo() {
        assertEquals("/* one\rtwo */", written("one\rtwo"));
    }

    @Test
    void aUnicodeEscapeIsNotReadAsTheCharacterItNames() {
        assertEquals("// a\\ u000a b", written("a\\u000a b"));
        assertEquals("/* *\\ u002f\n */", written("*\\u002f\n"));
        assertEquals("// C:\\ users\\me", written("C:\\users\\me"));
    }

    @Test
    void anEvenRunOfBackslashesIsNoEscapeAndStaysAsTyped() {
        assertEquals("// C:\\\\users", written("C:\\\\users"));
        assertEquals("// C:\\\\\\ users", written("C:\\\\\\users"));
    }

    @Test
    void insideACommentOnlyWhatWouldEndItChanges() {
        assertEquals("plain", CommentText.inside("plain"));
        assertEquals("a * / b \\ u002a", CommentText.inside("a */ b \\u002a"));
    }
}
