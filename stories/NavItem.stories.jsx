import React from "react";
import * as lucide from "lucide-react";
import { NavItem } from "../resources-storybook/js/stories.js";

export default {
  title: "Navigation/NavItem",
  component: NavItem,
  parameters: {
    layout: "centered",
  },
  argTypes: {
    active: { control: "boolean" },
    collapsed: { control: "boolean" },
    badge: { control: "text" },
  },
};

export const Default = {
  render: (args) => <NavItem {...args} />,
  args: {
    icon: lucide.Home,
    label: "Dashboard",
    active: false,
    collapsed: false,
    onPress: () => console.log("clicked"),
  },
};

export const Active = {
  render: (args) => <NavItem {...args} />,
  args: {
    icon: lucide.Folder,
    label: "Projects",
    active: true,
    collapsed: false,
    onPress: () => console.log("clicked"),
  },
};

export const WithBadge = {
  render: (args) => <NavItem {...args} />,
  args: {
    icon: lucide.Folder,
    label: "Projects",
    active: true,
    collapsed: false,
    badge: "12",
    onPress: () => console.log("clicked"),
  },
};

export const Collapsed = {
  render: (args) => <NavItem {...args} />,
  args: {
    icon: lucide.Settings,
    label: "Settings",
    active: false,
    collapsed: true,
    onPress: () => console.log("clicked"),
  },
};

export const CollapsedActive = {
  render: (args) => <NavItem {...args} />,
  args: {
    icon: lucide.Folder,
    label: "Projects",
    active: true,
    collapsed: true,
    onPress: () => console.log("clicked"),
  },
};
