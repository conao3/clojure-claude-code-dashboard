.PHONY: all
all:

.PHONY: server
server:
	pnpm exec shadow-cljs server

.PHONY: watch
watch:
	pnpm exec shadow-cljs watch :frontend :backend :test-frontend :test-backend

.PHONY: repl
repl:
	clj -M:dev:repl

.PHONY: update
update:
	clojure -Sdeps '{:deps {com.github.liquidz/antq {:mvn/version "RELEASE"}}}' -M -m antq.core --upgrade --force

.PHONY: generate-spectrum-colors
generate-spectrum-colors:
	pnpm exec node tools/generate-spectrum-colors/index.mjs

.PHONY: build-css
build-css:
	${MAKE} generate-spectrum-colors
	pnpm exec postcss resources/public/css/main.css -o resources-dev/public/dist/css/main.css

.PHONY: watch-css
watch-css:
	pnpm exec postcss resources/public/css/main.css -o resources-dev/public/dist/css/main.css --watch

.PHONY: storybook
storybook:
	pnpm exec storybook dev -p 6006

.PHONY: build-storybook
build-storybook:
	pnpm exec storybook build

.PHONY: watch-storybook
watch-storybook:
	pnpm exec shadow-cljs watch :storybook

.PHONY: compile-storybook
compile-storybook:
	pnpm exec shadow-cljs compile :storybook

.PHONY: release-frontend
release-frontend:
	pnpm exec shadow-cljs release frontend

.PHONY: release-backend
release-backend:
	pnpm exec shadow-cljs release backend

.PHONY: release
release:
	${MAKE} release-frontend
	${MAKE} release-backend

.PHONY: test-frontend
test-frontend:
	pnpm exec shadow-cljs compile test-frontend

.PHONY: test-backend
test-backend:
	pnpm exec shadow-cljs compile test-backend

.PHONY: test
test:
	${MAKE} test-frontend
	${MAKE} test-backend

.PHONY: run-backend
run-backend:
	pnpm exec node resources-dev/backend/main.js

.PHONY: validate-graphql
validate-graphql:
	pnpm exec node tools/validate-graphql/validate.mjs

### CI targets

.PHONY: ci-backend
ci-backend:
	clojure -P

.PHONY: ci-frontend
ci-frontend:
	pnpm install --frozen-lockfile

.PHONY: ci-integration
ci-integration:
	${MAKE} ci-frontend
	${MAKE} validate-graphql

.PHONY: clean
clean:
	rm -rf target .shadow-cljs .cpcache resources-dev/public/dist resources-dev/backend
