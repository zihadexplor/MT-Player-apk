# ADR-007: Settings in DataStore Preferences

## Status
Accepted

## Decision
All preferences (theme, accent, dynamic colour, playback, video gestures, library filters,
updater) live in one `SettingsStore` backed by DataStore Preferences and exposed as a
typed `StateFlow<Settings>`. Room holds user content only.

## Alternatives considered
Room single-row tables (old approach): heavy for key/value data, needs migrations for
every new toggle. SharedPreferences: synchronous I/O on main thread.
