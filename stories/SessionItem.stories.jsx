import React from "react";
import { SessionItem } from "../resources-storybook/js/stories.js";

export default {
  title: "Navigation/SessionItem",
  component: SessionItem,
  parameters: {
    layout: "centered",
  },
  argTypes: {
    active: { control: "boolean" },
  },
  decorators: [
    (Story) => (
      <div style={{ width: "280px", backgroundColor: "#1a1a2e" }}>
        <Story />
      </div>
    ),
  ],
};

export const Default = {
  render: (args) => <SessionItem {...args} />,
  args: {
    session: {
      id: "session-1",
      sessionId: "abc123def456",
      createdAt: "2024/12/10 14:30",
    },
    active: false,
    onPress: () => console.log("clicked"),
  },
};

export const Active = {
  render: (args) => <SessionItem {...args} />,
  args: {
    session: {
      id: "session-2",
      sessionId: "xyz789ghi012",
      createdAt: "2024/12/10 15:45",
    },
    active: true,
    onPress: () => console.log("clicked"),
  },
};

export const LongSessionId = {
  render: (args) => <SessionItem {...args} />,
  args: {
    session: {
      id: "session-3",
      sessionId: "very-long-session-id-that-should-be-truncated-properly",
      createdAt: "2024/12/09 09:15",
    },
    active: false,
    onPress: () => console.log("clicked"),
  },
};
