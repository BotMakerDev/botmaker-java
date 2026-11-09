package com.botmaker.java.grammar;

import com.botmaker.java.parse.JavaLevel;
import org.eclipse.jdt.core.dom.Expression;

import java.util.LinkedHashMap;
import java.util.Map;
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
        if (source == null) return Optional.empty();
        synchronized (PARSED) {
            Optional<SourceNode> kept = PARSED.get(source);
            if (kept != null) return kept;
        }
        Optional<SourceNode> parsed = Optional.ofNullable(SourceParser.expression(JavaLevel.LATEST, source))
                .map(node -> new SourceNode(node, source));
        synchronized (PARSED) {
            PARSED.put(source, parsed);
        }
        return parsed;
    }

    /** How many texts {@link #PARSED} keeps; the least recently read goes first. */
    private static final int KEPT = 2048;

    /**
     * Each text {@link #parse} read, by the text (2026-11-09): a map of 68 screens opened its 284 inputs' values from
     * their text on every read, and a new JDT parser for each was most of the 0.7 s it held the FX thread. A text
     * always parses to the same tree, and a reader only reads it: nothing writes into a parsed value's nodes, a
     * write builds a new tree and {@link JavaLevel#copy} copies into one.
     */
    private static final Map<String, Optional<SourceNode>> PARSED = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Optional<SourceNode>> eldest) {
            return size() > KEPT;
        }
    };

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
