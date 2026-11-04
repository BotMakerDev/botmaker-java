# Changelog

All notable changes to `botmaker-java`.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this module uses
[semantic versioning](https://semver.org/). `release.sh` refuses to cut a version with no section here.

## [Unreleased]

### Added

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

### Fixed

- A default value for a type no project source declares keeps its package and generics (2026-11-04): a
  `Consumer<MatchResult>` slot was enriched to the name `MatchResult>` and its seed threw.
