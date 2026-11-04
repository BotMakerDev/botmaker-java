# Changelog

All notable changes to `botmaker-java`.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this module uses
[semantic versioning](https://semver.org/). `release.sh` refuses to cut a version with no section here.

## [Unreleased]

### Added

- The module (2026-11-04): the value grammar and `SourceParser` moved from `botmaker-plugin-host`
  (`com.botmaker.plugin.host.grammar` is now `com.botmaker.java.grammar`), with their history at
  `botmaker-plugin-host@df71893`. `JavaLayersTest` keeps it free of UI and hosts, with one parser.
