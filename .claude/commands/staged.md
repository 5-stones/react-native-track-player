---
allowed-tools: Bash(git add:*), Bash(git status:*)
description: Reference currently staged changes for analysis or questions
---

## Context

- Current git status: !`git status`
- Current `git diff --staged`: !`git diff --staged`
- Current branch: !`git branch --show-current`
- Recent commits: !`git log --oneline -10`

## Instructions

You have access to the currently staged changes shown above. The user may ask you questions about these changes, request analysis, or ask you to perform tasks related to the staged content.

Please read the relevant files containing the staged changes to provide proper context for your response.