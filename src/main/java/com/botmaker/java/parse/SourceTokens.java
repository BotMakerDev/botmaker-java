package com.botmaker.java.parse;

import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.compiler.IScanner;
import org.eclipse.jdt.core.compiler.ITerminalSymbols;
import org.eclipse.jdt.core.compiler.InvalidInputException;

import java.util.ArrayList;
import java.util.List;

/**
 * A source file's tokens, for the one case a tree cannot answer: a file that does not parse. JDT's recovery drops
 * whatever it cannot place, so a damaged tree missing a name says nothing about the file, while its tokens are all
 * still there and, as in a tree, no comment or string is read as code.
 */
public final class SourceTokens {

    private SourceTokens() {}

    /** One token: its {@link ITerminalSymbols} symbol, its text and its offset in the source. */
    public record Token(int symbol, String text, int start) {}

    /** Every token of {@code source} in order; what the scanner cannot read, such as an open string, is skipped. */
    public static List<Token> of(JavaLevel level, String source) {
        IScanner scanner = ToolFactory.createScanner(false, false, false, level.javaVersion());
        scanner.setSource(source.toCharArray());
        List<Token> tokens = new ArrayList<>();
        int unreadableEnd = -1;
        while (true) {
            try {
                int symbol = scanner.getNextToken();
                if (symbol == ITerminalSymbols.TokenNameEOF) return tokens;
                tokens.add(new Token(symbol, new String(scanner.getCurrentTokenSource()),
                        scanner.getCurrentTokenStartPosition()));
            } catch (InvalidInputException unreadable) {
                int end = scanner.getCurrentTokenEndPosition();
                if (end <= unreadableEnd) return tokens;
                unreadableEnd = end;
            }
        }
    }
}
