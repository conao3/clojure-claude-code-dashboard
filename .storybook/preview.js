import "../resources-dev/public/dist/css/main.css";

export const parameters = {
  backgrounds: {
    default: "dark",
    values: [
      { name: "dark", value: "#1a1a2e" },
      { name: "light", value: "#f5f5f5" },
    ],
  },
  controls: {
    matchers: {
      color: /(background|color)$/i,
      date: /Date$/i,
    },
  },
};
