import React from "react";
import { ContentBlock, ToolResultBlock } from "../resources-storybook/js/stories.js";

export default {
  title: "Messages/ContentBlock",
  component: ContentBlock,
  parameters: {
    layout: "padded",
  },
  decorators: [
    (Story) => (
      <div style={{ width: "600px", backgroundColor: "#1a1a2e", padding: "20px" }}>
        <Story />
      </div>
    ),
  ],
};

export const TextBlock = {
  render: (args) => <ContentBlock {...args} />,
  args: {
    block: {
      type: "text",
      text: "これはテキストブロックのサンプルです。コードの説明や応答内容がここに表示されます。",
    },
    toolResults: {},
  },
};

export const ThinkingBlock = {
  render: (args) => <ContentBlock {...args} />,
  args: {
    block: {
      type: "thinking",
      thinking: "ユーザーの質問を分析しています...\nコードの構造を理解する必要があります。\n最適なアプローチを検討中です。",
    },
    toolResults: {},
  },
};

export const ToolUseBlock = {
  render: (args) => <ContentBlock {...args} />,
  args: {
    block: {
      type: "tool_use",
      id: "tool-123",
      name: "Read",
      input: '{\n  "file_path": "/src/main.cljs",\n  "offset": 0,\n  "limit": 100\n}',
    },
    toolResults: {},
  },
};

export const ToolUseWithResult = {
  render: (args) => <ContentBlock {...args} />,
  args: {
    block: {
      type: "tool_use",
      id: "tool-456",
      name: "Bash",
      input: '{\n  "command": "git status"\n}',
    },
    toolResults: {
      "tool-456": {
        type: "tool_result",
        tool_use_id: "tool-456",
        content: "On branch main\nYour branch is up to date with 'origin/main'.\n\nnothing to commit, working tree clean",
      },
    },
  },
};

export const ToolResultBlockStory = {
  name: "ToolResultBlock",
  render: (args) => <ToolResultBlock {...args} />,
  args: {
    block: {
      type: "tool_result",
      tool_use_id: "tool-789",
      content: "File contents:\n(ns example.core)\n\n(defn hello [name]\n  (str \"Hello, \" name \"!\"))",
    },
  },
};

export const UnknownBlock = {
  render: (args) => <ContentBlock {...args} />,
  args: {
    block: {
      type: "unknown_type",
    },
    toolResults: {},
  },
};
