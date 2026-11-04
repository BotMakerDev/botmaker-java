package com.botmaker.java.edit.helpers;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

import java.util.ArrayList;
import java.util.List;

/** The uses of a local variable inside its own scope. It was {@code Usages.local} until 2026-11-04. */
public final class LocalUses {

    private LocalUses() {}

    /**
     * Every other name in {@code declaration}'s scope that stands for the same local variable — a method's
     * parameter, a local, a lambda's or a loop's or a {@code catch}'s variable. The declaration itself is not
     * listed.
     *
     * <p>By binding wherever there is one; a name with <em>no</em> binding — an inferred lambda parameter in a
     * file whose target type did not resolve — is matched by spelling, inside the variable's own scope and
     * never into a nested lambda that declares the same name. That fallback is the only spelling match left
     * anywhere in "where is this used", and it cannot leave the scope that makes it right.
     */
    public static List<SimpleName> of(SimpleName declaration) {
        List<SimpleName> found = new ArrayList<>();
        ASTNode scope = scopeOf(declaration);
        if (scope == null) return found;
        String name = declaration.getIdentifier();
        IBinding declared = declaration.resolveBinding();
        scope.accept(new ASTVisitor() {
            @Override
            public boolean visit(LambdaExpression nested) {
                if (nested == scope) return true;
                return declared != null || nested.parameters().stream()
                        .map(LocalUses::lambdaParameterName)
                        .noneMatch(name::equals);
            }

            @Override
            public boolean visit(SimpleName node) {
                if (node == declaration || !node.getIdentifier().equals(name) || !canBeVariable(node)) return false;
                IBinding bound = node.resolveBinding();
                if (declared != null && bound != null) {
                    if (declared.isEqualTo(bound)) found.add(node);
                } else {
                    found.add(node);
                }
                return false;
            }
        });
        return found;
    }

    /**
     * The whole of the node a local variable can be named in: the lambda, loop, {@code catch} or method that
     * declares it, or the block of a local declaration. Null for a name that declares no local.
     */
    private static ASTNode scopeOf(SimpleName declaration) {
        if (!(declaration.getParent() instanceof VariableDeclaration declaring)
                || declaring.getParent() instanceof FieldDeclaration) {
            return null;
        }
        ASTNode holder = declaring.getParent();
        if (holder instanceof LambdaExpression || holder instanceof EnhancedForStatement
                || holder instanceof CatchClause || holder instanceof MethodDeclaration) {
            return holder;
        }
        if (holder instanceof VariableDeclarationExpression expression) return expression.getParent();
        if (holder instanceof VariableDeclarationStatement statement) return statement.getParent();
        // A pattern variable, a resource: the enclosing method is a superset of its scope, and a binding tells
        // the rest apart.
        for (ASTNode n = declaring; n != null; n = n.getParent()) {
            if (n instanceof MethodDeclaration || n instanceof LambdaExpression) return n;
        }
        return null;
    }

    private static String lambdaParameterName(Object parameter) {
        return parameter instanceof VariableDeclaration variable ? variable.getName().getIdentifier() : null;
    }

    /** False for the names that can never denote a variable: {@code foo()}, {@code x.foo}, {@code a.b}. */
    private static boolean canBeVariable(SimpleName node) {
        StructuralPropertyDescriptor location = node.getLocationInParent();
        return location != MethodInvocation.NAME_PROPERTY
                && location != FieldAccess.NAME_PROPERTY
                && location != SuperFieldAccess.NAME_PROPERTY
                && location != QualifiedName.NAME_PROPERTY
                && location != MethodDeclaration.NAME_PROPERTY;
    }
}
