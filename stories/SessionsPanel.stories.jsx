import React from "react";
import { SessionsPanel } from "../resources-storybook/js/stories.js";

export default {
  title: "Layout/SessionsPanel",
  component: SessionsPanel,
  parameters: {
    layout: "fullscreen",
  },
  decorators: [
    (Story) => (
      <div style={{ height: "600px", display: "flex" }}>
        <Story />
      </div>
    ),
  ],
};

const sampleSessions = [
  {
    id: "session-1",
    sessionId: "abc123def456",
    createdAt: "2024/12/10 14:30",
  },
  {
    id: "session-2",
    sessionId: "xyz789ghi012",
    createdAt: "2024/12/10 13:15",
  },
  {
    id: "session-3",
    sessionId: "jkl345mno678",
    createdAt: "2024/12/09 16:45",
  },
  {
    id: "session-4",
    sessionId: "pqr901stu234",
    createdAt: "2024/12/09 10:00",
  },
];

export const Default = {
  render: (args) => <SessionsPanel {...args} />,
  args: {
    project: { id: "1", name: "claude-code-dashboard" },
    sessions: sampleSessions,
    selectedSession: null,
    onSelectSession: (session) => console.log("Selected:", session),
  },
};

export const WithSelectedSession = {
  render: (args) => <SessionsPanel {...args} />,
  args: {
    project: { id: "1", name: "claude-code-dashboard" },
    sessions: sampleSessions,
    selectedSession: sampleSessions[1],
    onSelectSession: (session) => console.log("Selected:", session),
  },
};

export const NoProject = {
  render: (args) => <SessionsPanel {...args} />,
  args: {
    project: null,
    sessions: [],
    selectedSession: null,
    onSelectSession: (session) => console.log("Selected:", session),
  },
};

export const EmptySessions = {
  render: (args) => <SessionsPanel {...args} />,
  args: {
    project: { id: "2", name: "empty-project" },
    sessions: [],
    selectedSession: null,
    onSelectSession: (session) => console.log("Selected:", session),
  },
};

export const ManySessions = {
  render: (args) => <SessionsPanel {...args} />,
  args: {
    project: { id: "1", name: "active-project" },
    sessions: [
      ...sampleSessions,
      { id: "session-5", sessionId: "vwx567yza890", createdAt: "2024/12/08 14:20" },
      { id: "session-6", sessionId: "bcd123efg456", createdAt: "2024/12/08 11:30" },
      { id: "session-7", sessionId: "hij789klm012", createdAt: "2024/12/07 09:45" },
      { id: "session-8", sessionId: "nop345qrs678", createdAt: "2024/12/06 15:00" },
      { id: "session-9", sessionId: "tuv901wxy234", createdAt: "2024/12/05 12:15" },
      { id: "session-10", sessionId: "zab567cde890", createdAt: "2024/12/04 08:30" },
    ],
    selectedSession: null,
    onSelectSession: (session) => console.log("Selected:", session),
  },
};

export const LongProjectName = {
  render: (args) => <SessionsPanel {...args} />,
  args: {
    project: { id: "3", name: "very-long-project-name-that-should-be-truncated" },
    sessions: sampleSessions.slice(0, 2),
    selectedSession: null,
    onSelectSession: (session) => console.log("Selected:", session),
  },
};
