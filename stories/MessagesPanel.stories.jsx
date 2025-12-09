import React from "react";
import { MessagesPanel } from "../resources-storybook/js/stories.js";

export default {
  title: "Layout/MessagesPanel",
  component: MessagesPanel,
  parameters: {
    layout: "fullscreen",
  },
  decorators: [
    (Story) => (
      <div style={{ height: "600px", display: "flex", flex: 1 }}>
        <Story />
      </div>
    ),
  ],
};

export const Default = {
  render: (args) => <MessagesPanel {...args} />,
  args: {
    session: {
      id: "session-1",
      sessionId: "abc123def456",
      createdAt: "2024/12/10 14:30",
    },
  },
};

export const NoSession = {
  render: (args) => <MessagesPanel {...args} />,
  args: {
    session: null,
  },
};

export const LongSessionId = {
  render: (args) => <MessagesPanel {...args} />,
  args: {
    session: {
      id: "session-2",
      sessionId: "very-long-session-id-that-should-be-properly-displayed",
      createdAt: "2024/12/09 10:15",
    },
  },
};
