import React from "react";
import { AssistantMessage, UserMessage } from "../resources-storybook/js/stories.js";

export default {
  title: "Messages/FullMessages",
  parameters: {
    layout: "padded",
  },
  decorators: [
    (Story) => (
      <div style={{ width: "700px", backgroundColor: "#1a1a2e", padding: "20px" }}>
        <Story />
      </div>
    ),
  ],
};

export const AssistantTextOnly = {
  render: (args) => <AssistantMessage {...args} />,
  args: {
    message: {
      message: {
        content: [
          {
            type: "text",
            text: "はい、コードを確認しました。いくつかの改善点があります。\n\n1. 関数名をより明確にすることをお勧めします\n2. エラーハンドリングを追加する必要があります\n3. テストカバレッジを増やすことを検討してください",
          },
        ],
      },
    },
    toolResults: {},
  },
};

export const AssistantWithThinking = {
  render: (args) => <AssistantMessage {...args} />,
  args: {
    message: {
      message: {
        content: [
          {
            type: "thinking",
            thinking: "ユーザーのコードを分析しています...\n\n- main関数の構造を確認\n- 依存関係を調査\n- 最適なリファクタリング方法を検討",
          },
          {
            type: "text",
            text: "コードを分析した結果、以下の改善点を提案します。",
          },
        ],
      },
    },
    toolResults: {},
  },
};

export const AssistantWithToolUse = {
  render: (args) => <AssistantMessage {...args} />,
  args: {
    message: {
      message: {
        content: [
          {
            type: "text",
            text: "ファイルの内容を確認します。",
          },
          {
            type: "tool_use",
            id: "tool-read-1",
            name: "Read",
            input: '{\n  "file_path": "/src/main.cljs"\n}',
          },
        ],
      },
    },
    toolResults: {
      "tool-read-1": {
        type: "tool_result",
        tool_use_id: "tool-read-1",
        content: "(ns example.main)\n\n(defn -main [& args]\n  (println \"Hello, World!\"))",
      },
    },
  },
};

export const AssistantMultipleTools = {
  render: (args) => <AssistantMessage {...args} />,
  args: {
    message: {
      message: {
        content: [
          {
            type: "thinking",
            thinking: "複数のファイルを確認する必要があります。",
          },
          {
            type: "text",
            text: "プロジェクトの構造を確認しています。",
          },
          {
            type: "tool_use",
            id: "tool-glob-1",
            name: "Glob",
            input: '{\n  "pattern": "src/**/*.cljs"\n}',
          },
          {
            type: "tool_use",
            id: "tool-bash-1",
            name: "Bash",
            input: '{\n  "command": "git status"\n}',
          },
        ],
      },
    },
    toolResults: {
      "tool-glob-1": {
        type: "tool_result",
        tool_use_id: "tool-glob-1",
        content: "src/main.cljs\nsrc/components/button.cljs\nsrc/utils/helpers.cljs",
      },
      "tool-bash-1": {
        type: "tool_result",
        tool_use_id: "tool-bash-1",
        content: "On branch main\nChanges to be committed:\n  modified: src/main.cljs",
      },
    },
  },
};

export const UserTextOnly = {
  render: (args) => <UserMessage {...args} />,
  args: {
    message: {
      message: {
        content: [
          {
            type: "text",
            text: "このコードをレビューしてください。特にパフォーマンスの問題がないか確認してほしいです。",
          },
        ],
      },
    },
    displayedToolUseIds: new Set(),
  },
};

export const UserWithToolResult = {
  render: (args) => <UserMessage {...args} />,
  args: {
    message: {
      message: {
        content: [
          {
            type: "tool_result",
            tool_use_id: "tool-edit-1",
            content: "File edited successfully.",
          },
        ],
      },
    },
    displayedToolUseIds: new Set(),
  },
};

export const UserWithToolResultDisplayed = {
  render: (args) => <UserMessage {...args} />,
  args: {
    message: {
      message: {
        content: [
          {
            type: "tool_result",
            tool_use_id: "tool-edit-1",
            content: "File edited successfully.",
          },
        ],
      },
    },
    displayedToolUseIds: new Set(["tool-edit-1"]),
  },
};
