# TODO

## Message Types

List of message types found in Claude Code session files (JSONL).
Implement UI components for each type to display them appropriately.

### Aggregation Method

Extract `type` field from all JSONL files under `~/.claude/projects/` (excluding `agent-*`) and count occurrences.

```bash
find ~/.claude/projects -name "*.jsonl" -not -name "agent-*" -exec cat {} \; | \
    jq -r '.type // empty' | sort | uniq -c | sort -rn
```

### Column Description

| Column | Description |
|--------|-------------|
| Count | Number of occurrences (higher count = higher priority) |
| Type | Value of `type` field in JSONL |
| Status | Implementation status (`[ ]` = not implemented, `[x]` = implemented) |

### TODO

| Count | Type | Status |
|------:|------|--------|
| 4745 | `assistant` | [x] |
| 2507 | `user` | [x] |
| 390 | `file-history-snapshot` | [ ] |
| 58 | `queue-operation` | [ ] |
| 17 | `system` | [ ] |
| 5 | `summary` | [ ] |
