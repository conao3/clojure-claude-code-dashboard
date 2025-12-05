# clojure-claude-code-dashboard

Claude Code Dashboard - ClojureScript frontend with Apollo GraphQL backend.

## Development

### Start shadow-cljs server

```bash
pnpm exec shadow-cljs server
```

### Start watch builds (in another terminal)

```bash
pnpm exec shadow-cljs watch :frontend :backend :test-frontend :test-backend
```

### Start backend server (in another terminal)

```bash
make run-backend
```

## Ports

| Port | Description |
|------|-------------|
| 8000 | Frontend dev server (shadow-cljs dev-http) |
| 4000 | Backend API server (Express + Apollo Server) |
| 9100 | Frontend test runner |
| 9630 | shadow-cljs UI |

## Endpoints

- http://localhost:8000/ - Frontend application
- http://localhost:8000/admin/graphiql.html - GraphiQL IDE (dev only)
- http://localhost:8000/admin/apollo - Apollo Sandbox (dev only)
- http://localhost:8000/api/graphql - GraphQL API endpoint (proxied)
- http://localhost:9100/ - Frontend test runner
- http://localhost:9630/ - shadow-cljs UI

## Proxy

In development, requests to `http://localhost:8000/api/*` are proxied to `http://localhost:4000/api/*`.
