# ccboard

ccboard (Claude Code Dashboard) - ClojureScript frontend with Apollo GraphQL backend.

## Development

### Quick Start

```bash
make server          # Terminal 1: Start shadow-cljs server
make watch           # Terminal 2: Start watch builds
make watch-css       # Terminal 3: Watch CSS changes
make run-backend     # Terminal 4: Start backend server
```

Then open http://localhost:8000/

## Make Targets

| Target | Description |
|--------|-------------|
| `make server` | Start shadow-cljs server |
| `make watch` | Start watch builds for frontend, backend, and tests |
| `make run-backend` | Start backend server |
| `make build-css` | Build CSS (generates spectrum colors and runs PostCSS) |
| `make watch-css` | Watch CSS changes |
| `make generate-spectrum-colors` | Generate `resources/public/css/spectrum-colors.css` from Spectrum Design Tokens |
| `make repl` | Start Clojure REPL |
| `make test` | Run all tests |
| `make test-frontend` | Run frontend tests |
| `make test-backend` | Run backend tests |
| `make release` | Build release for frontend and backend |
| `make release-frontend` | Build release for frontend |
| `make release-backend` | Build release for backend |
| `make update` | Update dependencies |
| `make clean` | Clean build artifacts |

## Ports

| Port | Description |
|------|-------------|
| 8000 | Frontend dev server (shadow-cljs dev-http) |
| 4000 | Backend API server (Express + Apollo Server) |
| 9100 | Frontend test runner |
| 9630 | shadow-cljs UI |
| 9500 | Portfolio |

## Endpoints

- http://localhost:8000/ - Frontend application
- http://localhost:8000/admin/graphiql.html - GraphiQL IDE (dev only)
- http://localhost:8000/admin/apollo - Apollo Sandbox (dev only)
- http://localhost:8000/api/graphql - GraphQL API endpoint (proxied)
- http://localhost:9100/ - Frontend test runner
- http://localhost:9630/ - shadow-cljs UI
- http://localhost:9500/ - Portfolio

## Proxy

In development, requests to `http://localhost:8000/api/*` are proxied to `http://localhost:4000/api/*`.

## Styling with Spectrum Design Tokens

This project uses [Adobe Spectrum Design Tokens](https://github.com/adobe/spectrum-design-data) integrated with Tailwind CSS. The tokens are generated from `@adobe/spectrum-tokens` package and converted to Tailwind-compatible CSS custom properties.

### Color Sources

Colors are generated from three token files:

| File | Description |
|------|-------------|
| `color-palette.json` | Base colors (gray, blue, red, etc.) |
| `semantic-color-palette.json` | Semantic colors (accent, informative, negative, etc.) |
| `color-aliases.json` | Contextual aliases (background, content, border colors) |

### Color Structure

Colors are organized in a semantic system with the following categories:

| Category | Purpose | Base Color |
|----------|---------|------------|
| `neutral` | Default UI elements | gray |
| `neutral-subdued` | Secondary/muted elements | gray (lighter) |
| `accent` | Primary actions, links | blue |
| `informative` | Info messages | blue |
| `negative` | Errors, destructive actions | red |
| `positive` | Success messages | green |
| `notice` | Warnings | orange |
| `disabled` | Disabled elements | gray (very light) |

Each category has the following color types:

| Type | Usage | Example |
|------|-------|---------|
| `*-content` | Text color | `text-neutral-content` |
| `*-background` | Background color | `bg-accent-background` |
| `*-visual` | Icons, indicators | `text-accent-visual` |
| `*-border` | Border color (negative only) | `border-negative-border` |

### Usage Examples

#### Text Colors (content)

```clojure
:p.text-neutral-content          ; Default text (gray-800)
:p.text-neutral-subdued-content  ; Secondary text (gray-700)
:p.text-accent-content           ; Accent/link text (blue-900)
:p.text-negative-content         ; Error text (red-900)
:p.text-disabled-content         ; Disabled text (gray-400)
```

#### Background Colors (background)

```clojure
:div.bg-neutral-background       ; Neutral button (gray-800)
:div.bg-neutral-subdued-background ; Secondary button (gray-500)
:div.bg-accent-background        ; Primary button (blue-800)
:div.bg-informative-background   ; Info badge (blue-800)
:div.bg-negative-background      ; Error/delete button (red-800)
:div.bg-positive-background      ; Success badge (green-800)
:div.bg-notice-background        ; Warning badge (orange-900)
:div.bg-disabled-background      ; Disabled element (gray-100)
```

#### Icon Colors (visual)

```clojure
:span.text-neutral-visual        ; Default icon (gray-600)
:span.text-accent-visual         ; Accent icon (blue-900)
:span.text-informative-visual    ; Info icon (blue-900)
:span.text-negative-visual       ; Error icon (red-900)
:span.text-positive-visual       ; Success icon (green-900)
:span.text-notice-visual         ; Warning icon (orange-900)
```

#### Page Background Layers

```clojure
:div.bg-background-base          ; Base background (gray-25)
:div.bg-background-layer-1       ; Layer 1 (gray-50)
:div.bg-background-layer-2       ; Layer 2 (gray-75)
:div.bg-background-elevated      ; Elevated/modal (gray-75)
```

#### Semantic Color Scale (100-1600)

Each semantic color has a full scale for advanced usage:

```clojure
:div.bg-accent-900        ; Accent (blue)
:div.bg-informative-900   ; Informative (blue)
:div.bg-negative-900      ; Negative/Error (red)
:div.bg-positive-900      ; Positive/Success (green)
:div.bg-notice-900        ; Notice/Warning (orange)
```

#### Base Color Scale

Available base colors (each with scale 100-1600, gray has 25-1000):

- `gray`, `blue`, `red`, `orange`, `yellow`
- `green`, `cyan`, `indigo`, `purple`, `fuchsia`
- `magenta`, `pink`, `turquoise`, `seafoam`, `celery`
- `chartreuse`, `brown`, `cinnamon`, `silver`

```clojure
:div.bg-gray-500   ; Gray background
:div.text-gray-800 ; Gray text
:div.bg-blue-900   ; Blue background
:div.bg-red-900    ; Red background
```

### Regenerating Colors

To regenerate `resources/public/css/spectrum-colors.css`:

```bash
make generate-spectrum-colors
```

The theme is set to "dark" by default. To change it, edit `THEME` in `tools/generate-spectrum-colors/index.mjs`.

### Color Reference

You can browse all available colors at the [Spectrum Design Tokens Viewer](https://opensource.adobe.com/spectrum-design-data/s2-tokens-viewer).
