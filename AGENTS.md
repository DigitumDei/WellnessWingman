# Repository Guidelines

## Project Structure & Module Organization
- `shared/` contains shared domain, data, and platform abstractions for the Kotlin Multiplatform codebase.
- `composeApp/` contains shared Compose UI, while `androidApp/` and `iosApp/` provide platform entry points and packaging.
- SQLDelight schema and migrations live under `shared/src/commonMain/sqldelight/`; platform-specific implementations live in the matching `androidMain/`, `iosMain/`, and `desktopMain/` source sets.
- Register shared services through the Koin modules under `shared/src/commonMain/kotlin/com/wellnesswingman/di/`.

## Build, Test, and Development Commands
- `./gradlew assembleDebug` builds the Android debug app from the repo root.
- `./gradlew :shared:desktopTest` runs the shared test suite used in CI.
- `./gradlew :shared:koverXmlReport` generates the XML coverage report consumed by Codecov.
- `./gradlew :shared:koverHtmlReport` generates a browsable local coverage report.
- `./gradlew check` runs the broader Gradle verification tasks when you need a full pre-PR validation pass.

## Coding Style & Naming Conventions
- Follow Kotlin conventions: 4-space indentation, PascalCase for types, camelCase for functions/properties, and package names matching the directory structure.
- Keep shared business logic in `shared/` and shared UI in `composeApp/`; platform-specific code should stay in the appropriate source set instead of leaking conditionals into common code.
- Run the relevant Gradle formatting or verification tasks already configured in the module you touch before publishing changes.

## Testing Guidelines
- Place shared tests under `shared/src/commonTest/`, mirroring production packages.
- Name test classes `<TypeUnderTest>Test` unless the surrounding package already uses a different convention.
- Mock AI integrations so Gradle test runs remain deterministic and offline-friendly.

### Code Coverage
- Run coverage with `./gradlew :shared:desktopTest :shared:koverXmlReport :shared:koverHtmlReport`.
- This generates machine-readable XML at `shared/build/reports/kover/report.xml` and browsable HTML under `shared/build/reports/kover/html/`.
- Generate additional summaries with your preferred reporting tooling if needed; Codecov consumes the XML report directly in CI.
- Example local report workflow:
  ```bash
  ./gradlew :shared:desktopTest :shared:koverHtmlReport
  ```
- Open `shared/build/reports/kover/html/index.html` in a browser to inspect coverage locally.
- Target minimum coverage thresholds: 80% line coverage, 70% branch coverage for production code.

## Security & Configuration
- Store user-provided LLM keys with platform keystores or secure platform storage abstractions, never in plaintext files.
- Keep inference and prompting on-device, opt out of telemetry, and document any third-party SDK data handling.
- When adding caching or logs, redact prompts and health data by default and guard debugging hooks behind compilation symbols.

## Commit & Pull Request Guidelines
- Never create commits or push branches until the requester has reviewed the changes and explicitly asked you to do so.
- Write commits with short, imperative subjects (e.g., "Remove aspire") and include context when touching multiple layers.
- Scope diffs narrowly and call out security implications of storage or AI changes in commit bodies or PR notes.
- For PRs, provide a summary, linked issue, UI screenshots or recordings if applicable, per-platform manual test notes, and clarity on how sensitive data stays protected.

## GitHub CLI Usage

### Working with Pull Requests
- `gh pr create --title "Title" --body "Description"` - Create a new PR from current branch
- `gh pr create` - Create PR interactively with prompts
- `gh pr view [number]` - View PR details (omit number for current branch)
- `gh pr diff [number]` - View PR diff
- `gh pr list` - List all open PRs
- `gh pr comment [number] --body "Comment text"` - Add comment to PR
- `gh pr review [number] --approve` - Approve a PR
- `gh pr review [number] --comment --body "Feedback"` - Add review comments
- `gh pr merge [number]` - Merge a PR

### Working with Issues
- `gh issue create --title "Title" --body "Description"` - Create a new issue
- `gh issue create` - Create issue interactively
- `gh issue list` - List all open issues
- `gh issue view [number]` - View issue details
- `gh issue comment [number] --body "Comment"` - Add comment to issue
- `gh issue close [number]` - Close an issue
- `gh issue edit [number] --add-label "bug,priority:high"` - Add labels
- `gh issue edit [number] --add-assignee @me` - Assign issue

### Advanced Options
- Use `--label`, `--assignee`, `--milestone`, `--project` flags with create commands
- Use `--web` flag to open in browser (e.g., `gh pr view --web`)
- Pass multi-line content via heredoc:
  ```bash
  gh issue create --title "Title" --body "$(cat <<'EOF'
  Multi-line
  description
  EOF
  )"
  ```

### CI/CD Integration
- `gh pr checks [number]` - View CI check status
- `gh run list` - List workflow runs
- `gh run view [run-id]` - View specific workflow run details
