#!/bin/bash
# Setup git hooks for the project
# Run this after cloning the repository

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(dirname "$SCRIPT_DIR")"
HOOKS_DIR="$REPO_ROOT/.git/hooks"

echo "Setting up git hooks..."

# Create commit-msg hook for conventional commits
cat > "$HOOKS_DIR/commit-msg" << 'EOF'
#!/bin/bash
# Commit message hook: Validates conventional commit format
# Format: type(scope): description
# Types: feat, fix, docs, style, refactor, test, chore, build, ci, perf

commit_msg=$(cat "$1")

# Allow merge commits
if echo "$commit_msg" | grep -qE "^Merge "; then
    exit 0
fi

# Allow revert commits
if echo "$commit_msg" | grep -qE "^Revert "; then
    exit 0
fi

# Conventional commit pattern
pattern="^(feat|fix|docs|style|refactor|test|chore|build|ci|perf)(\(.+\))?: .{1,}"

if ! echo "$commit_msg" | grep -qE "$pattern"; then
    echo ""
    echo "❌ INVALID COMMIT MESSAGE"
    echo ""
    echo "Commit message must follow Conventional Commits format:"
    echo "  type(scope): description"
    echo ""
    echo "Types:"
    echo "  feat     - New feature"
    echo "  fix      - Bug fix"
    echo "  docs     - Documentation only"
    echo "  style    - Formatting, no code change"
    echo "  refactor - Code change without feat/fix"
    echo "  test     - Adding tests"
    echo "  chore    - Maintenance tasks"
    echo "  build    - Build system changes"
    echo "  ci       - CI configuration"
    echo "  perf     - Performance improvement"
    echo ""
    echo "Examples:"
    echo "  feat: add animal naming with nametags"
    echo "  fix(breeding): crash when feeding baby animals"
    echo "  docs: update README with installation steps"
    echo ""
    echo "Your message: $commit_msg"
    echo ""
    exit 1
fi

exit 0
EOF

chmod +x "$HOOKS_DIR/commit-msg"

# Create pre-push hook
cat > "$HOOKS_DIR/pre-push" << 'EOF'
#!/bin/bash
# Pre-push hook: Verify build before pushing
# This prevents pushing broken code to the repository

echo "🔨 Running pre-push build verification..."

# Get the repo root directory
REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

# Detect OS and use appropriate gradle command
if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "cygwin" || "$OSTYPE" == "win32" ]]; then
    # Windows - set JAVA_HOME if not set
    if [ -z "$JAVA_HOME" ]; then
        export JAVA_HOME="/c/Program Files/Microsoft/jdk-21.0.9.10-hotspot"
    fi
    ./gradlew.bat build -x test --quiet 2>&1
else
    # Linux/Mac
    ./gradlew build -x test --quiet 2>&1
fi

BUILD_EXIT_CODE=$?

if [ $BUILD_EXIT_CODE -ne 0 ]; then
    echo ""
    echo "❌ BUILD FAILED - Push rejected"
    echo "Fix the build errors before pushing."
    echo ""
    exit 1
fi

echo "✅ Build successful - Push allowed"
exit 0
EOF

chmod +x "$HOOKS_DIR/pre-push"

echo "✅ Git hooks installed successfully!"
echo ""
echo "Hooks installed:"
echo "  - commit-msg: Validates conventional commit format"
echo "  - pre-push:   Verifies build before pushing"
echo ""
echo "Conventional commit types:"
echo "  feat, fix, docs, style, refactor, test, chore, build, ci, perf"
echo ""
echo "Example: git commit -m \"feat: add new breeding animation\""
