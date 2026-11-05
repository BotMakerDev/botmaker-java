package com.botmaker.java.grammar;

import com.botmaker.java.edit.handlers.TryHandler;
import com.botmaker.java.edit.palette.SignatureType;
import com.botmaker.java.edit.refactor.SignatureMigration;
import com.botmaker.java.parse.JavaLevel;
import com.botmaker.java.types.ProjectAnalyzer;
import com.botmaker.java.types.ResolvedType;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A type is compared as a type: its shape and what its name resolves to, never what the node prints. */
class TypeNodesTest {

    /** The return type of the only method of a class in a file with these imports. */
    private static Type returned(String imports, String type) {
        return method(imports, type + " f() { return null; }").getReturnType2();
    }

    private static MethodDeclaration method(String imports, String member) {
        CompilationUnit unit = SourceParser.parse(JavaLevel.LATEST,
                "package com.bot;\n" + imports + "\nclass Subject { " + member + " }\n");
        return ((TypeDeclaration) unit.types().getFirst()).getMethods()[0];
    }

    private static SingleVariableDeclaration parameter(String imports, String declared) {
        return (SingleVariableDeclaration) method(imports, "void f(" + declared + ") { }").parameters().getFirst();
    }

    @Test
    void aFullNameAndAnImportedOneAreOneType() {
        assertTrue(TypeNodes.same(returned("", "java.util.List<a.Point>"),
                returned("import java.util.List;\nimport a.Point;", "List<Point>")));
        assertFalse(TypeNodes.same(returned("import a.Point;", "Point"), returned("import b.Point;", "Point")));
    }

    @Test
    void aFileWritesTheTypeItsNamesResolveTo() {
        String imports = "import java.util.Map;\nimport a.Point;";
        assertTrue(TypeNodes.writes(returned(imports, "Map<String,   Point>"), "java.util.Map<java.lang.String, a.Point>"));
        assertTrue(TypeNodes.writes(returned(imports, "Map<String,Point>"), "Map<String, Point>"));
        assertFalse(TypeNodes.writes(returned(imports, "Point"), "b.Point"), "another class of the same simple name");
        assertFalse(TypeNodes.writes(returned(imports, "Map<String, Point>"), "java.util.Map<java.lang.String, b.Point>"));
    }

    @Test
    void aBareNameSaysNoPackage() {
        assertTrue(TypeNodes.writes(returned("import a.Outcome;", "Outcome"), "Outcome"));
        assertTrue(TypeNodes.writes(returned("", "a.Outcome"), "Outcome"));
        assertFalse(TypeNodes.writes(returned("", "MyOutcome"), "Outcome"));
    }

    @Test
    void typeArgumentsArePartOfTheType() {
        assertFalse(TypeNodes.writes(returned("import java.util.List;", "List<String>"), "java.util.List"));
        assertFalse(TypeNodes.writes(returned("import java.util.List;", "List<String>"), "List"));
        assertFalse(TypeNodes.writes(returned("", "Map<K, List<X>>"), "Map<K, List>"));
        assertFalse(TypeNodes.writes(returned("import java.util.List;", "List"), "java.util.List<String>"));
    }

    @Test
    void voidAndWhatIsNoTypeAreAnswered() {
        assertTrue(TypeNodes.writes(returned("", "void"), "void"));
        assertFalse(TypeNodes.writes(returned("", "int"), "void"));
        assertFalse(TypeNodes.writes(returned("", "int"), "not a type"));
        assertFalse(TypeNodes.writes(null, "int"));
    }

    @Test
    void aClassIsNamedWhateverItsArguments() {
        String canonical = "java.util.ArrayList";
        assertTrue(TypeNodes.names(returned("import java.util.ArrayList;", "ArrayList<String>"), canonical));
        assertTrue(TypeNodes.names(returned("import java.util.*;", "ArrayList"), canonical));
        assertTrue(TypeNodes.names(returned("", "java.util.ArrayList<String>"), canonical));
        assertFalse(TypeNodes.names(returned("import java.util.*;", "ArrayListOfMine"), canonical));
        assertFalse(TypeNodes.names(returned("import a.ArrayList;", "ArrayList"), canonical));
    }

    @Test
    void aNestedClassIsNamedByItsBinaryNameOrPastItsPackage() {
        assertTrue(TypeNodes.names(returned("import a.Outer.Inner;", "Inner<String>"), "a.Outer$Inner"));
        assertTrue(TypeNodes.names(returned("import java.util.Map;", "Map.Entry"), "Map.Entry"));
        assertTrue(TypeNodes.names(returned("import java.util.Map.Entry;", "Entry"), "Map.Entry"));
        assertFalse(TypeNodes.names(returned("", "My.Entry"), "Map.Entry"));
    }

    @Test
    void aTypedComparesByItsWholeName() {
        assertEquals("java.time.Duration", new SignatureType.Typed(Duration.class, "null").comparedName());
        assertEquals("java.util.List<java.util.Map.Entry>", new SignatureType.Typed(
                new ValueTypes.Parameterized(List.class, List.of(Map.Entry.class)), "null").comparedName());
        assertEquals("java.util.List<java.lang.Integer>", new SignatureType.Typed(
                new ValueTypes.Parameterized(List.class, List.of(int.class)), "null").comparedName());
    }

    @Test
    void aStringArrayIsOneHoweverItIsWritten() {
        assertTrue(TypeNodes.isStringArray(parameter("", "String[] args")));
        assertTrue(TypeNodes.isStringArray(parameter("", "String args[]")));
        assertTrue(TypeNodes.isStringArray(parameter("", "String... args")));
        assertTrue(TypeNodes.isStringArray(parameter("", "java.lang.String[] args")));
        assertFalse(TypeNodes.isStringArray(parameter("", "String args")));
        assertFalse(TypeNodes.isStringArray(parameter("", "String[][] args")));
        assertFalse(TypeNodes.isStringArray(parameter("", "int[] args")));
        assertFalse(TypeNodes.isStringArray(parameter("import a.String;", "String[] args")));
    }

    @Test
    void writtenReadsAsAPersonWouldAndHoldsTheArrayPartsOutsideTheNode() {
        assertEquals("Map<String, List<? extends Point>>", TypeNodes.written(returned("", "Map<String,List<? extends Point>>")));
        assertEquals("int[]", TypeNodes.written(parameter("", "int x[]")));
        assertEquals("int[]", TypeNodes.written(parameter("", "int... x")));
    }

    @Test
    void aNameIsResolvedAsFarAsTheFileSays() {
        assertEquals("a.Point", ofWritten("import a.Point;", "Point"));
        assertEquals("java.lang.Integer", ofWritten("", "Integer"));
        assertEquals("Mine", ofWritten("", "Mine"));
        assertEquals("java.util.List<String>", ofWritten("import java.util.List;", "List<String>"));
        assertEquals("a.Point[]", ofWritten("import a.Point;", "Point[]"));
        assertEquals("int", ofWritten("", "int"));
    }

    private static String ofWritten(String imports, String type) {
        return ResolvedType.ofWritten(returned(imports, type)).qualifiedName();
    }

    @Test
    void aMultiCatchReadsWithItsBars() {
        CompilationUnit unit = SourceParser.parse(JavaLevel.LATEST, """
                class Subject {
                    void f() {
                        try { } catch (IllegalStateException|IllegalArgumentException e) { }
                    }
                }
                """);
        List<String> caught = new ArrayList<>();
        unit.accept(new ASTVisitor() {
            @Override
            public boolean visit(CatchClause clause) {
                caught.add(TryHandler.catchTypeText(clause));
                return true;
            }
        });
        assertEquals(List.of("IllegalStateException | IllegalArgumentException"), caught);
    }

    @Test
    void onlyAnArrayListIsTakenApartForItsValues() {
        assertEquals(List.of("1"), leaves("new java.util.ArrayList<>(1)"));
        assertEquals(List.of("1"), leaves("new ArrayList<>(1)"));
        assertEquals(List.of(), leaves("new ArrayListOfMine(1)"));
    }

    private static List<String> leaves(String source) {
        List<Expression> found = new ArrayList<>();
        ProjectAnalyzer.collectLeafValues(SourceParser.expression(JavaLevel.LATEST, source), found);
        return found.stream().filter(each -> each.toString().equals("1")).map(Expression::toString).toList();
    }

    /** {@code Integer} is a number: a returned {@code (Integer) x} still fits a function that now gives an {@code int}. */
    @Test
    void aBoxWrittenBareIsAboxOfJavaLang() {
        MethodDeclaration method = method("", "Object f(Object x) { return (Integer) x; }");
        assertEquals(SignatureMigration.ReturnFate.UNCHANGED,
                SignatureMigration.returnFate(method, SignatureType.kept("Object"), SignatureType.kept("int")));
    }
}
