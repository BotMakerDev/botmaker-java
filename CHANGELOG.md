# Changelog

All notable changes to `botmaker-java`.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this module uses
[semantic versioning](https://semver.org/). `release.sh` refuses to cut a version with no section here.

## [Unreleased]

### Added

- `edit.helpers.BotEntry.of(source)` reads, from a parse, the package and the top-level class that holds the bot's
  `main` (2026-11-09). A comment or a string naming `main` is no entry. When a file does not parse, its tokens decide,
  and a file too broken to keep its type has an entry with no class name (`named()` is false). The core's
  `ProjectConfig` and the cli's `.bmk` reader both use it.
- `edit.BodyInserts.insert` puts a statement on a body's line after a comment through the rewrite's own list
  (2026-11-09). A `TargetSourceRangeComputer` stretches the statement above to the comment's end and starts the one
  below at its own first character. A body of only comments is replaced by those comments and the statement, and a
  comment whose statement above moves past it is written back before the statement, each comment as its own source.

### Removed

- `AstRewriteHelper.applyRewriteAndInsertAt` (2026-11-09): nothing inserts Java as text at an offset any more.

- The grammar reads and writes what a plan is made of (2026-11-06). A varargs link reads its arguments into one list
  part and writes each back. A `Supplier` part reads `() -> expr` as the value it gives, read when asked, and writes
  back as the same lambda. An interface or abstract class with no canonical shape, such as `Goal` or `Retry`, reads by
  the calls that build its kinds. When several links share a name, a part kept as source goes to the first wither that
  takes it, so `.needs(Firestone::eventOn)` is a condition, not a goal.

### Changed

- This pom is the only one that names JDT's version (2026-11-05): the cli and Studio dropped their own declarations.
- Types compared as types (2026-11-05, clean-java phase 10): `grammar.TypeNodes` answers what a printed node used to.
  `same` is `ParamsFitter`'s rule, moved; `writes` compares a node to a spelled type through its file's imports, type
  arguments included; `names` asks for a class whatever its arguments; `isStringArray` takes every spelling of `main`'s
  parameter. So `int x[]`, `int... x` and `int[] x` are one signature key, a bot's own `Duration` is carried rather than
  read as `java.time.Duration`, `new ArrayList<String>()` keeps its arguments, and only `java.util.ArrayList` counts as
  a list. `ResolvedType.ofWritten` names a type with no binding, so a bare `Integer` is a number. `SignatureType` has
  `comparedName` (whole, from `ValueTypes.wholeName`), `Kept` gains it, and `MethodSignatures.signatureTypeOf` reads a
  node or the chooser's `ResolvedType`.
- Reading through the tree (2026-11-05, clean-java phase 9): `NumberLiterals.typeName` is the one answer to a number
  literal's type, so `0xE` and `0xFF` are `int`, where `ProjectAnalyzer` and `SignatureMigration` each called them
  `double` or `float`. `ImportManager` reads a file with no tree yet from a parse of its text, so a line inside a text
  block is no `package`. `FileTypeDetector.isJvmMain` (from `StatementPlacement`) and `holdsJvmMain` say where a bot
  starts; `parse.SourceTokens` reads a file that does not parse, whose tree may have lost code.
- `Precedence` also parenthesises a non-primary at a qualified name's qualifier, a method reference's receiver and an
  `outer.new` (2026-11-05): the `@Migrate` engine fills a rule's body through it.
- `AstRewriteHelper.applyRewrite` and `applyRewriteAndInsertAt` throw `RewriteFailed`, with a sentence and the cause,
  when a rewrite cannot be applied to the text handed in (2026-11-05). They printed the stack trace and returned that
  text, which every caller took for an edit that changed nothing.

### Added

- `edit.helpers.CommentText` (2026-11-05): text written so it stays a comment. `written` gives a line or block comment;
  `inside` makes text safe in a block or doc comment. `*/` becomes `* /` and `\u` becomes `\ u`, the only changes.
- `JavaValue.detached`: an expression read out of a file, copied into a tree of its own with its offsets moved onto its
  own text, so a value kept past the file's parse pins neither the file's tree nor its text, and still shows each part
  as written. `JavaValue.written()` hands that text on whenever the node's offsets index it (2026-11-05).

- The module (2026-11-04): the value grammar and `SourceParser` moved from `botmaker-plugin-host`
  (`com.botmaker.plugin.host.grammar` is now `com.botmaker.java.grammar`), with their history at
  `botmaker-plugin-host@df71893`. `JavaLayersTest` keeps it free of UI and hosts, with one parser.
- `com.botmaker.java.parse.SourceSet` (2026-11-04): the read-only view of a project's sources, its files, their text
  and trees, its classpath and source root. `parsedUnit` parses and keeps a file's tree, `ownerOf` and `freshValue` give
  what the bound libraries claim of a name and seed for a type.
- `com.botmaker.java.types`, `index` and `edit` (2026-11-04): Studio's resolved types, `ProjectAnalyzer` over a
  `SourceSet` and a `LibraryIndex`, `ImportManager`, `EditContext`, `BlockId`, `InitializerFactory` and the handlers
  and helpers that need nothing of Studio, moved from `botmaker-studio@cea8a957`. `EnumManipulationHandler.addEnumToClass`
  takes the name and the constants rather than Studio's `EnumDraft`.
- The bot's index and the edits over it (2026-11-04), moved from `botmaker-studio@e0081238`: `parse.BotParser`;
  `index.BotIndex`, `ValueTypeResolver` and `ApiDocsParser`; `index.nav` (`Usages`, `Refactor`, `TypeRename`,
  `TypeDelete`, `TextSearch`); `edit.guard` (`CompileGuard`, `UnusedImports`); `edit.refactor` (`MethodReferences`,
  `ReviewMarks`); `edit.palette`, the palette's block and expression kinds, `ApiDocs` and `TypeNames`; and
  `edit.factories.ExpressionFactory`. `BotIndex` is built over a `SourceSet` or a map of sources; the open project's
  index is its holder's to build.
- `parse.JavaLevel` (2026-11-05): the bot's Java version, read from its pom's `maven.compiler.release` and clamped to
  what JDT supports. `SourceParser`, `SourceFormatter`, `ParamsFitter` and `ParamValues` take it on every entry,
  `SourceSet.level()` carries it, and `BotIndex` and `BotParser` parse at it. Code the level cannot hold (a switch
  expression in a Java 11 bot) is refused with `JavaLevel.TooOld`, whose message names the version; a String
  literal is written as a text block only from Java 15. `JavaLevel.copy` copies a node into a tree of another level,
  which JDT's `ASTNode.copySubtree` refuses: a value built at the newest level lands in a bot on Java 25, and a text
  block in a bot before Java 15 is a plain literal. `JavaLayersTest`: only `JavaLevel` asks JDT for its newest Java
  or calls `copySubtree`.
- The palette model (2026-11-05), moved from `botmaker-studio@b3a90cbe`: `edit.palette`'s `BotType`, `SignatureType`,
  `SignatureTypes`, `FunctionDraft`, `EnumDraft`, `BlockCatalog` and `ExpressionCatalog`; `edit.StatementPlacement`,
  `edit.helpers.MethodSignatures` and `edit.refactor.SignatureMigration`. What reads the bound plugins' types takes a
  `ValueGrammar` (`BotType.values`, `SignatureTypes.of`, `typeOf`, `freshValue`, `MethodSignatures.draftOf`,
  `signatureTypeOf`); a `SignatureType.Typed` carries the default text the grammar gave it when it was picked.

### Fixed

- A default value for a type no project source declares keeps its package and generics (2026-11-04): a
  `Consumer<MatchResult>` slot was enriched to the name `MatchResult>` and its seed threw.
