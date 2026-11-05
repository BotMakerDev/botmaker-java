package com.botmaker.java.edit;

import com.botmaker.java.edit.helpers.AstRewriteHelper;
import com.botmaker.java.parse.BotParser;
import com.botmaker.java.parse.JavaLevel;
import com.botmaker.java.parse.SourceSet;
import com.botmaker.java.types.ResolvedType;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A project file never drawn has no tree yet: where it lives is read off a parse of its text. */
class ImportManagerSourcesTest {

    private static String importFor(String helperSource) {
        Path helper = Path.of("/bot/Helper.java");
        SourceSet sources = new SourceSet() {
            @Override public Stream<Path> files() { return Stream.of(helper); }
            @Override public Optional<CompilationUnit> unit(Path file) { return Optional.empty(); }
            @Override public Optional<String> text(Path file) { return Optional.of(helperSource); }
            @Override public List<String> classpath() { return List.of(); }
            @Override public Path sourceRoot() { return null; }
            @Override public JavaLevel level() { return JavaLevel.LATEST; }
        };
        String source = "package com.bot;\nclass Here {}\n";
        CompilationUnit here = BotParser.syntax(source);
        ASTRewrite rewriter = ASTRewrite.create(here.getAST());
        ImportManager.addImport(here, rewriter, ResolvedType.named("Helper"), sources);
        CompilationUnit written = BotParser.syntax(AstRewriteHelper.applyRewrite(rewriter, source));
        return written.imports().isEmpty() ? null : written.imports().getFirst().toString().strip();
    }

    @Test
    void aFileWithNoTreeIsImportedFromItsPackage() {
        assertEquals("import com.bot.tools.Helper;",
                importFor("/* package com.old; */\npackage com.bot.tools;\nclass Helper {}\n"));
    }

    @Test
    void aLineInsideATextBlockIsNoPackage() {
        assertEquals(null, importFor("""
                class Helper {
                    String how = \"""
                            package com.old;
                            \""";
                }
                """));
    }
}
