package com.botmaker.java.edit.helpers;

import com.botmaker.java.grammar.SourceParser;
import com.botmaker.java.parse.JavaLevel;
import com.botmaker.java.parse.SourceTokens;
import com.botmaker.java.grammar.TypeNodes;
import org.eclipse.jdt.core.compiler.ITerminalSymbols;
import org.eclipse.jdt.core.dom.*;

import java.util.List;

/**
 * Detects file and method types in the AST.
 */
public class FileTypeDetector {

    /**
     * Checks if a type declaration is a standalone enum file.
     */
    public static boolean isStandaloneEnumFile(AbstractTypeDeclaration typeDecl) {
        return typeDecl instanceof EnumDeclaration;
    }

    /**
     * Checks if a type declaration is a class file.
     */
    public static boolean isClassFile(AbstractTypeDeclaration typeDecl) {
        return typeDecl instanceof TypeDeclaration;
    }

    /**
     * The name of a Java entry point. A generated bot's is fixed and cannot be renamed (the rename field in
     * {@code MethodDeclarationBlock} refuses it), and {@code MainBlock} identifies itself by it — so it is one
     * constant rather than a literal in each of those places.
     */
    public static final String MAIN_METHOD = "main";

    /**
     * Checks if a method is a main method.
     */
    public static boolean isMainMethod(MethodDeclaration method) {
        if (!MAIN_METHOD.equals(method.getName().getIdentifier())) {
            return false;
        }
        if (!Modifier.isStatic(method.getModifiers())) {
            return false;
        }
        if (!Modifier.isPublic(method.getModifiers())) {
            return false;
        }
        if (method.parameters().size() != 1) {
            return false;
        }
        return true;
    }

    /**
     * Whether {@code method} is {@code public static void main(String[])}, or the {@code main()} a bot on Java 25
     * may start from. Java 25 also starts a {@code main} neither public nor static; no bot's entry is written so.
     */
    public static boolean isJvmMain(MethodDeclaration method) {
        int flags = method.getModifiers();
        if (!MAIN_METHOD.equals(method.getName().getIdentifier()) || !Modifier.isPublic(flags)
                || !Modifier.isStatic(flags) || !(method.getReturnType2() instanceof PrimitiveType primitive)
                || primitive.getPrimitiveTypeCode() != PrimitiveType.VOID || method.parameters().size() > 1) {
            return false;
        }
        if (method.parameters().isEmpty()) return true;
        return TypeNodes.isStringArray((SingleVariableDeclaration) method.parameters().getFirst());
    }

    /**
     * Whether any class in {@code unit}, its member classes too, declares {@link #isJvmMain}. A file that does not
     * parse may have lost the method from its tree, so {@code source}'s tokens answer for that one.
     */
    public static boolean holdsJvmMain(CompilationUnit unit, String source) {
        boolean[] found = {false};
        unit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodDeclaration method) {
                found[0] |= isJvmMain(method);
                return false;
            }
        });
        return found[0] || (SourceParser.hasSyntaxErrors(unit) && tokensSayMain(source));
    }

    /** Whether the tokens read {@code public static void main (}, in either order of the two modifiers. */
    private static boolean tokensSayMain(String source) {
        List<SourceTokens.Token> tokens = SourceTokens.of(JavaLevel.LATEST, source);
        for (int i = 3; i + 1 < tokens.size(); i++) {
            int first = tokens.get(i - 3).symbol();
            int second = tokens.get(i - 2).symbol();
            boolean modifiers = (first == ITerminalSymbols.TokenNamepublic && second == ITerminalSymbols.TokenNamestatic)
                    || (first == ITerminalSymbols.TokenNamestatic && second == ITerminalSymbols.TokenNamepublic);
            if (modifiers && tokens.get(i - 1).symbol() == ITerminalSymbols.TokenNamevoid
                    && MAIN_METHOD.equals(tokens.get(i).text())
                    && tokens.get(i + 1).symbol() == ITerminalSymbols.TokenNameLPAREN) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds the main method in a type declaration.
     */
    public static MethodDeclaration findMainMethod(TypeDeclaration type) {
        for (MethodDeclaration method : type.getMethods()) {
            if (isMainMethod(method)) {
                return method;
            }
        }
        return null;
    }
}
