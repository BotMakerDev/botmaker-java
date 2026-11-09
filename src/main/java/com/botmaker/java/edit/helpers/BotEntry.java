package com.botmaker.java.edit.helpers;

import com.botmaker.java.parse.BotParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.PackageDeclaration;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The class a bot starts from, read from one source file's tree: the top-level type that declares
 * {@code public static void main(String[])} (or the {@code main()} Java 25 starts), itself or in a member class, and
 * the file's package. One reader for every host that asks which class is the bot's entry: Studio's project, the
 * cli's {@code .bmk}, so the author's machine and the catalog's rebuild answer alike.
 *
 * @param packageName the file's package, empty in the default package
 * @param className   the simple name of the top-level type holding {@code main}, empty when a file too broken
 *                    to parse kept no type
 */
public record BotEntry(String packageName, String className) {

    /** What {@code main} looks like in text: only ever a reason not to parse a file, never a reason to believe it. */
    private static final Pattern MAYBE_MAIN = Pattern.compile("\\bmain\\s*\\(");

    /**
     * The entry {@code source} declares, else empty. A file that never writes {@code main(} is not parsed. A file
     * that does not parse may have lost the method from its tree; when its tokens still read
     * {@code public static void main(}, its first top-level type is the answer, and when the tree kept no type, the
     * entry has its package and an empty {@link #className} (see {@link #named}).
     */
    public static Optional<BotEntry> of(String source) {
        if (!MAYBE_MAIN.matcher(source).find()) return Optional.empty();
        CompilationUnit unit = BotParser.syntax(source);
        List<?> types = unit.types();
        Optional<AbstractTypeDeclaration> holder = types.stream()
                .map(AbstractTypeDeclaration.class::cast)
                .filter(BotEntry::holdsMain)
                .findFirst();
        PackageDeclaration declared = unit.getPackage();
        String packageName = declared == null ? "" : declared.getName().getFullyQualifiedName();
        if (holder.isPresent()) return Optional.of(new BotEntry(packageName, holder.get().getName().getIdentifier()));
        if (!FileTypeDetector.holdsJvmMain(unit, source)) return Optional.empty();
        String first = types.isEmpty() ? "" : ((AbstractTypeDeclaration) types.getFirst()).getName().getIdentifier();
        return Optional.of(new BotEntry(packageName, first));
    }

    /** Whether the tree named the type: a file too broken for that has an entry with no {@link #className}. */
    public boolean named() {
        return !className.isEmpty();
    }

    /** {@code com.acme.bot.Bot}, or the simple name in the default package: the name {@code java -cp …} is given. */
    public String qualifiedName() {
        return packageName.isEmpty() ? className : packageName + "." + className;
    }

    /** Whether {@code type} or a class inside it declares {@link FileTypeDetector#isJvmMain}. */
    private static boolean holdsMain(AbstractTypeDeclaration type) {
        boolean[] found = {false};
        type.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodDeclaration method) {
                found[0] |= FileTypeDetector.isJvmMain(method);
                return false;
            }
        });
        return found[0];
    }
}
