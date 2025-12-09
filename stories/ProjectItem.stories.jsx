import React from "react";
import { ProjectItem } from "../resources-storybook/js/stories.js";

export default {
  title: "Navigation/ProjectItem",
  component: ProjectItem,
  parameters: {
    layout: "centered",
  },
  argTypes: {
    active: { control: "boolean" },
    collapsed: { control: "boolean" },
  },
};

export const Default = {
  render: (args) => <ProjectItem {...args} />,
  args: {
    project: {
      id: "1",
      name: "claude-code-dashboard",
      sessions: [{ id: "s1" }, { id: "s2" }, { id: "s3" }],
    },
    active: false,
    collapsed: false,
    onPress: () => console.log("clicked"),
  },
};

export const Active = {
  render: (args) => <ProjectItem {...args} />,
  args: {
    project: {
      id: "1",
      name: "claude-code-dashboard",
      sessions: [{ id: "s1" }, { id: "s2" }],
    },
    active: true,
    collapsed: false,
    onPress: () => console.log("clicked"),
  },
};

export const NoSessions = {
  render: (args) => <ProjectItem {...args} />,
  args: {
    project: {
      id: "2",
      name: "empty-project",
      sessions: [],
    },
    active: false,
    collapsed: false,
    onPress: () => console.log("clicked"),
  },
};

export const Collapsed = {
  render: (args) => <ProjectItem {...args} />,
  args: {
    project: {
      id: "1",
      name: "claude-code-dashboard",
      sessions: [{ id: "s1" }],
    },
    active: false,
    collapsed: true,
    onPress: () => console.log("clicked"),
  },
};

export const LongName = {
  render: (args) => <ProjectItem {...args} />,
  args: {
    project: {
      id: "3",
      name: "very-long-project-name-that-should-be-truncated",
      sessions: [{ id: "s1" }, { id: "s2" }, { id: "s3" }, { id: "s4" }, { id: "s5" }],
    },
    active: false,
    collapsed: false,
    onPress: () => console.log("clicked"),
  },
};
