# CLAUDE.md

Guidance for working in **botmaker-java**, the library that reads and writes a bot's Java.

Read the umbrella `../CLAUDE.md` first; `../docs/refactor/35-typed-value-reader.md` and `36-bound-values.md` for the
value grammar; `../docs/superpowers/specs/2026-11-04-java-and-core-design.md` for why this module exists.

## What this module is

One library for every module that touches a bot's source, before a compile (Studio, cli) or during a run (SDK). It has
no state, no files and no UI: a caller passes text or trees and gets text or trees back.

- `grammar`: `SourceParser`, the one entry from text to a tree and the only main-code `ASTParser.newParser`
  (`JavaLayersTest`); `SourceFormatter`; `ValueGrammar`, `ExpressionReader`, `ValueWriter` and `Factory`, which read a
  plugin's values off JDT and write JDT trees; `ParamValues` and `ParamsFitter`, a parameter's method and the fit of
  `params/` to `starting-params/`; `BotAnnotation`, a BotMaker annotation known by what its imports resolve to;
  `TypeNodes`, every question about a written type, so nothing compares what a node prints. `grammar` imports no other
  package of this module but `parse`.
- `parse`: `SourceSet`, the read-only view of a project's sources that a caller hands in: its files, their text and
  trees, its classpath and source root. This module holds no project; it reads one through this. What a bound
  library claims (`ownerOf`, `freshValue`) comes through it too, never from a plugin registry. `BotParser` parses one
  more source the way the bot's are. `JavaLevel` is the bot's Java version (`SourceSet.level()`); every parse, format
  and `AST` takes one, and only `JavaLevel` names JDT's newest level (`JavaLayersTest`). A fragment copied into a
  bot's tree is built at `LATEST` and copied by `JavaLevel.copy`, never `ASTNode.copySubtree`, which throws across levels; code the bot's level cannot hold throws `JavaLevel.TooOld`.
- `types`: `ResolvedType` and its kin, `ProjectAnalyzer` (a project's and its libraries' types, over a `SourceSet` and
  an `index.LibraryIndex`), `MethodSignature`.
- `index`: `LibraryIndex`, the libraries' types a caller indexes, and `LibraryType`; `BotIndex`, the whole bot parsed
  in one batch with bindings, cached by content, over sources the caller hands in; `nav`, where a binding is used
  (`Usages`, `Uses`, `StructureKind`), the renames and deletes planned from that (`Refactor`, `TypeRename`,
  `TypeDelete`) and `TextSearch`. `BotIndex` keeps one cached index and stamps the classpath's jars, and
  `ApiDocsParser` reads a sources jar: the module's two reads of files, moved as they were from Studio.
- `edit`: the rewrites of a unit: `EditContext`, `ImportManager`, `BlockId`, `factories` (`InitializerFactory`,
  `ExpressionFactory`), `handlers` and `helpers` (`AstRewriteHelper`, `LocalUses`); `guard`, whether an edit adds a
  compile error (`CompileGuard`) and the imports it leaves unused; `refactor` (`MethodReferences`, `ReviewMarks`);
  `palette`, the kinds of block and expression an edit inserts, the catalogues that offer them and a function's
  signature types (`BotType`, `SignatureType`, `FunctionDraft`); what reads the bound plugins' types takes the caller's
  `ValueGrammar`, never a plugin registry. `StatementPlacement` says where a block may go; `refactor.SignatureMigration`
  plans a signature change.

## Rules

- Write Java with JDT trees, never strings or templates. Three places may hold Java as text: a comment
  (`edit.helpers.CommentText` makes text from outside the program safe in one), a method restored exactly as written,
  and the generated ▶ Try class. A check on raw text may only be a reason not to parse, never the answer; a file that
  does not parse is read by `parse.SourceTokens`, because JDT's recovery drops code.
- Compare a written type with `grammar.TypeNodes`, never by what a node prints.
- Imports nothing above it: no `javafx`, Studio, core, plugin-host, SDK or cli (`JavaLayersTest`).
- `botmaker-studio-api` stays `provided`: a contract class must be one `Class` object across the plugin boundary.
- JDT is a plain dependency, and this pom is the only one that names its version. A host that must not download it,
  such as plugin-host and the cli's main jar, declares this module `optional`.
