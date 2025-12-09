import React from "react";
import * as lucide from "lucide-react";
import { MessageBubble } from "../resources-storybook/js/stories.js";

export default {
  title: "Messages/MessageBubble",
  component: MessageBubble,
  parameters: {
    layout: "padded",
  },
  argTypes: {
    role: {
      control: "select",
      options: ["user", "assistant"],
    },
    thinking: { control: "boolean" },
  },
  decorators: [
    (Story) => (
      <div style={{ width: "600px", backgroundColor: "#1a1a2e", padding: "20px" }}>
        <Story />
      </div>
    ),
  ],
};

export const UserMessage = {
  render: (args) => (
    <MessageBubble {...args}>
      <p className="text-sm leading-relaxed text-neutral-content">
        こんにちは、コードのレビューをお願いします。
      </p>
    </MessageBubble>
  ),
  args: {
    role: "user",
    icon: lucide.User,
    iconClass: "text-accent-content",
    time: "14:30",
  },
};

export const AssistantMessage = {
  render: (args) => (
    <MessageBubble {...args}>
      <p className="text-sm leading-relaxed text-neutral-content">
        はい、コードを確認しますね。いくつかの改善点を見つけました。
      </p>
    </MessageBubble>
  ),
  args: {
    role: "assistant",
    icon: lucide.Cpu,
    iconClass: "text-purple-400",
    time: "14:31",
  },
};

export const AssistantWithTools = {
  render: (args) => (
    <MessageBubble {...args}>
      <p className="text-sm leading-relaxed text-neutral-content">
        ファイルを読み込んでいます...
      </p>
    </MessageBubble>
  ),
  args: {
    role: "assistant",
    icon: lucide.Cpu,
    iconClass: "text-purple-400",
    time: "14:32",
    toolCount: 3,
  },
};

export const AssistantWithThinking = {
  render: (args) => (
    <MessageBubble {...args}>
      <p className="text-sm leading-relaxed text-neutral-content">
        問題を分析しています...
      </p>
    </MessageBubble>
  ),
  args: {
    role: "assistant",
    icon: lucide.Cpu,
    iconClass: "text-purple-400",
    time: "14:33",
    thinking: true,
  },
};

export const AssistantWithToolsAndThinking = {
  render: (args) => (
    <MessageBubble {...args}>
      <p className="text-sm leading-relaxed text-neutral-content">
        複雑なタスクを実行中です...
      </p>
    </MessageBubble>
  ),
  args: {
    role: "assistant",
    icon: lucide.Cpu,
    iconClass: "text-purple-400",
    time: "14:34",
    toolCount: 5,
    thinking: true,
  },
};
