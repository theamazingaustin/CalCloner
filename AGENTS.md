<!-- graft:start -->
## Graft — repo context graph

This repo is indexed in `graft/`: small linked markdown nodes that explain each
system and carry exact file:line spans, kept in sync with the code through git.

For ANY task here — understanding how something works, finding where code lives,
or scoping a change — get context from the graph before grepping or opening
source files. Re-ask freely (it's cheap) and reuse literal identifiers you
already have (symbol, error string, file name) as the query. New to this repo?
Run `graft map` first — a token-budgeted orientation (dir clusters, hubs,
hotspots), no LLM, no key.

- Run `graft ask "<your question>" --source` → ranked nodes with the relevant
  code spans inlined (each hit's ≤8-line crux by default; `--full` for whole
  definitions when the crux isn't enough). Match the tool to the task shape:
  for understanding or editing, the top node IS the answer — cite its
  `covers:` file:line spans and edit straight from `--source`. For
  exhaustive tasks ("every occurrence / every caller of this pattern"), ranked
  results are top-N, not complete — run `graft grep "<literal>"` instead
  (exhaustive over indexed files, grouped by enclosing symbol), falling back
  to raw `grep -rn` only for unindexed files.
- `graft skeleton <file>` → every definition's signature + span, ~10× cheaper
  than reading the file; use it to skim an API surface.
- `graft callers <symbol>` gives precomputed, exact edges — who calls this.
  Add `--direction out` for what it calls, or `--depth N` to walk
  transitively for the full blast radius. For structural questions, skip
  ranking and use this directly.
- Or browse: `graft/INDEX.md` lists every node; follow the links.
- Monorepos and folders of multiple repos rank fairly across sub-projects —
  hits carry `[scope/]` labels naming which one they're from. Narrow with
  `graft ask "<task>" --in <scope>/` once you know where you're working.

If a returned span is truncated ("+N more lines"), open the file at that exact
range before finalizing. Only open source files when a node genuinely lacks a
needed detail, and then at the exact file:line the node points to — never
re-read whole files.

After big code changes, refresh the graph with `graft build` (deterministic,
no API key, $0).
<!-- graft:end -->

## Release & Version Tagging Rules
Whenever committing and pushing release/feature changes intended for GitHub builds, **ALWAYS** create and push an annotated or lightweight git tag starting with `v` (e.g. `v<version>-<month>.<day>-<buildNumber>`). The GitHub Actions workflow specifically listens on `tags: ['v*']` to build and publish the release APK.
After pushing a release tag, launch `powershell -ExecutionPolicy Bypass -File .\scripts\monitor_release.ps1` as an async background task via `run_command` (with `IsDaemon: false`). Stop calling tools to sleep until the task completes reactively, then present the resulting APK download URL directly to the user with zero token waste.

## Strict Tooling & Architecture Rules: Graft & Ponytail

### 1. Mandatory Graft Protocol (No Direct Grepping or Blind File Reading)
- **DO NOT use native `grep_search` or `find_by_name` on indexed project files.** You MUST always use `graft grep "<pattern>"` (or `graft ask "<question>" --source`) via `run_command`.
- **DO NOT view whole source files with `view_file`** without first running `graft skeleton <file>` or `graft callers <symbol>` to identify the exact line range. Only view the specific targeted line slice when the graft crux isn't sufficient.
- **Before refactoring or editing any symbol**, run `graft callers <symbol> --depth 2` to verify blast radius and connected call sites.

### 2. Mandatory Ponytail Protocol (YAGNI & Minimal Code)
- Implement the absolute minimum code required to fulfill the prompt (YAGNI).
- Exclude speculative abstractions, wrapper types, unnecessary interfaces, and heavy dependencies.
- Prioritize concise, idiomatic, single-purpose implementations over boilerplate architectures.


