package com.botmaker.java.grammar;

import com.botmaker.java.parse.JavaLevel;
import org.eclipse.jdt.core.dom.ASTMatcher;
import org.eclipse.jdt.core.dom.ArrayType;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.NameQualifiedType;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.QualifiedType;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.UnionType;
import org.eclipse.jdt.core.dom.WildcardType;

import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * Questions about a type as the tree holds it, so that nothing compares what a node prints.
 *
 * <p>A node's {@code toString()} is JDT's debugging output: it writes {@code Map<String,Point>} with no space, keeps
 * a type annotation, and calls {@code java.util.List} and an imported {@code List} two types and two different
 * {@code Point}s one. Each answer here comes from the node's shape and from what its name resolves to:
 * {@link SourceNames}, the binding when the file was parsed against its classpath, and otherwise its imports.
 */
public final class TypeNodes {

    private TypeNodes() {}

    /** Two written types, each as its own file resolves it: {@code List<Point>} and {@code java.util.List<a.Point>}. */
    public static boolean same(Type one, Type other) {
        return alike(one, other, TypeNodes::sameClass);
    }

    /**
     * Whether {@code node} is the type spelled {@code wanted}. A name that starts with its package is resolved through
     * {@code node}'s file, so {@code Point} under {@code import b.Point} is not {@code a.Point}. Any other says no
     * package, and any class that ends so is it. Type arguments count: {@code List<String>} is not {@code List}. Text
     * that is no type answers no.
     */
    public static boolean writes(Type node, String wanted) {
        if (node == null || wanted == null) return false;
        String text = wanted.strip();
        if ("void".equals(text)) return isVoid(node);
        String written = written(node);
        if (written.equals(text)) return true;
        Type parsed = SourceParser.type(JavaLevel.LATEST, text);
        if (parsed == null) return false;
        return written.equals(written(parsed)) || alike(node, parsed, TypeNodes::spells);
    }

    /**
     * Whether {@code node} names the class {@code canonical}, whatever its type arguments. A nested class may come by
     * its binary name, {@code a.Outer$Inner}, or past its package, {@code Map.Entry}.
     */
    public static boolean names(Type node, String canonical) {
        String wanted = canonical.replace('$', '.');
        return switch (node) {
            case null -> false;
            case ParameterizedType generic -> names(generic.getType(), wanted);
            case SimpleType simple when startsWithPackage(wanted) -> SourceNames.refersTo(simple.getName(), wanted);
            default -> named(node).map(Named::name)
                    .filter(name -> name.equals(wanted) || (!startsWithPackage(wanted) && name.endsWith("." + wanted)))
                    .isPresent();
        };
    }

    public static boolean isVoid(Type node) {
        return node instanceof PrimitiveType primitive && primitive.getPrimitiveTypeCode() == PrimitiveType.VOID;
    }

    /** Whether {@code parameter} is one {@code String} array, however written: {@code String[] a}, {@code a[]}, {@code ...}. */
    public static boolean isStringArray(SingleVariableDeclaration parameter) {
        Type type = parameter.getType();
        Type element = type instanceof ArrayType array ? array.getElementType() : type;
        int dimensions = (type instanceof ArrayType array ? array.getDimensions() : 0)
                + parameter.getExtraDimensions() + (parameter.isVarargs() ? 1 : 0);
        return dimensions == 1 && names(element, "java.lang.String");
    }

    /**
     * The type as a person reads it, {@code Map<String, Point>}, with the names its file wrote and the spacing of
     * {@code TypeNames.label}. For showing and carrying a type, never for comparing one.
     */
    public static String written(Type type) {
        return switch (type) {
            case null -> "";
            case ParameterizedType generic -> written(generic.getType()) + "<" + joined(generic.typeArguments(), ", ") + ">";
            case ArrayType array -> written(array.getElementType()) + "[]".repeat(array.getDimensions());
            case UnionType union -> joined(union.types(), " | ");
            case WildcardType wildcard -> wildcard.getBound() == null ? "?"
                    : "? " + (wildcard.isUpperBound() ? "extends " : "super ") + written(wildcard.getBound());
            case SimpleType simple -> simple.getName().getFullyQualifiedName();
            default -> type.toString();
        };
    }

    /** What {@code parameter} declares, array parts outside its type node too: {@code int x[]} and {@code int... x} are {@code int[]}. */
    public static String written(SingleVariableDeclaration parameter) {
        return written(parameter.getType()) + "[]".repeat(parameter.getExtraDimensions() + (parameter.isVarargs() ? 1 : 0));
    }

    /**
     * The name to know {@code type} by when the file has no bindings: a single-type import makes a class whole, and
     * {@code bare} says what a name the file did not make whole is. Type arguments stay as the file wrote them.
     */
    public static String resolvedName(Type type, UnaryOperator<String> bare) {
        return switch (type) {
            case null -> "";
            case ParameterizedType generic ->
                    resolvedName(generic.getType(), bare) + "<" + joined(generic.typeArguments(), ", ") + ">";
            case ArrayType array -> resolvedName(array.getElementType(), bare) + "[]".repeat(array.getDimensions());
            default -> named(type).map(name -> name.whole() ? name.name() : bare.apply(name.name()))
                    .orElseGet(() -> written(type));
        };
    }

    private static String joined(List<?> types, String separator) {
        return types.stream().map(each -> written((Type) each)).collect(Collectors.joining(separator));
    }

    /** The two trees alike in shape, with {@code classes} deciding each pair of class names. */
    private static boolean alike(Type one, Type other, BiPredicate<Type, Type> classes) {
        return switch (one) {
            case null -> other == null;
            case ParameterizedType generic when other instanceof ParameterizedType theirs ->
                    alike(generic.getType(), theirs.getType(), classes)
                            && alikeAll(generic.typeArguments(), theirs.typeArguments(), classes);
            case ArrayType array when other instanceof ArrayType theirs -> array.getDimensions() == theirs.getDimensions()
                    && alike(array.getElementType(), theirs.getElementType(), classes);
            case PrimitiveType primitive when other instanceof PrimitiveType theirs ->
                    primitive.getPrimitiveTypeCode() == theirs.getPrimitiveTypeCode();
            case WildcardType wildcard when other instanceof WildcardType theirs ->
                    wildcard.isUpperBound() == theirs.isUpperBound()
                            && alike(wildcard.getBound(), theirs.getBound(), classes);
            default -> other != null && classes.test(one, other);
        };
    }

    private static boolean alikeAll(List<?> one, List<?> other, BiPredicate<Type, Type> classes) {
        if (one.size() != other.size()) return false;
        for (int i = 0; i < one.size(); i++) {
            if (!alike((Type) one.get(i), (Type) other.get(i), classes)) return false;
        }
        return true;
    }

    /**
     * {@code wanted}, parsed from text, has no file of its own: it means what it spells, in {@code node}'s file. It
     * reaches here with no arguments of its own, so a {@code node} with some is another type.
     */
    private static boolean spells(Type node, Type wanted) {
        if (node instanceof ParameterizedType) return false;
        Optional<Named> name = named(wanted);
        return name.isPresent() ? names(node, name.get().name()) : node.subtreeMatch(new ASTMatcher(), wanted);
    }

    /**
     * Two class names, each as its own file resolves it. A binding on both sides is final. A whole name against a
     * simple one from a file with an on-demand import asks that file, so an imported {@code a.Foo} is not the
     * {@code Foo} of {@code import b.*}. Otherwise the simple name is one its file never says, as in a starting copy
     * that has not taken the value's import yet, and it matches by its last segments.
     */
    private static boolean sameClass(Type one, Type other) {
        ITypeBinding mineBound = one.resolveBinding();
        ITypeBinding theirsBound = other.resolveBinding();
        if (mineBound != null && theirsBound != null && !mineBound.isRecovered() && !theirsBound.isRecovered()) {
            return mineBound.getKey().equals(theirsBound.getKey());
        }
        Optional<Named> mineNamed = named(one);
        Optional<Named> theirsNamed = named(other);
        if (mineNamed.isEmpty() || theirsNamed.isEmpty()) return one.subtreeMatch(new ASTMatcher(), other);
        Named mine = mineNamed.get();
        Named theirs = theirsNamed.get();
        if (mine.whole() && theirs.whole()) return mine.name().equals(theirs.name());
        if (mine.whole() && other instanceof SimpleType simple && importsOnDemand(other)) {
            return SourceNames.refersTo(simple.getName(), mine.name());
        }
        if (theirs.whole() && one instanceof SimpleType simple && importsOnDemand(one)) {
            return SourceNames.refersTo(simple.getName(), theirs.name());
        }
        return mine.name().equals(theirs.name()) || mine.name().endsWith("." + theirs.name())
                || theirs.name().endsWith("." + mine.name());
    }

    private static boolean importsOnDemand(Type type) {
        return type.getRoot() instanceof CompilationUnit unit && unit.imports().stream()
                .anyMatch(each -> ((ImportDeclaration) each).isOnDemand() && !((ImportDeclaration) each).isStatic());
    }

    /** A class's name as written, and whether the file's imports made it whole. */
    private record Named(String name, boolean whole) {}

    /** {@code type}'s class name, its first segment resolved by a single-type import of its file; empty if no class. */
    private static Optional<Named> named(Type type) {
        String written = switch (type) {
            case SimpleType simple -> simple.getName().getFullyQualifiedName();
            case NameQualifiedType qualified -> qualified.getQualifier().getFullyQualifiedName() + "."
                    + qualified.getName().getIdentifier();
            case QualifiedType qualified when qualified.getQualifier() instanceof SimpleType outer ->
                    outer.getName().getFullyQualifiedName() + "." + qualified.getName().getIdentifier();
            default -> null;
        };
        if (written == null) return Optional.empty();
        int dot = written.indexOf('.');
        String first = dot < 0 ? written : written.substring(0, dot);
        if (type.getRoot() instanceof CompilationUnit unit) {
            for (Object each : unit.imports()) {
                ImportDeclaration imported = (ImportDeclaration) each;
                String name = imported.getName().getFullyQualifiedName();
                if (!imported.isOnDemand() && !imported.isStatic() && name.endsWith("." + first)) {
                    return Optional.of(new Named(name + written.substring(first.length()), true));
                }
            }
        }
        return Optional.of(new Named(written, startsWithPackage(written)));
    }

    /** A name written whole starts with its package, in lower case: {@code java.time.Duration}, not {@code Map.Entry}. */
    private static boolean startsWithPackage(String name) {
        return name.indexOf('.') > 0 && Character.isLowerCase(name.charAt(0));
    }
}
