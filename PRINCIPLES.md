# Development Workflow

Prioritize speed, correctness, and minimal context usage.

## Code Discovery
- Before editing, identify the actual code responsible.
- Trace relevant callers, dependencies, contracts and tests.
- Do not assume file ownership from filenames alone.
- Verify findings against the current repository.

## Delegation
- Handle simple, well-understood changes directly.
- If code ownership is unclear, delegate read-only
  investigation to an Explore subagent.
- Delegate independent review for risky changes.
- Avoid unnecessary agents and repeated exploration.

## Implementation
- Prefer the smallest complete change.
- Preserve existing architecture and conventions.
- Run relevant tests and inspect the final diff.
- Do not generate specs for routine fixes.

## Memory
- Use Engram when available.
- Retrieve relevant decisions before complex changes.
- Save verified, reusable discoveries.
- Treat code as authoritative over old memories.
