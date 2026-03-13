# Codex Instructions — NFCWorkFlow (MSc Project 25-26)

## Code Review Guidelines

> **Important:** The following rules must be strictly followed.

1. **Language:** For every pull request review, always write the review comments in Chinese (Simplified Chinese / 简体中文), even if the pull request title, description, commit messages, or changed code are in English. Do not reply in English unless the user explicitly requests English in the pull request comment thread.

2. **Change explanations:** If a suggested change is given, explain in detail why this action was taken, including:
   - What problem the change solves
   - Why this approach was chosen over alternatives
   - How the change impacts code quality, performance, security, or maintainability
   - Code examples showing the expected result, if necessary
  
3. **Key points:** If no significant issues are found, clearly state that the changes look safe and well-structured. Leave a comment in the pull requests regardless of whether there is a problem or not.
   - Focus on correctness, security, and unintended side effects.
   - Identify logical bugs, edge cases, missing validations, and error handling issues.
   - Flag potential security risks such as injection, data leakage, improper auth checks, or unsafe dependencies.
   - Highlight performance regressions or inefficient patterns when relevant.
   - Avoid nitpicking style unless it affects readability or maintainability.
   - Always include file path and line references when pointing out issues.
   - Be concise, specific, and technical. Avoid generic praise.

