// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.java.index;

import com.botmaker.java.grammar.BotAnnotation;
import com.botmaker.java.grammar.BotNames;
import com.botmaker.java.grammar.JavaValue;
import com.botmaker.java.grammar.ParamsFitter;
import com.botmaker.java.grammar.SourceNode;
import com.botmaker.java.grammar.ValueGrammar;
import com.botmaker.java.grammar.ValueTypes;
import com.botmaker.java.parse.JavaLevel;
import com.botmaker.plugin.api.params.Param;
import com.botmaker.plugin.api.params.Pool;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.MethodReference;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.TextBlock;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

import java.lang.invoke.MethodType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The pools of a bot's {@code @Pools} groups, read off their bound trees without running them (2026-11-15, spec
 * {@code 2026-11-15-run-claims-design.md} §1): what the host deals several runs from.
 *
 * <pre>{@code
 * @Pools
 * public final class FarmPools {
 *     public Pool<GameAccount> accounts() {
 *         return Pool.of(GameAccount.MAIN, GameAccount.ALT_1).fills(Farm::account).rotate();
 *     }
 * }
 * }</pre>
 *
 * <p>Each pool is one call chain the contract's {@link Pool} would build, checked by the same rules: an element is a
 * literal, a constant or a value the grammar reads (a record of those); {@code fills} names a parameter of a
 * {@code @Param} group whose type holds the elements, or a list of them after {@code take}. A pool that does not read
 * says why and has no elements. Bindings decide which class each name is, so the unit must be parsed against the
 * project's classpath ({@link BotIndex}).
 */
public final class PoolReader {

    private static final String POOL = Pool.class.getCanonicalName();
    private static final String PARAM = Param.class.getCanonicalName();
    private static final String LIST = List.class.getCanonicalName();

    private PoolReader() {}

    /**
     * The parameter a pool fills, as the run's agent finds it in the bot's classes.
     *
     * @param owner      the group's binary name, {@code com.bot.params.bot.Farm}
     * @param method     the parameter's method, {@code account}
     * @param descriptor the method's JVM descriptor, {@code ()Lcom/bot/GameAccount;}
     * @param type       the parameter's type, as a value's type
     * @param element    what each element is read as: the type, or after {@code take} its list's element
     */
    public record Filled(String owner, String method, String descriptor, Type type, Type element) {}

    /**
     * One pool, read.
     *
     * @param group    the {@code @Pools} class's binary name
     * @param method   the pool's method in it
     * @param fills    the parameter it fills, once it reads
     * @param allOf    the canonical name of {@code all}'s type, when the pool takes every constant of one
     * @param elements what runs are dealt from, as the grammar writes each, with the imports it names; none when the
     *                 pool does not read
     * @param take     how many elements a run takes; {@code 0} for one, the parameter an element and not a list
     * @param each     how many live runs may hold one element at once
     * @param rotates  whether the element used least recently is dealt first
     * @param problem  why the pool does not read; empty when it does
     */
    public record Reading(String group, String method, Optional<Filled> fills, Optional<String> allOf,
                          List<JavaValue> elements, int take, int each, boolean rotates, String problem) {

        public Reading {
            elements = List.copyOf(elements);
            problem = problem == null ? "" : problem;
        }

        /** Whether the pool reads: it fills a parameter and has its elements. */
        public boolean reads() {
            return problem.isEmpty();
        }

        private static Reading refused(String group, String method, String why) {
            return new Reading(group, method, Optional.empty(), Optional.empty(), List.of(), 0, 1, false, why);
        }
    }

    /**
     * What else reads as an element, beside a literal, a constant and what the grammar reads: the host's view of the
     * bot's own records, which the grammar declines by design.
     */
    @FunctionalInterface
    public interface Elements {

        /** Nothing more. */
        Elements NONE = (type, element) -> false;

        /** Whether {@code element} is a value of {@code type}. */
        boolean reads(Type type, SourceNode element);
    }

    /**
     * Every pool of {@code unit}'s {@code @Pools} classes, in file order.
     *
     * @param units    every unit of the bot, parsed together with {@code unit}: where an open set's constants are
     *                 declared, so one that names another is the same element
     * @param source   {@code unit}'s text
     * @param names    the bot's own constants the grammar may meet in an element
     */
    public static List<Reading> read(CompilationUnit unit, String source, Collection<CompilationUnit> units,
                                     ValueGrammar grammar, BotNames names, Elements elements) {
        List<Reading> out = new ArrayList<>();
        for (Object each : unit.types()) {
            if (!(each instanceof TypeDeclaration type) || type.isInterface() || BotAnnotation.POOLS.on(type) == null) {
                continue;
            }
            String group = binaryName(type);
            for (MethodDeclaration method : type.getMethods()) {
                if (!ParamsFitter.isParameter(method)) continue;
                Chain chain = new Chain(group, method.getName().getIdentifier(), source, units, grammar, names, elements);
                out.add(chain.read(method));
            }
        }
        return List.copyOf(out);
    }

    /** {@code type}'s binary name, from its binding or else its package and name. */
    private static String binaryName(TypeDeclaration type) {
        ITypeBinding binding = type.resolveBinding();
        if (binding != null && binding.getBinaryName() != null) return binding.getBinaryName();
        CompilationUnit unit = (CompilationUnit) type.getRoot();
        String pack = unit.getPackage() == null ? "" : unit.getPackage().getName().getFullyQualifiedName() + ".";
        return pack + type.getName().getIdentifier();
    }

    /** One pool's call chain, read step by step as {@link Pool} would build it. */
    private static final class Chain {

        private final String group;
        private final String method;
        private final String source;
        private final Collection<CompilationUnit> units;
        private final ValueGrammar grammar;
        private final BotNames names;
        private final Elements others;

        private final List<Expression> listed = new ArrayList<>();
        private ITypeBinding all;
        private IMethodBinding fills;
        private int take;
        private int each = 1;
        private boolean rotates;
        private AST ast;

        Chain(String group, String method, String source, Collection<CompilationUnit> units, ValueGrammar grammar,
              BotNames names, Elements others) {
            this.group = group;
            this.method = method;
            this.source = source;
            this.units = units;
            this.grammar = grammar;
            this.names = names;
            this.others = others;
        }

        Reading read(MethodDeclaration declared) {
            try {
                return reading(declared);
            } catch (Unread unread) {
                return Reading.refused(group, method, unread.getMessage());
            }
        }

        private Reading reading(MethodDeclaration declared) {
            ast = declared.getAST();
            IMethodBinding pool = declared.resolveBinding();
            ITypeBinding returns = pool == null ? null : pool.getReturnType();
            if (returns == null || returns.isRecovered()) {
                throw new Unread("does not resolve: a pool reads against the project's classpath, which holds the"
                        + " contract's Pool");
            }
            if (!returns.getErasure().getQualifiedName().equals(POOL)) throw new Unread("returns no Pool");
            Expression returned = returned(declared);
            List<MethodInvocation> steps = new ArrayList<>();
            Expression at = returned;
            while (at instanceof MethodInvocation call && !isPoolFactory(call)) {
                steps.addFirst(call);
                at = call.getExpression();
            }
            if (!(at instanceof MethodInvocation root)) {
                throw new Unread("returns no pool: it starts at Pool.of(…) or Pool.all(…)");
            }
            start(root);
            for (MethodInvocation step : steps) step(step);
            if (fills == null) throw new Unread("fills no parameter: end it with .fills(Group::parameter)");
            ITypeBinding parameter = fills.getReturnType();
            ITypeBinding element = elementOf(parameter);
            List<JavaValue> values = all != null ? constants(element) : listedValues(element);
            ITypeBinding owner = fills.getDeclaringClass().getErasure();
            Filled filled = new Filled(owner.getBinaryName(), fills.getName(), "()" + descriptor(parameter),
                    form(parameter), form(element));
            return new Reading(group, method, Optional.of(filled),
                    Optional.ofNullable(all).map(type -> type.getErasure().getQualifiedName()), values, take, each,
                    rotates, "");
        }

        /** {@code Pool.of(…)} or {@code Pool.all(…)}: the contract's, by binding. */
        private static boolean isPoolFactory(MethodInvocation call) {
            String name = call.getName().getIdentifier();
            return (name.equals("of") || name.equals("all")) && isPools(call);
        }

        private static boolean isPools(MethodInvocation call) {
            IMethodBinding binding = call.resolveMethodBinding();
            return binding != null && binding.getDeclaringClass().getErasure().getQualifiedName().equals(POOL);
        }

        private void start(MethodInvocation root) {
            if (root.getName().getIdentifier().equals("all")) {
                if (root.arguments().size() != 1 || !(root.arguments().getFirst() instanceof TypeLiteral literal)) {
                    throw new Unread("Pool.all takes one class literal, as Pool.all(Hero.class)");
                }
                all = literal.getType().resolveBinding();
                if (all == null || all.isRecovered()) throw new Unread(said(literal) + " is no class of this bot's classpath");
                return;
            }
            // Pool.<Hero>of() lists nothing yet: a starting copy's pool, which deals nothing until this PC lists some.
            IMethodBinding of = root.resolveMethodBinding();
            if (root.arguments().size() == 1 && of != null && of.isVarargs()
                    && ((Expression) root.arguments().getFirst()).resolveTypeBinding() instanceof ITypeBinding array
                    && array.isArray()) {
                throw new Unread("lists its elements in an array: list them one by one, as Pool.of(a, b)");
            }
            for (Object argument : root.arguments()) listed.add((Expression) argument);
        }

        private void step(MethodInvocation step) {
            if (!isPools(step)) throw new Unread(step.getName() + "(…) is not one of Pool's steps");
            switch (step.getName().getIdentifier()) {
                case "fills" -> {
                    if (fills != null) throw new Unread("fills two parameters: a pool fills one");
                    fills = parameter((Expression) step.arguments().getFirst());
                }
                case "take" -> {
                    if (take > 0) throw new Unread("takes twice: a pool takes once");
                    if (fills != null) throw new Unread("takes after fills: take(k) comes before fills");
                    take = count(step, "take");
                    if (all == null && !listed.isEmpty() && take > listed.size()) {
                        throw new Unread("takes " + take + " of " + listed.size() + " elements");
                    }
                }
                case "each" -> each = count(step, "each");
                case "rotate" -> rotates = true;
                default -> throw new Unread(step.getName() + "(…) is not one of Pool's steps");
            }
        }

        /** {@code take(k)} or {@code each(n)}'s count: a constant of at least one. */
        private int count(MethodInvocation step, String name) {
            Object constant = ((Expression) step.arguments().getFirst()).resolveConstantExpressionValue();
            if (!(constant instanceof Integer n)) throw new Unread(name + "(…) takes a number written out, as " + name + "(2)");
            if (n < 1) throw new Unread(name + "(" + n + ") is less than one");
            return n;
        }

        /** The parameter {@code reference} names: a no-argument instance method of a {@code @Param} group. */
        private IMethodBinding parameter(Expression reference) {
            if (!(reference instanceof MethodReference named)) {
                throw new Unread("fills takes a method reference, as Farm::account, not " + said(reference));
            }
            IMethodBinding binding = named.resolveMethodBinding();
            if (binding == null || binding.isRecovered()) {
                throw new Unread(said(reference) + " names no method of this bot");
            }
            if (Modifier.isStatic(binding.getModifiers()) || binding.getParameterTypes().length != 0
                    || binding.isConstructor() || !isGroup(binding.getDeclaringClass())) {
                throw new Unread(said(reference) + " is not a parameter: a method of a @Param group with no parameter");
            }
            return binding;
        }

        private static boolean isGroup(ITypeBinding type) {
            for (IAnnotationBinding annotation : type.getAnnotations()) {
                if (annotation.getAnnotationType().getQualifiedName().equals(PARAM)) return true;
            }
            return false;
        }

        /** What each element is: the parameter's type, or after {@code take} its list's element. */
        private ITypeBinding elementOf(ITypeBinding parameter) {
            String named = named();
            if (take == 0) return parameter;
            if (!parameter.getErasure().getQualifiedName().equals(LIST) || parameter.getTypeArguments().length != 1) {
                throw new Unread(named + " is a " + parameter.getName() + ": take(" + take + ") fills a List");
            }
            return parameter.getTypeArguments()[0];
        }

        private String named() {
            return fills.getDeclaringClass().getName() + "::" + fills.getName();
        }

        /** The elements {@code of} listed, each checked to be an {@code element} the grammar writes, once. */
        private List<JavaValue> listedValues(ITypeBinding element) {
            List<JavaValue> out = new ArrayList<>();
            Set<String> identities = new LinkedHashSet<>();
            for (Expression listed : this.listed) {
                ITypeBinding type = listed.resolveTypeBinding();
                // As Pool.fills checks it: the boxed element an instance of the boxed parameter, so an int never
                // widens into a long.
                if (type == null || !boxed(type).isSubTypeCompatible(boxed(element))) {
                    throw new Unread(named() + " is a " + element.getName() + ", which does not hold " + said(listed));
                }
                IVariableBinding constant = constantOf(listed);
                SourceNode node = new SourceNode(listed, source);
                Type form = form(element);
                boolean reads = constant != null || grammar.read(form, node, names).isPresent()
                        || plain(listed) && others.reads(form, node);
                if (!reads) {
                    throw new Unread(said(listed) + " is not a value the host writes: a literal, a constant, or a"
                            + " record of those");
                }
                JavaValue value = constant != null ? constant(aliasOf(constant))
                        : new JavaValue(JavaValue.detached(node).node(), importsOf(listed), node.source());
                String identity = identity(listed);
                boolean again = identity != null ? !identities.add(identity) : out.stream().anyMatch(value::sameJava);
                if (again) throw new Unread("lists " + said(listed) + " twice");
                out.add(value);
            }
            return List.copyOf(out);
        }

        /**
         * What makes two listed elements one, as {@link Pool#of} compares them by equality: a compile-time value
         * ({@code "main"} and a constant holding it), or the constant another names ({@code ALT = MAIN}); null for
         * anything else, compared as written.
         */
        private String identity(Expression listed) {
            Object value = listed.resolveConstantExpressionValue();
            if (value != null) return "=" + value.getClass().getName() + ":" + value;
            IVariableBinding constant = constantOf(listed);
            return constant == null ? null : aliasOf(constant).getKey();
        }

        /** {@code type}'s erasure, a primitive's wrapper, as {@link MethodType#wrap} gives the contract's. */
        private ITypeBinding boxed(ITypeBinding type) {
            if (!type.isPrimitive()) return type.getErasure();
            String wrapper = switch (type.getName()) {
                case "int" -> "Integer";
                case "char" -> "Character";
                default -> Character.toUpperCase(type.getName().charAt(0)) + type.getName().substring(1);
            };
            ITypeBinding found = ast.resolveWellKnownType("java.lang." + wrapper);
            return found == null ? type : found;
        }

        /**
         * Every constant of {@code all}'s type, each as {@code Type.NAME}: an enum's, or the {@code public static final}
         * fields of the class that hold one. A field whose value is another of them ({@code ALT = MAIN}) is the same
         * element, as {@link Pool#all} finds it by equality.
         */
        private List<JavaValue> constants(ITypeBinding element) {
            if (!all.isAssignmentCompatible(element)) {
                throw new Unread(named() + " is a " + element.getName() + ", which does not hold a " + all.getName());
            }
            List<IVariableBinding> fields = new ArrayList<>(List.of(all.getDeclaredFields()));
            fields.sort(Comparator.comparingInt(IVariableBinding::getVariableId));
            List<JavaValue> out = new ArrayList<>();
            Set<Object> values = new LinkedHashSet<>();
            for (IVariableBinding field : fields) {
                int modifiers = field.getModifiers();
                boolean member = all.isEnum() ? field.isEnumConstant()
                        : Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers)
                          && field.getType().getErasure().isSubTypeCompatible(all.getErasure());
                if (!member || isAlias(field)) continue;
                if (field.getConstantValue() != null && !values.add(field.getConstantValue())) continue;
                out.add(constant(field));
            }
            if (out.isEmpty()) throw new Unread(all.getName() + " has no constant to deal");
            if (take > out.size()) throw new Unread("takes " + take + " of " + out.size() + " elements");
            return List.copyOf(out);
        }

        /** Whether {@code field} names another constant of {@code all}'s, in the bot's source. */
        private boolean isAlias(IVariableBinding field) {
            IVariableBinding named = aliasOf(field);
            return !named.isEqualTo(field) && named.getDeclaringClass().getErasure().isEqualTo(all.getErasure());
        }

        /**
         * The constant {@code field} holds when its declaration in the bot's source is another constant
         * ({@code ALT = MAIN}), followed to the last; {@code field} itself otherwise. A constant of a library's class
         * has no source here, so two of its fields holding one value read as two.
         */
        private IVariableBinding aliasOf(IVariableBinding field) {
            IVariableBinding at = field;
            for (int hops = 0; hops < 16; hops++) {
                IVariableBinding next = null;
                for (CompilationUnit unit : units) {
                    if (unit.findDeclaringNode(at.getKey()) instanceof VariableDeclarationFragment declared
                            && declared.getInitializer() != null) {
                        next = constantOf(declared.getInitializer());
                        break;
                    }
                }
                if (next == null || next.isEqualTo(at)) return at;
                at = next;
            }
            return at;
        }

        /**
         * Whether {@code element} is made of literals, constants and records of those only: what the host's own reading
         * of a bot's record is trusted with, since it counts a record's parts and reads none.
         */
        private static boolean plain(Expression element) {
            return switch (element) {
                case StringLiteral ignored -> true;
                case NumberLiteral ignored -> true;
                case BooleanLiteral ignored -> true;
                case CharacterLiteral ignored -> true;
                case TextBlock ignored -> true;
                case PrefixExpression negative when negative.getOperator() == PrefixExpression.Operator.MINUS ->
                        negative.getOperand() instanceof NumberLiteral;
                case ClassInstanceCreation record when record.getAnonymousClassDeclaration() == null ->
                        record.arguments().stream().allMatch(part -> plain((Expression) part));
                default -> constantOf(element) != null;
            };
        }

        /** {@code node} as its author wrote it. */
        private String said(Expression node) {
            return new SourceNode(node, source).source();
        }

        /** {@code field} written {@code Type.NAME} from its outermost class, which it imports. */
        private static JavaValue constant(IVariableBinding field) {
            List<String> path = new ArrayList<>();
            ITypeBinding top = field.getDeclaringClass().getErasure();
            for (; top.getDeclaringClass() != null; top = top.getDeclaringClass()) path.addFirst(top.getName());
            path.addFirst(top.getName());
            AST ast = JavaLevel.LATEST.newAst();
            QualifiedName written = ast.newQualifiedName(ast.newName(path.toArray(String[]::new)),
                    ast.newSimpleName(field.getName()));
            return JavaValue.built(written, List.of(top.getQualifiedName()));
        }

        /** The {@code static final} field {@code expression} names, or null. */
        private static IVariableBinding constantOf(Expression expression) {
            IBinding binding = switch (expression) {
                case Name name -> name.resolveBinding();
                case FieldAccess access -> access.resolveFieldBinding();
                default -> null;
            };
            return binding instanceof IVariableBinding field && field.isField()
                    && Modifier.isStatic(field.getModifiers()) && Modifier.isFinal(field.getModifiers()) ? field : null;
        }

        /** The value's type for {@code type}: a container over its arguments, or a class the bot declares. */
        private Type form(ITypeBinding type) {
            if (type.isParameterizedType() && type.getErasure().getQualifiedName().equals(LIST)
                    && type.getTypeArguments().length == 1) {
                return ValueTypes.listOf(form(type.getTypeArguments()[0]));
            }
            Type read = ValueTypeResolver.ofBinding(grammar, type.isPrimitive() ? type : type.getErasure());
            return read instanceof ValueTypes.Unknown && type.isFromSource()
                    ? new ValueTypes.BotClass(type.getErasure().getQualifiedName(), List.of()) : read;
        }

        private static Expression returned(MethodDeclaration method) {
            Block body = method.getBody();
            if (body.statements().size() == 1 && body.statements().getFirst() instanceof ReturnStatement ret
                    && ret.getExpression() != null) {
                return ret.getExpression();
            }
            throw new Unread("is not one return of a pool");
        }
    }

    /**
     * The classes {@code expression} writes by simple name, by their outermost class: what an element needs imported
     * wherever it is written.
     */
    private static List<String> importsOf(Expression expression) {
        Set<String> out = new LinkedHashSet<>();
        expression.accept(new ASTVisitor() {
            @Override
            public boolean visit(SimpleName name) {
                ASTNode parent = name.getParent();
                if (parent instanceof QualifiedName qualified && qualified.getName() == name) return false;
                if (name.resolveBinding() instanceof IVariableBinding field && field.isField()
                        && !(parent instanceof FieldAccess access && access.getName() == name)) {
                    // MAIN by a static import: the file the value is written into has no such import.
                    throw new Unread("writes " + name.getIdentifier() + " by a static import: write it with its class,"
                            + " as Owner." + name.getIdentifier());
                }
                if (name.resolveBinding() instanceof ITypeBinding type && !type.isPrimitive()) {
                    ITypeBinding top = type.getErasure();
                    while (top.getDeclaringClass() != null) top = top.getDeclaringClass();
                    out.add(top.getQualifiedName());
                }
                return false;
            }
        });
        return List.copyOf(out);
    }

    /** The JVM descriptor of {@code type}'s erasure. */
    private static String descriptor(ITypeBinding type) {
        if (type.isPrimitive()) return type.getBinaryName();
        if (type.isArray()) return "[" + descriptor(type.getComponentType());
        return "L" + type.getErasure().getBinaryName().replace('.', '/') + ";";
    }

    /** Why a pool does not read, said where it was found. */
    private static final class Unread extends RuntimeException {
        Unread(String why) {
            super(why, null, false, false);
        }
    }
}
