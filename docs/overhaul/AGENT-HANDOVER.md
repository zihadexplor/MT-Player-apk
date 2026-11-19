# Agent Handover Guide — UI/UX Overhaul (`feat/ui-overhaul`)

Read this whole file before doing anything. It is the single source of truth for resuming the
overhaul. Update the "Progress" and "Next steps" sections after every push.

## Progress

| Phase | Status | Commit |
|---|---|---|
| P0 Audit, docs, handover | Done | 43f070c |
| P1 Design system + greeting | Done | 5587b8f |
| P2 Top navigation + dock | Done | 5587b8f |
| P3 Home Jump back in + feed | Done | 5587b8f (UI test in the next commit) |
| P4 Global refresh | Done (Now Playing, pill tabs, detail headers, chips) | see `git log` |
| P5 Settings About credit | Done | 5587b8f |
| P6 Brand / logo ("Ember Cascade") | Done | see `git log` |
| P7 Tests + CI green | In progress — watch the latest run on `feat/ui-overhaul` | |
| P8 PR Proposal (no PR, no merge) | Not started | |

## Next steps (exact)

1. Wait for the latest CI run on `feat/ui-overhaul` (all five jobs). If an emulator job fails,
   download `emulator-evidence-api<N>` and follow the rea root-cause method before changing code.
2. Review the CI screenshots (home, music, playing, after-kill, night, day) for contrast and clipping.
3. When every job is green, post the formal PR Proposal to the owner in chat. Do not open a PR,
   do not merge. Wait for explicit approval.
4. Version bump (e.g. 2.1.0 / code 8) is intentionally not done; ask the owner in the proposal.

## Logo notes (P6)

- Mark: a play triangle cut into three rising "sound steps" (video and music in one glyph),
  amber `#FFC27A` -> `#FF8A4C` -> ember `#FF5A36`, on warm ink `#0E0B10`/`#2B2027` with a soft
  ember glow. Colours live in `res/values/colors.xml` (`logo_*`).
- Files (names unchanged, ADR-015): `ic_launcher_background/foreground/monochrome.xml`,
  `ic_splash.xml` (ink disc so it reads on light and dark splash), `tv_banner.xml`,
  `tv_banner_background.xml` (now 160x90dp, which also clears the old VectorRaster lint warning).
- The geometry was generated from one script (play triangle (36,30)-(84,78) apex at y=54, three
  segments, 4.5 gap, scale 0.8 around centre) and rendered at 48/216/324 px on light and dark
  before committing.

## Key decisions (summary — details in 01-architecture.md)

- ADR-012: Yumeiro (GPL-3.0 + trademark policy) is studied, never copied. No Yumeiro code, strings,
  images, logo or font files from its repo.
- ADR-013: greeting = `dayPartFor(hour)` (5–11 / 12–16 / 17–21 / else), refreshed at the next hour
  boundary and on resume; string resources.
- ADR-014: Jump back in uses existing local state only (restored queue, play history,
  `video_progress`). No new storage, no migration.
- ADR-015: new vector-only brand mark in the Ember Night palette; resource names unchanged.

## Environment and commands

- Repo: `https://github.com/zihadexplor/MT-Player-apk`, local `/home/user/workspace/mtplayer`.
- Reference: `gh repo clone siamform/yumeiro` (study only).
- Skills (cloned in `~/skills`): `ui-ux-pro-max-skill`, `kotlin-agent-skills`, `rea`,
  `claude-skills/skills/architecture-designer`.
- Build: `ANDROID_HOME=~/android-sdk JAVA_HOME=~/jdk PATH=~/jdk/bin:$PATH ./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin`
  (lint report: `app/build/reports/lint-results-debug.txt`).
- No KVM locally: emulator tests run only in CI (`.github/workflows/ci.yml`, jobs: build/test/lint,
  emulator API 29/34/35, update over v1.3.0). CI runs on pushes to non-main branches; a head
  commit containing `[skip ci]` runs nothing. Do not open a PR (that would also start duplicate runs).
- Watch: `gh run list -R zihadexplor/MT-Player-apk --branch feat/ui-overhaul`,
  `gh run watch <id> --interval 60 --exit-status`; evidence artifacts `emulator-evidence-api<N>`.
- Commits end with the trailer `Co-authored-by: Perplexity <noreply@perplexity.ai>`.

## Standing rules from earlier owner messages (still binding)

- Keep the same package name, signing credentials, and app identity so it remains an update, not a new app.
- Scope remains strictly local/offline only. No online streaming, no online search, no cloud features.
- Never expose any credential in code, commits, logs, docs, issues, crash reports, or chat.
- Dark-first theme, fully readable light theme, correct system theme ("no white text on light
  backgrounds"). Every interactive element needs proper touch targets and feedback.
- Do not claim physical-device verification from emulator results. Maintain `docs/OWNER-MANUAL-TESTS.md`.
- Push regularly after every meaningful change or completed task; never leave substantial work
  uncommitted or unpushed. After each push, give the owner a concise summary of changes, completed
  work, remaining tasks and known issues. Keep this handover guide updated.
- Use CI efficiently: inspect running workflows, avoid duplicate runs, test the smallest relevant
  scope first.
- Never silently wipe or overwrite user data.

## The overhaul brief, preserved verbatim (binding for every future agent)

> # Master Engineering & UI/UX Overhaul Prompt
>
> ## 1. Mandatory Agent Skills
> Load and apply the following skills wherever relevant throughout execution:
> - **UI/UX Design (Mandatory for any UI/UX refinement or redesign):** `https://github.com/nextlevelbuilder/ui-ux-pro-max-skill.git` — Must be invoked *first* before making any visual or UX changes.
> - **Kotlin & Android Development:** `https://github.com/Kotlin/kotlin-agent-skills.git` — Apply for Android-specific implementation and architectural choices.
> - **Root-Cause Problem Solving (Critical):** `https://github.com/morluto/rea.git` — Mandatory whenever encountering blockers, bugs, or ambiguity before guessing or giving up.
> - **System Architecture:** `https://github.com/jeffallan/claude-skills` (inspect `architecture-designer/SKILL.md` before installing) — Use for system-level architecture, ADRs, scalability, and non-functional requirements.
>
> ---
>
> ## 2. Core Engineering & Operational Rules
> 1. **Audit Before Execution:** Act as a Senior Software Engineer, Expert Debugger, and Systems Architect. Before modifying any code, inspect the entire repository—including all branches, commit history, and existing documentation—and configure a clean development environment.
> 2. **Security & Continuity:** Never expose credentials, API keys, or secrets in code, commits, logs, docs, issues, crash reports, or chat outputs. Read all documentation written by previous agents before acting.
> 3. **Scale & Cross-Device Architecture:** Research competitors and engineer a superior solution designed for **200,000 global monthly active users (MAU)**. Ensure smooth performance and responsive layouts across all device tiers—from low-RAM entry-level phones to flagship smartphones and Android TV.
> 4. **Strict Scope Discipline:** Execute only what is specified. Do not over-engineer or introduce unrequested features.
>
> ---
>
> ## 3. Feature & Redesign Specifications
> Clone and study the reference repository (`gh repo clone siamform/yumeiro`) to adopt its design language, architecture, and UX patterns:
>
> 1. **Dynamic Time-Based Top Navigation:**
>    - Redesign the top navigation bar to match Yumeiro's style.
>    - Inherit Yumeiro’s dynamic time-based greeting system (e.g., *Good Morning*, *Good Evening*, *Good Night*) directly within the top nav.
> 2. **Home Section Restructuring ("Jump Back In"):**
>    - Remove the existing **"Shuffle All"** and **"Videos"** buttons/sections.
>    - Inherit and implement the **"Continue" / "Jump Back In"** section from Yumeiro.
> 3. **Yumeiro-Style Content Feed:**
>    - Refactor the main content feed layout, card hierarchy, and scrolling experience to mirror Yumeiro’s aesthetic and structure.
> 4. **App Branding & Logo Redesign:**
>    - Replace the current unprofessional app icon/logo.
>    - Act as a senior graphic designer (applying graphic/Photoshop design principles) to craft a cohesive, modern app logo that matches the app's name, color palette, and visual identity—similar to how Yumeiro’s logo reflects its own distinct brand vibe.
> 5. **Global Modern UI Refresh:**
>    - Overhaul outdated visual elements across the application to establish a unified, modern design system inspired by Yumeiro.
> 6. **Settings Attribution:**
>    - Add the trademark/credit line: **`Made with love by Tawfik Estiak Zihad`** inside **Settings > About**.
>
> ---
>
> ## 4. Git Workflow, Documentation & Handover Protocol
> - **Branching & Phased Execution:** Create a dedicated feature branch and structure the implementation into clear, sequential phases.
> - **Upfront Documentation & Handover:** Because sandbox sessions or token limits may reset mid-task, generate all architectural docs, phase breakdowns, and a comprehensive **Agent Handover Guide** upfront. The handover guide must summarize current progress, architectural decisions, and exact next steps so a subsequent agent can resume seamlessly, and must explicitly preserve all rules in this prompt for future agents.
> - **Verification & Approval Gate:**
>   - Write and run all unit/UI tests and ensure all CI checks pass completely.
>   - **Do NOT open a Pull Request or merge** into the main branch automatically.
>   - Once everything is tested and CI passes, present a formal **PR Proposal** summarizing the changes and wait for explicit user approval.
>
> Execute
>
