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
  `params/` to `starting-params/`; `BotAnnotation`, a BotMaker annotation known by what its imports resolve to.
- `parse`: `SourceSet`, the read-only view of a project's sources that a caller hands in: its files, their text and
  trees, its classpath and source root. This module holds no project; it reads one through this.

## Rules

- Write Java with JDT trees, never strings or templates.
- Imports nothing above it: no `javafx`, Studio, core, plugin-host, SDK or cli (`JavaLayersTest`).
- `botmaker-studio-api` stays `provided`: a contract class must be one `Class` object across the plugin boundary.
- JDT is a plain dependency. A host that must not download it, such as plugin-host and the cli's main jar, declares
  this module `optional`.
