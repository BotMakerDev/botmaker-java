package com.botmaker.java.parse;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;
import org.eclipse.jdt.core.dom.TextBlock;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * The Java feature version a bot's source is read and written at (2026-11-04): the pom's
 * {@code maven.compiler.release}, or, in a running bot, the JVM's own. Every parse and every new tree takes one, so
 * a bot on an older Java is read as that Java reads it — {@code int record = 1;} is a variable before Java 16 — and a
 * write it cannot hold is refused by name rather than by JDT's {@code UnsupportedOperationException}.
 *
 * <p>The one place that names JDT's latest level ({@code JavaLayersTest}). A level JDT does not support is the
 * nearest it does: above the newest, the newest; below the oldest, the oldest.
 *
 * @param feature the feature version, {@code 25} for Java 25, within what the pinned JDT supports
 */
public record JavaLevel(int feature) {

    private static final List<Integer> SUPPORTED = AST.getAllSupportedVersions();

    private static final int LOWEST = SUPPORTED.getFirst();

    private static final int HIGHEST = AST.getJLSLatest();

    /** JDT's newest level: a source with no pom, a library's source, a fragment no bot holds. */
    public static final JavaLevel LATEST = new JavaLevel(HIGHEST);

    /** A release as a pom writes it, digits only; anything else names no level. */
    private static final Pattern RELEASE = Pattern.compile("[1-9][0-9]{0,2}");

    /** The compiler options of each level, built once: {@code JavaCore.getOptions()} rebuilds its map on each call. */
    private static final Map<Integer, Map<String, String>> OPTIONS = new ConcurrentHashMap<>();

    public JavaLevel {
        feature = Math.clamp(feature, LOWEST, HIGHEST);
    }

    /** The level a pom's {@code maven.compiler.release} names; absent or malformed is {@link #LATEST}. */
    public static JavaLevel of(String release) {
        if (release == null || !RELEASE.matcher(release.strip()).matches()) return LATEST;
        return new JavaLevel(Integer.parseInt(release.strip()));
    }

    /** The level {@code node}'s tree was made at, so a node built beside it is made at the same one. */
    public static JavaLevel of(ASTNode node) {
        return of(node.getAST());
    }

    /** The level {@code ast} was made at. */
    public static JavaLevel of(AST ast) {
        return new JavaLevel(ast.apiLevel());
    }

    /** The level of the JVM this code runs in: a running bot's own Java. */
    public static JavaLevel running() {
        return new JavaLevel(Runtime.version().feature());
    }

    /** The {@code AST.JLS*} constant a new tree or parser is made at. */
    public int jls() {
        return feature;
    }

    /** A new, empty tree at this level. */
    public AST newAst() {
        return AST.newAST(jls(), false);
    }

    /** JDT's name for this version, {@code "1.8"} for 8 and {@code "25"} for 25. */
    public String javaVersion() {
        return feature == 8 ? JavaCore.VERSION_1_8 : Integer.toString(feature);
    }

    /**
     * JDT's compiler options at this level, with doc comments read; unmodifiable, so a caller that adds an option
     * copies it. A parser left at JDT's default reads Java 1.3, where {@code enum} is a name and generics are errors.
     */
    public Map<String, String> compilerOptions() {
        return OPTIONS.computeIfAbsent(feature, f -> {
            Map<String, String> options = JavaCore.getOptions();
            JavaCore.setComplianceOptions(javaVersion(), options);
            options.put(JavaCore.COMPILER_DOC_COMMENT_SUPPORT, JavaCore.ENABLED);
            return Map.copyOf(options);
        });
    }

    /**
     * {@code node} as a node of {@code target}, whatever level each was made at: what every copy from one tree into
     * another goes through ({@code JavaLayersTest}). JDT copies only within one level — {@code ASTNode.copySubtree}
     * throws across two — while a value is built at {@link #LATEST} and the bot's tree is at the bot's level, so
     * across levels this copies property by property. Below Java 15 a text block becomes the plain literal of its
     * value; any other node or property {@code target}'s level lacks throws {@link TooOld}.
     */
    @SuppressWarnings("unchecked")
    public static <T extends ASTNode> T copy(AST target, T node) {
        if (node == null) return null;
        if (target.apiLevel() == node.getAST().apiLevel()) return (T) ASTNode.copySubtree(target, node);
        JavaLevel level = of(target);
        try {
            return (T) across(target, level, node);
        } catch (UnsupportedOperationException | IllegalArgumentException lacking) {
            throw new TooOld("That code needs a newer Java", level);
        }
    }

    @SuppressWarnings("unchecked")
    private static ASTNode across(AST target, JavaLevel level, ASTNode node) {
        if (node instanceof TextBlock block && !level.has(Feature.TEXT_BLOCKS)) {
            // Read as the compiler reads it: JDT's getLiteralValue keeps the indentation of a block built in a tree.
            String escaped = block.getEscapedValue();
            String body = escaped.substring(escaped.indexOf('\n') + 1, escaped.length() - 3);
            StringLiteral literal = target.newStringLiteral();
            literal.setLiteralValue(body.stripIndent().translateEscapes());
            return literal;
        }
        ASTNode copy = target.createInstance(node.getNodeType());
        List<?> held = copy.structuralPropertiesForType();
        for (Object each : node.structuralPropertiesForType()) {
            StructuralPropertyDescriptor property = (StructuralPropertyDescriptor) each;
            Object value = node.getStructuralProperty(property);
            if (!held.contains(property)) {
                // A property this level lacks is fine only while it says nothing: no child, no item, not set.
                boolean unset = value == null || Boolean.FALSE.equals(value) || value instanceof List<?> list && list.isEmpty();
                if (unset) continue;
                throw new TooOld("That code needs a newer Java", level);
            }
            if (property.isSimpleProperty()) {
                copy.setStructuralProperty(property, value);
            } else if (property.isChildProperty()) {
                if (value != null) copy.setStructuralProperty(property, across(target, level, (ASTNode) value));
            } else {
                List<ASTNode> into = (List<ASTNode>) copy.getStructuralProperty(property);
                for (Object child : (List<?>) value) into.add(across(target, level, (ASTNode) child));
            }
        }
        copy.setSourceRange(node.getStartPosition(), node.getLength());
        return copy;
    }

    /** Whether this level holds {@code feature}. */
    public boolean has(Feature needed) {
        return feature >= needed.since();
    }

    /** Throws {@link TooOld} when this level cannot hold {@code feature}: the refusal names both versions. */
    public void require(Feature needed) {
        if (!has(needed)) throw new TooOld(needed.what() + " needs Java " + needed.since(), this);
    }

    /** What a write needs of the language, and the first Java that has it. */
    public enum Feature {
        SWITCH_EXPRESSIONS(14, "A switch expression"),
        SWITCH_RULES(14, "A case -> rule"),
        TEXT_BLOCKS(15, "A text block"),
        RECORDS(16, "A record");

        private final int since;
        private final String what;

        Feature(int since, String what) {
            this.since = since;
            this.what = what;
        }

        public int since() {
            return since;
        }

        public String what() {
            return what;
        }
    }

    /** A write the bot's Java cannot hold; its message is the sentence the user reads. */
    public static final class TooOld extends IllegalStateException {

        public TooOld(String need, JavaLevel level) {
            super(need + "; this bot is on Java " + level.feature() + ", so nothing was changed.");
        }
    }
}
