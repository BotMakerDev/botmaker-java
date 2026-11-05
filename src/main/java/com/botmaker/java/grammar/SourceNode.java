package com.botmaker.java.grammar;

import com.botmaker.java.parse.JavaLevel;
import org.eclipse.jdt.core.dom.Expression;

import java.util.Optional;

/**
 * An expression and the text its offsets index: the file it was parsed out of, or the one expression.
 *
 * <p>A JDT node does not carry the text it came from, and a part the grammar cannot read is shown as it was
 * written. {@code node.toString()} is JDT's own reprint, not what the person wrote. It is used only when
 * {@code text} is {@code null}: a canvas slot, whose {@code ValueSlot.source()} has always shown that.
 */
public record SourceNode(Expression node, String text) {

    /**
     * {@code source} parsed as exactly one expression ({@link SourceParser#expression}), with its text, or empty. A
     * value's text alone is no bot's, so it is read at {@link JavaLevel#LATEST}; copied into a bot's tree it takes
     * that tree's level, and a node the level cannot hold is refused there.
     */
    public static Optional<SourceNode> parse(String source) {
        return Optional.ofNullable(SourceParser.expression(JavaLevel.LATEST, source))
                .map(node -> new SourceNode(node, source));
    }

    /** The node's own slice of {@link #text}, as it was written. */
    public String source() {
        if (text == null) return node.toString();
        int start = node.getStartPosition();
        return text.substring(start, start + node.getLength());
    }

    /** A node inside this one, over the same text. */
    public SourceNode child(Expression child) {
        return new SourceNode(child, text);
    }
}
