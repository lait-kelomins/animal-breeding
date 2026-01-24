# Todos

This folder contains plans and progress logs for features and bugfixes in development.

## Purpose
- Document implementation plans for upcoming work
- Track approaches tried (success/failure) for each feature
- Document learnings to prevent repeating failed approaches
- Provide context for resuming work after breaks

## File Naming Convention
- `plan-{name}.md` - Implementation plans
- `feature-{name}.md` - Feature progress logs
- `bugfix-{name}.md` - Bug fix progress logs

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

## Current Work
- `plan-taming-integration.md` - Taming system integration plan
- `api-breeding-plugin.md` - API reference for breeding plugin (com.laits.breeding)
- `api-taming-plugin.md` - API reference for taming plugin (com.tameableanimals)
- `feature-nametag-persistence.md` - Death detection and respawn persistence
