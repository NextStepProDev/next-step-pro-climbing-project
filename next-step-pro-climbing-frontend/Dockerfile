# ⚠️ 24, not the newer 26, and it is the tests that decide: node 26 ships its own global
# localStorage, which shadows the one jsdom provides, and 32 tests fail on it under vitest 4 /
# jsdom 30. Building on a version the suite cannot run on means shipping what nothing verified.
# 24 is the active LTS; revisit 26 once vitest and jsdom handle that global (26 becomes LTS on
# 2026-10-28). Keep this in step with .nvmrc, which CI reads.
FROM node:24-alpine AS build
WORKDIR /app
ARG APP_VERSION=dev
ENV VITE_APP_VERSION=$APP_VERSION

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
