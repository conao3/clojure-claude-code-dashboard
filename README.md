# ccboard

A dashboard for viewing Claude Code sessions and messages, built with ClojureScript and Apollo GraphQL.

## Overview

ccboard (Claude Code Dashboard) provides a web interface for browsing and analyzing Claude Code sessions. The frontend is built with ClojureScript and Reagent, while the backend uses Express with Apollo Server to expose a GraphQL API.

## Features

- Browse Claude Code sessions and conversations
- View detailed message history with syntax highlighting
- Modern UI with Adobe Spectrum design tokens
- GraphQL API for flexible data querying

## Requirements

- Node.js 18.0.0 or higher
- pnpm (package manager)
- Java (for ClojureScript compilation)

## Installation

Install the package globally from npm:

```bash
npm install -g @conao3/ccboard
```

Or run directly with npx:

```bash
npx @conao3/ccboard
```

## Development

### Quick Start

Run the following commands in separate terminals:

```bash
make server          # Start shadow-cljs server
make watch           # Start watch builds
make watch-css       # Watch CSS changes
make run-backend     # Start backend server
```

Then open http://localhost:8000/ in your browser.

### Available Commands

| Command | Description |
|---------|-------------|
| `make server` | Start shadow-cljs server |
| `make watch` | Start watch builds for frontend, backend, and tests |
| `make watch-css` | Watch CSS changes |
| `make run-backend` | Start backend server |
| `make build-css` | Build CSS with PostCSS |
| `make repl` | Start Clojure REPL |
| `make test` | Run all tests |
| `make test-frontend` | Run frontend tests |
| `make test-backend` | Run backend tests |
| `make release` | Build release for frontend and backend |
| `make clean` | Clean build artifacts |
| `make update` | Update dependencies |

### Development URLs

| URL | Description |
|-----|-------------|
| http://localhost:8000/ | Frontend application |
| http://localhost:4000/admin/apollo | Apollo Sandbox |
| http://localhost:8000/admin/graphiql.html | GraphiQL IDE |
| http://localhost:9630/ | shadow-cljs UI |
| http://localhost:9500/ | Portfolio (component library) |
| http://localhost:9100/ | Test runner |

### Port Reference

| Port | Service |
|------|---------|
| 8000 | Frontend dev server (shadow-cljs) |
| 4000 | Backend API server (Express + Apollo) |
| 9630 | shadow-cljs UI |
| 9500 | Portfolio |
| 9100 | Test runner |

In development, requests to `/api/*` on port 8000 are proxied to port 4000.

## Styling

This project uses [Adobe Spectrum Design Tokens](https://github.com/adobe/spectrum-design-data) integrated with Tailwind CSS. The tokens provide a consistent color system and are automatically converted to CSS custom properties.

### Color System

Colors follow a semantic naming convention:

| Category | Purpose |
|----------|---------|
| `neutral` | Default UI elements |
| `accent` | Primary actions and links |
| `informative` | Information messages |
| `negative` | Errors and destructive actions |
| `positive` | Success messages |
| `notice` | Warnings |
| `disabled` | Disabled states |

Each category provides:

- `*-content` for text colors
- `*-background` for background colors
- `*-visual` for icons and indicators

### Usage Examples

```clojure
;; Text colors
:p.text-neutral-content          ; Default text
:p.text-accent-content           ; Accent text
:p.text-negative-content         ; Error text

;; Background colors
:div.bg-accent-background        ; Primary button
:div.bg-negative-background      ; Error state
:div.bg-positive-background      ; Success state

;; Background layers
:div.bg-background-base          ; Base background
:div.bg-background-layer-1       ; Elevated layer
:div.bg-background-elevated      ; Modal background
```

### Available Colors

Base palette colors (scales from 100-1600):

- gray, blue, red, orange, yellow, green
- cyan, indigo, purple, fuchsia, magenta, pink
- turquoise, seafoam, celery, chartreuse
- brown, cinnamon, silver

To regenerate color tokens:

```bash
make generate-spectrum-colors
```

For the complete color reference, see the [Spectrum Design Tokens Viewer](https://opensource.adobe.com/spectrum-design-data/s2-tokens-viewer).

## License

Apache-2.0
