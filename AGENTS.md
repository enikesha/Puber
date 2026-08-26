# Puber Repository Guide

Puber is an Android TV KinoPub client. Runtime code is in `:app`; baseline
profile generation is in `:baselineprofile`. Package: `com.kino.puber`.

## Context Index

- Kent lifecycle and delivery: `.kent/project-contract.md`.
- Node-specific reading budgets: `.kent/context/*.md`.
- Workflow and architecture index:
  `.kent/skills/puber-android-workflow/SKILL.md`.
- Step-specific recipes:
  `.kent/skills/puber-android-workflow/references/recipes/`.
- Workflow, MCP, Serena, routing, and feature-target rules:
  `.kent/skills/puber-android-workflow/references/rules/`.

Read the active node's manifest first and load only the recipes required by the
current step or finding.

## Build And Worktrees

- Flavors: `dev` and `prod`.
- Main compile task: `:app:compileDevDebugKotlin`.
- Main checkout may use `./gradlew`.
- Any project-local or Kent-managed worktree uses
  `./tools/agentw <task>`.
- Dependency versions come from `gradle/libs.versions.toml`; do not hardcode
  them.
- Detekt configuration lives under `config/detekt/`.
- Project-local worktrees live under `.kent/worktrees/`; do not create sibling
  worktrees. Kent-managed worktrees remain Kent-owned and must not be moved.
- Worktree SDK setup may write only `sdk.dir` to `local.properties`. Never copy
  KinoPub/TMDB credentials into task worktrees.

## Architecture Gotchas

- DI uses Koin DSL. Global modules are assembled in `PuberApp.kt`; screen
  dependencies live in the screen's `buildModule(scopeId, parentScope)`.
- Screens implement parcelable `PuberScreen` and navigate through `AppRouter`.
- Parameterized screens must override `key` with stable navigation identity so
  Voyager state, TV focus, and DI scopes do not collide. Computed parcelable
  keys use `@IgnoredOnParcel`.
- ViewModels extend `PuberVM<ViewState>` or `PagingVM<T, VS>`.
- Content composables are pure: state plus
  `onAction: (UIAction) -> Unit`.
- UI uses Compose TV Material3. Load the matching navigation, DI, ViewModel,
  Compose, filtering, paging, API, or testing recipe before editing.
- API access is centralized in `KinoPubApiClient`; API models are used directly
  by project domain/UI mapping.
- User-visible strings live in `res/values/strings.xml`; non-composable code
  uses `ResourceProvider`.

## Runtime And External Systems

- Smoke follows `.kent/commands/smoke-test.md` and
  `.kent/context/smoke.md`.
- Acquire a TV emulator lease before install, launch, input, logs, or MCP
  targeting. Use the exact serial for every adb and target-specific MCP call.
- Install the freshly built dev APK; do not use an implicit Gradle install
  target.
- Use `~/.kent/bin/kent-mcp-call` and
  `~/.kent/bin/kent-mcp-list`, never raw `mcporter`.
- Keep credentials, broad UI dumps/logs, playback/account mutations, and raw
  authenticated responses out of Git and workflow evidence unless an explicit
  task authorization permits the exact action.

## Local Chromecast Release Deployment

- Commit all finished implementation before building or deploying. Build from
  the intended committed `HEAD`; never include unrelated uncommitted changes in
  a deployed APK.
- The known local Chromecast is `sabrina_prod_stable` (`device:sabrina`). Its
  direct ADB serial was `192.168.0.149:42127` on 2026-08-25; it may also appear
  through an `_adb-tls-connect._tcp` alias. Re-resolve the current exact serial
  with the mobile resource-lock adapter and `adb devices -l` before use.
- Physical-device deployment still requires explicit authorization for the
  current task. Acquire the exact serial's shared resource lock before any
  install, launch, shell, log, input, or MCP call.
- Build the committed production release with `:app:assembleProdRelease`. The
  APK is `app/build/outputs/apk/prod/release/app-prod-release.apk` and the
  package is `com.kino.puber`. In worktrees, use `./tools/agentw` and supply
  required credentials transiently through the environment; never copy them
  into worktree `local.properties`.
- Install only through `.kent/adapters/mobile/android-apk-install-preserve`
  using the selected serial and production package. Never uninstall, clear app
  data, allow a downgrade, or bypass signer validation implicitly.
- On this host, Homebrew `apkanalyzer` needs
  `APKANALYZER_OPTS=-Dcom.android.sdklib.toolsdir=$HOME/Library/Android/sdk/cmdline-tools/latest`.
  Pin `APKSIGNER=$HOME/Library/Android/sdk/build-tools/36.1.0/apksigner` until
  the preservation adapter accepts Build Tools 37's `V2 Signer` output label.

## Repository Hygiene

- Generated/modified file content and commit messages are English unless the
  user explicitly requests otherwise.
- Do not create implicit `.todo/.current` pointers.
- Kent task state owns lifecycle; `plan.md` tracks writer-owned steps and
  `meta.json` stores identity/source metadata only.
