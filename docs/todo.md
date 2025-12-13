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
| 390 | `file-history-snapshot` | [x] |
| 58 | `queue-operation` | [x] |
| 17 | `system` | [x] |
| 5 | `summary` | [x] |

---

## UI Improvements

### High Priority

#### Session List Enhancement
- [ ] Add first message preview (first 30 characters)
- [ ] Add message count badge
- [ ] Add tool usage count display

#### Search Functionality
- [ ] Implement project name filtering
- [ ] Implement message search within sessions
- [ ] Add search result highlighting

#### Thinking Block
- [ ] Default to collapsed state
- [ ] Show summary only, expand on click

#### Assistant Message Visibility
- [ ] Add light background color to distinguish from page background
- [ ] Add subtle border for visual separation

### Medium Priority

#### Keyboard Shortcuts
- [ ] `j`/`k` for session navigation
- [ ] `/` for search focus
- [ ] `Escape` for deselection

#### Tool Result Display
- [ ] Option to hide already-displayed tool results
- [ ] Group consecutive tool_result messages

#### Date Separators
- [ ] Add date separators between messages
- [ ] Group messages by day

#### Real-time Updates
- [ ] Add manual refresh button
- [ ] Implement auto-update (polling or WebSocket)
- [ ] New message notification

### Low Priority

#### Session Customization
- [ ] Allow custom session names
- [ ] Show full UUID on hover

#### Code Display
- [ ] JSON/YAML toggle option
- [ ] Syntax highlighting
- [ ] Collapsible long JSON blocks

#### File History
- [ ] Move FileHistorySnapshot to separate panel/tab
- [ ] Display as file change history

### Accessibility

#### ARIA Labels
- [ ] Add `aria-label` to settings icon
- [ ] Add appropriate landmarks to messages

#### Contrast
- [ ] Ensure WCAG AA level (4.5:1) contrast ratio
- [ ] Review text with opacity-50/60

#### Focus Indicators
- [ ] Review `outline-none` usage
- [ ] Add visible focus indicators for keyboard navigation

#### Screen Reader Support
- [ ] Add `role="log"` and `aria-live="polite"` to message list
- [ ] Add `role="article"` to each message
- [ ] Add appropriate `aria-label` to tool results
