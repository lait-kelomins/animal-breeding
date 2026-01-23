# Dev Notes

This folder contains progress logs for features and bugfixes in development.

## Purpose
- Track approaches tried (success/failure) for each feature
- Document learnings to prevent repeating failed approaches
- Provide context for resuming work after breaks

## File Naming Convention
- `feature-{name}.md` - For new features
- `bugfix-{name}.md` - For bug fixes

## Template
Each file follows this structure:

```markdown
# Feature/Bugfix: [Name]
Branch: `feature/xyz` or `bugfix/xyz`
Started: YYYY-MM-DD

## Goal
[What we're trying to achieve]

## Attempts Log

### Attempt 1 - [Approach Name]
**Status:** ✅ Success / ❌ Failed / 🔄 In Progress
**What we tried:** [Description]
**Result:** [What happened]
**Learnings:** [Key takeaways]

## Final Solution
[What worked]

## Files Modified
- `path/to/file.java` - [description]
```

## Current Features in Development
- `feature-nametag-persistence.md` - Death detection and respawn persistence
