package com.botmaker.java.edit;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.Comment;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.core.dom.rewrite.TargetSourceRangeComputer;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Puts a statement on a line of a {@link Block}, a line as {@link BodyChildren} counts them, comments included,
 * through the rewrite's own list, so JDT lays it out as it does every node it writes.
 *
 * <p>A comment holds no slot in {@code statements()}, and JDT stretches a statement's range over the comments next
 * to it: inserted at the slot after a comment, the statement would be written before it. Here the rewrite's
 * {@link TargetSourceRangeComputer} draws the line instead: the statement above the comment reaches to the comment's
 * end, so what goes after it goes after the comment too, and the statement below starts at its own first character.
 *
 * <p>Two places have no statement to lean on, and there the comments themselves are written again, each as its own
 * source text: a body that holds only comments (it is replaced by a block of those comments and the new statement),
 * and a comment whose statement above leaves in the same rewrite (moved past the comment: its removal takes the
 * comment with it, and the comment is written back before the new statement). A comment is the one thing here written
 * as text, since a tree holds none.
 */
public final class BodyInserts {

    private BodyInserts() {}

    /**
     * Inserts {@code statement} at {@code childIndex} of {@code body} on {@code rewriter}. {@code source} is the text
     * {@code body}'s tree was parsed from, read only for a comment written again. A statement this rewrite removes
     * from {@code body} must be removed before this call, so the insert sees it gone.
     */
    public static void insert(ASTRewrite rewriter, Block body, int childIndex, Statement statement, String source) {
        ListRewrite list = rewriter.getListRewrite(body, Block.STATEMENTS_PROPERTY);
        int at = BodyChildren.statementIndex(body, childIndex);
        Comment anchor = BodyChildren.commentBefore(body, childIndex);
        if (anchor == null) {
            list.insertAt(statement, at, null);
            return;
        }
        List<?> statements = body.statements();
        Statement above = at > 0 ? (Statement) statements.get(at - 1) : null;
        Statement below = at < statements.size() ? (Statement) statements.get(at) : null;
        // A comment is a line of its own here, not part of the statement JDT would give it to: above reaches to the
        // anchor's end, and below starts at the first line after the anchor, a comment of its own or itself, so what
        // goes before below goes right under the anchor and the two ranges never overlap.
        if (above != null) Ranges.on(rewriter).endAt(above, end(anchor));
        if (below != null) Ranges.on(rewriter).startAt(below, firstLineAfter(body, anchor, below));
        if (above != null && list.getRewrittenList().contains(above)) {
            list.insertAfter(statement, above, null);
        } else if (above != null) {
            ASTNode after = above;
            for (Comment comment : commentsBetween(body, above, anchor)) {
                ASTNode written = rewriter.createStringPlaceholder(text(comment, source), ASTNode.EMPTY_STATEMENT);
                list.insertAfter(written, after, null);
                after = written;
            }
            list.insertAfter(statement, after, null);
        } else if (below != null) {
            list.insertBefore(statement, below, null);
        } else {
            rewriter.replace(body, commentsAnd(rewriter, body, childIndex, statement, source), null);
        }
    }

    /** A block of {@code body}'s comments, each written again, with {@code statement} on line {@code childIndex}. */
    private static Block commentsAnd(ASTRewrite rewriter, Block body, int childIndex, Statement statement,
                                     String source) {
        AST ast = body.getAST();
        Block block = ast.newBlock();
        @SuppressWarnings("unchecked")
        List<Statement> lines = block.statements();
        for (ASTNode line : BodyChildren.lines(body)) {
            if (line instanceof Comment comment) {
                lines.add((Statement) rewriter.createStringPlaceholder(text(comment, source), ASTNode.EMPTY_STATEMENT));
            }
        }
        lines.add(Math.min(childIndex, lines.size()), statement);
        return block;
    }

    /** The comments on the lines after {@code above} up to {@code anchor}, which ends them. */
    private static List<Comment> commentsBetween(Block body, Statement above, Comment anchor) {
        int from = above.getStartPosition() + above.getLength();
        return BodyChildren.lines(body).stream()
                .filter(line -> line instanceof Comment && line.getStartPosition() >= from
                        && line.getStartPosition() <= anchor.getStartPosition())
                .map(Comment.class::cast)
                .toList();
    }

    /** Where the first line after {@code anchor} starts: a comment before {@code below}, else {@code below} itself. */
    private static int firstLineAfter(Block body, Comment anchor, Statement below) {
        int after = end(anchor);
        return BodyChildren.lines(body).stream()
                .mapToInt(ASTNode::getStartPosition)
                .filter(start -> start >= after && start <= below.getStartPosition())
                .min()
                .orElse(below.getStartPosition());
    }

    private static String text(Comment comment, String source) {
        return source.substring(comment.getStartPosition(), end(comment));
    }

    private static int end(ASTNode node) {
        return node.getStartPosition() + node.getLength();
    }

    /** The ranges a rewrite writes nodes by, some drawn here, the rest as the rewrite had them. */
    private static final class Ranges extends TargetSourceRangeComputer {

        private final TargetSourceRangeComputer before;
        private final Map<ASTNode, Integer> starts = new IdentityHashMap<>();
        private final Map<ASTNode, Integer> ends = new IdentityHashMap<>();

        private Ranges(TargetSourceRangeComputer before) {
            this.before = before;
        }

        static Ranges on(ASTRewrite rewriter) {
            if (rewriter.getExtendedSourceRangeComputer() instanceof Ranges ranges) return ranges;
            Ranges ranges = new Ranges(rewriter.getExtendedSourceRangeComputer());
            rewriter.setTargetSourceRangeComputer(ranges);
            return ranges;
        }

        void startAt(ASTNode node, int start) {
            starts.put(node, start);
        }

        void endAt(ASTNode node, int end) {
            ends.merge(node, end, Math::max);
        }

        @Override
        public SourceRange computeSourceRange(ASTNode node) {
            SourceRange range = before.computeSourceRange(node);
            int start = starts.getOrDefault(node, range.getStartPosition());
            int end = Math.max(ends.getOrDefault(node, 0), range.getStartPosition() + range.getLength());
            return new SourceRange(start, end - start);
        }
    }
}
