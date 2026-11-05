package com.botmaker.java.edit.helpers;

import java.util.regex.Pattern;

/**
 * Text from outside the program, written so it stays a comment: what the author typed in a comment block, a name in a
 * generated doc comment. Fixed text BotMaker wrote itself needs none of this.
 *
 * <p>JDT has no comment node to rewrite, so a comment is the one place Java is held as text, and its text can end the
 * comment: {@code *&#47;} closes a block comment, a line break ends a line comment, and a unicode escape counts as
 * the character it names before the compiler looks for a comment at all. One with no valid digits, as in the Windows
 * path {@code C:\}{@code users}, does not even compile.
 */
public final class CommentText {

    /** A backslash that starts a unicode escape: {@code u} after an odd run of backslashes (JLS 3.3). */
    private static final Pattern ESCAPE = Pattern.compile("(?<!\\\\)((?:\\\\\\\\)*)\\\\u");

    private CommentText() {
    }

    /**
     * {@code text} as a whole comment. One line stays a line comment, where {@code *&#47;} means nothing and is kept as
     * typed, unless code follows on its line, which a line comment would swallow. Any line break makes it a block
     * comment.
     */
    public static String written(String text, boolean codeFollows) {
        boolean oneLine = !text.contains("\n") && !text.contains("\r");
        return oneLine && !codeFollows ? "// " + withoutEscapes(text) : "/* " + inside(text) + " */";
    }

    /** {@code text} safe inside a block or doc comment. */
    public static String inside(String text) {
        return withoutEscapes(text).replace("*/", "* /");
    }

    private static String withoutEscapes(String text) {
        return ESCAPE.matcher(text).replaceAll("$1\\\\ u");
    }
}
