import React from "react";
import {
  SystemMessageItem,
  SummaryMessageItem,
  FileHistorySnapshotMessage,
  QueueOperationMessage,
  UnknownMessage,
  BrokenMessage,
} from "../resources-storybook/js/stories.js";

export default {
  title: "Messages/SystemMessages",
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

export const SystemMessage = {
  render: (args) => <SystemMessageItem {...args} />,
  args: {
    message: {
      subtype: "context_loaded",
      timestamp: "2024/12/10 14:30:00",
      systemContent: "コンテキストが正常に読み込まれました。",
      level: "info",
    },
  },
};

export const SummaryMessage = {
  render: (args) => <SummaryMessageItem {...args} />,
  args: {
    message: {
      summary: "このセッションでは、ユーザーがコードレビューを依頼し、いくつかの改善点を提案しました。主な変更点は関数の分割とエラーハンドリングの追加です。",
    },
  },
};

export const FileHistorySnapshot = {
  render: (args) => <FileHistorySnapshotMessage {...args} />,
  args: {
    message: {
      isSnapshotUpdate: false,
      trackedFileBackups: [
        "/src/main.cljs",
        "/src/components/button.cljs",
        "/test/main_test.cljs",
      ],
    },
  },
};

export const FileHistorySnapshotUpdate = {
  render: (args) => <FileHistorySnapshotMessage {...args} />,
  args: {
    message: {
      isSnapshotUpdate: true,
      trackedFileBackups: [
        "/src/main.cljs",
      ],
    },
  },
};

export const QueueOperation = {
  render: (args) => <QueueOperationMessage {...args} />,
  args: {
    message: {
      operation: "enqueue",
      content: "新しいタスクをキューに追加",
      queueSessionId: "queue-session-abc123",
      timestamp: "2024/12/10 14:35:00",
    },
  },
};

export const Unknown = {
  render: (args) => <UnknownMessage {...args} />,
  args: {
    message: {
      messageId: "unknown-msg-123",
    },
  },
};

export const Broken = {
  render: (args) => <BrokenMessage {...args} />,
  args: {
    message: {
      messageId: "broken-msg-456",
      rawMessage: '{"type": "invalid", "data": null, "error": "Parse failed"}',
    },
  },
};
