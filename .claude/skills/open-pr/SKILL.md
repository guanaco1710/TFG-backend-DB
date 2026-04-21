---
name: open-pr
description: Commit the current changes on a feature branch and open a pull request with a standardized title and body. Use when the user says "open a PR", "ship this", or similar. Delegates git/gh operations to the github-manager agent.
---

# /open-pr

## Steps

1. **Pre-flight.** Run in parallel:
   - `git status` (untracked + modified)
   - `git diff` and `git diff --staged`
   - `git log --oneline -10`
   - `git branch --show-current`

2. **Refuse to PR from `main`.** If on `main`, stop and ask the user for a branch name, then create it (`<type>/<kebab-summary>`, e.g. `feat/booking-cancellation`).

3. **Checks before committing.**
   - `./mvnw test` — must pass. If it doesn't, surface failures and stop.
   - Run built-in `/security-review` on the diff. Surface findings; ask the user whether to proceed if anything is flagged.

4. **Delegate to `github-manager`** to:
   - Stage files explicitly (no `git add -A`).
   - Commit with a conventional message (`feat:`, `fix:`, `refactor:`, etc.) ending with the Co-Authored-By trailer.
   - Push with `-u` if the branch has no upstream.
   - Open the PR with:
     - Title under 70 chars
     - Body with `## Summary` (1–3 bullets) and `## Test plan` (checklist)

5. **Return** the PR URL.

## Don'ts

- Don't force-push.
- Don't skip hooks (`--no-verify`).
- Don't commit `.env`, keys, or anything that looks like a secret — warn the user if the diff contains suspicious strings.
