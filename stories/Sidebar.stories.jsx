import React from "react";
import { Sidebar } from "../resources-storybook/js/stories.js";

export default {
  title: "Layout/Sidebar",
  component: Sidebar,
  parameters: {
    layout: "fullscreen",
  },
  argTypes: {
    collapsed: { control: "boolean" },
  },
  decorators: [
    (Story) => (
      <div style={{ height: "600px", display: "flex" }}>
        <Story />
      </div>
    ),
  ],
};

const sampleProjects = [
  {
    id: "1",
    name: "claude-code-dashboard",
    sessions: [{ id: "s1" }, { id: "s2" }, { id: "s3" }],
  },
  {
    id: "2",
    name: "my-awesome-project",
    sessions: [{ id: "s4" }, { id: "s5" }],
  },
  {
    id: "3",
    name: "empty-project",
    sessions: [],
  },
  {
    id: "4",
    name: "another-project",
    sessions: [{ id: "s6" }],
  },
];

export const Default = {
  render: (args) => <Sidebar {...args} />,
  args: {
    projects: sampleProjects,
    selectedProject: null,
    collapsed: false,
    onSelectProject: (project) => console.log("Selected:", project),
  },
};

export const WithSelectedProject = {
  render: (args) => <Sidebar {...args} />,
  args: {
    projects: sampleProjects,
    selectedProject: sampleProjects[0],
    collapsed: false,
    onSelectProject: (project) => console.log("Selected:", project),
  },
};

export const Collapsed = {
  render: (args) => <Sidebar {...args} />,
  args: {
    projects: sampleProjects,
    selectedProject: sampleProjects[1],
    collapsed: true,
    onSelectProject: (project) => console.log("Selected:", project),
  },
};

export const Empty = {
  render: (args) => <Sidebar {...args} />,
  args: {
    projects: [],
    selectedProject: null,
    collapsed: false,
    onSelectProject: (project) => console.log("Selected:", project),
  },
};

export const ManyProjects = {
  render: (args) => <Sidebar {...args} />,
  args: {
    projects: [
      ...sampleProjects,
      { id: "5", name: "project-five", sessions: [{ id: "s7" }] },
      { id: "6", name: "project-six", sessions: [{ id: "s8" }, { id: "s9" }] },
      { id: "7", name: "project-seven", sessions: [{ id: "s10" }] },
      { id: "8", name: "project-eight", sessions: [] },
      { id: "9", name: "project-nine", sessions: [{ id: "s11" }] },
      { id: "10", name: "project-ten", sessions: [{ id: "s12" }, { id: "s13" }] },
    ],
    selectedProject: null,
    collapsed: false,
    onSelectProject: (project) => console.log("Selected:", project),
  },
};
