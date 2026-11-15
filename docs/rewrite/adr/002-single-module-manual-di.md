# ADR-002: Single module, package-by-feature, manual DI

## Status
Accepted

## Context
One small team, CI on 2-core runners, ~15k lines expected. Hilt adds KAPT/KSP processing
and build time; multi-module adds configuration overhead.

## Decision
One `:app` module organised by feature packages. A small `AppContainer` created in
`Application.onCreate` owns singletons (database, repositories, settings, player
connection). ViewModels get dependencies through a `viewModelFactory` initializer.

## Alternatives considered
- Hilt: compile-time safety, but slower builds and more generated code.
- Koin: runtime DI, extra dependency for little benefit at this size.
- Multi-module: better isolation, but premature for this size.

## Consequences
Fast builds, explicit wiring. If the app grows past ~40k lines, revisit modularisation.
