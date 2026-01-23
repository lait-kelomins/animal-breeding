# Contributing to Lait's Animal Breeding

Thanks for your interest in contributing! This guide will help you get started.

## Getting Started

### Prerequisites

- Java 21 (required - HytaleServer.jar is compiled with Java 21)
- Gradle 9.x
- A Hytale server for testing

### Setup

1. Fork and clone the repository
2. Add `HytaleServer.jar` to the `libs/` folder (not included in repo)
3. Install git hooks (verifies build before push):
   ```bash
   ./scripts/setup-hooks.sh
   ```
4. Build the project:
   ```bash
   ./gradlew build -x test
   ```
5. Copy the JAR from `build/libs/` to your server's `mods/` folder

## Git Flow Workflow

We use Git Flow for development. Here's how it works:

```
main ──────────────────────────► stable releases
         ▲
         │ merge via PR
    release/x.x.x
         ▲
         │ merge via PR
develop ───────────────────────► integration branch
    ▲         ▲
    │         │ merge via PR
feature/a   feature/b
```

### Branches

| Branch | Purpose |
|--------|---------|
| `main` | Stable releases only - never commit directly |
| `develop` | Integration branch - PRs merge here |
| `feature/*` | New features |
| `bugfix/*` | Bug fixes |
| `release/*` | Release preparation |
| `hotfix/*` | Urgent fixes for production |

### Working on a Feature

1. **Create a feature branch from `develop`:**
   ```bash
   git checkout develop
   git pull origin develop
   git checkout -b feature/my-feature
   ```

2. **Make your changes and commit:**
   ```bash
   git add .
   git commit -m "Add my feature"
   ```

3. **Push and create a Pull Request:**
   ```bash
   git push origin feature/my-feature
   ```
   Then open a PR targeting `develop` on GitHub.

### Commit Messages (Conventional Commits)

We use [Conventional Commits](https://www.conventionalcommits.org/) format. The commit-msg hook will validate this.

**Format:** `type(scope): description`

| Type | Description |
|------|-------------|
| `feat` | New feature |
| `fix` | Bug fix |
| `docs` | Documentation only |
| `style` | Formatting, no code change |
| `refactor` | Code change without feat/fix |
| `test` | Adding tests |
| `chore` | Maintenance tasks |
| `build` | Build system changes |
| `ci` | CI configuration |
| `perf` | Performance improvement |

**Examples:**
```
feat: add heart particles when animals breed
fix(breeding): crash when feeding baby animals
docs: update README with installation steps
refactor(growth): simplify age calculation logic
```

The scope (in parentheses) is optional but helpful for larger changes.

## Code Style

- Follow existing code patterns in the project
- Use meaningful variable and method names
- Add comments for complex logic
- Keep methods focused and small

## Testing

Before submitting a PR:
1. Build successfully: `./gradlew build`
2. Test in-game with a Hytale server
3. Verify your feature works as expected
4. Check for regressions in existing features

## Pull Request Guidelines

- Target the `develop` branch (not `main`)
- Fill out the PR template completely
- Keep PRs focused - one feature/fix per PR
- Include screenshots or videos for UI changes
- Respond to review feedback promptly

## Questions?

Open an issue or reach out on Discord: **lait_kelomins**
