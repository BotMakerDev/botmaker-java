package com.botmaker.java.grammar;

import com.botmaker.java.parse.JavaLevel;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.TypeDeclaration;

import java.util.Optional;

/**
 * A plugin's marked value in its holder class, found by its mark ({@code @SdkHostValue(SdkHostValue.Id.DONE)}) rather
 * than by its method's name: what a run reads and writes of its plugin's values of this PC (2026-11-15), with the
 * grammar of the bot's JVM and no compile.
 */
public final class MarkedReturns {

    private MarkedReturns() {}

    /**
     * The expression the method of {@code holder} marked {@code @<marker>(… <constant>)} returns, as written; empty
     * when the source does not parse, or no such method returns one expression.
     *
     * @param marker   the marker annotation's simple name, {@code SdkHostValue}
     * @param constant the id's constant, {@code DONE}
     */
    public static Optional<String> returned(JavaLevel level, String source, String holder, String marker,
                                            String constant) {
        CompilationUnit unit = SourceParser.parse(level, source);
        if (SourceParser.hasSyntaxErrors(unit)) return Optional.empty();
        return marked(unit, holder, marker, constant).flatMap(ParamValues::returned)
                .map(expression -> source.substring(expression.getStartPosition(),
                        expression.getStartPosition() + expression.getLength()));
    }

    /**
     * {@code source} with that method returning {@code value}, and the imports {@code value} names; empty when there
     * is no such method or it is not one {@code return}.
     */
    public static Optional<String> withReturned(JavaLevel level, String source, String holder, String marker,
                                                String constant, JavaValue value) {
        CompilationUnit unit = SourceParser.parse(level, source);
        if (SourceParser.hasSyntaxErrors(unit)) return Optional.empty();
        return marked(unit, holder, marker, constant)
                .flatMap(method -> ParamValues.withReturned(level, source, holder, method.getName().getIdentifier(),
                        value));
    }

    private static Optional<MethodDeclaration> marked(CompilationUnit unit, String holder, String marker,
                                                      String constant) {
        for (Object each : unit.types()) {
            if (!(each instanceof TypeDeclaration type) || !type.getName().getIdentifier().equals(holder)) continue;
            for (MethodDeclaration method : type.getMethods()) {
                if (method.parameters().isEmpty() && marks(method, marker, constant)) return Optional.of(method);
            }
        }
        return Optional.empty();
    }

    private static boolean marks(MethodDeclaration method, String marker, String constant) {
        for (Object modifier : method.modifiers()) {
            if (modifier instanceof SingleMemberAnnotation mark && last(mark.getTypeName()).equals(marker)
                    && mark.getValue() instanceof Name id && last(id).equals(constant)) {
                return true;
            }
        }
        return false;
    }

    /** {@code SdkHostValue.Id.DONE}'s last part, {@code DONE}. */
    private static String last(Name name) {
        String written = name.getFullyQualifiedName();
        return written.substring(written.lastIndexOf('.') + 1);
    }
}
