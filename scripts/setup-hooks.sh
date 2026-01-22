#!/bin/bash
# Setup git hooks for the project
# Run this after cloning the repository

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(dirname "$SCRIPT_DIR")"
HOOKS_DIR="$REPO_ROOT/.git/hooks"

echo "Setting up git hooks..."

# Create pre-push hook
cat > "$HOOKS_DIR/pre-push" << 'EOF'
#!/bin/bash
# Pre-push hook: Verify build before pushing
# This prevents pushing broken code to the repository

echo "🔨 Running pre-push build verification..."

# Set JAVA_HOME for the build
export JAVA_HOME="/c/Program Files/Microsoft/jdk-21.0.9.10-hotspot"

# Get the repo root directory
REPO_ROOT="$(git rev-parse --show-toplevel)"

# Run gradle build (skip tests for speed, but compile everything)
cd "$REPO_ROOT"
./gradlew.bat build -x test --quiet 2>&1

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
echo "  - pre-push: Verifies build before pushing"
