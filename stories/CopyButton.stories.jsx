import React from "react";
import { CopyButton } from "../resources-storybook/js/stories.js";

export default {
  title: "Components/CopyButton",
  component: CopyButton,
  parameters: {
    layout: "centered",
  },
  decorators: [
    (Story) => (
      <div className="group" style={{ backgroundColor: "#1a1a2e", padding: "40px" }}>
        <Story />
      </div>
    ),
  ],
};

export const Default = {
  render: (args) => <CopyButton {...args} />,
  args: {
    text: "Sample text to copy",
    label: "Copy",
  },
};

export const CustomLabel = {
  render: (args) => <CopyButton {...args} />,
  args: {
    text: '{"key": "value"}',
    label: "Copy JSON",
  },
};

export const RawLabel = {
  render: (args) => <CopyButton {...args} />,
  args: {
    text: '{"rawMessage": "test"}',
    label: "Raw",
  },
};
