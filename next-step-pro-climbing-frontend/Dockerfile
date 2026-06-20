FROM node:24-alpine AS build
WORKDIR /app
ARG APP_VERSION=dev
ENV VITE_APP_VERSION=$APP_VERSION

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# Compile the Brotli dynamic modules against the exact nginx version of the runtime
# image. Alpine's prebuilt nginx-mod-http-brotli targets Alpine's own nginx package and
# is NOT binary-compatible with the official nginx:alpine build, so we build from source
# with --with-compat. Both stages use the same nginx:alpine tag -> versions always match.
FROM nginx:alpine AS brotli-build
RUN apk add --no-cache build-base git wget pcre-dev zlib-dev linux-headers brotli-dev
RUN set -eux; \
    NGINX_VERSION="$(nginx -v 2>&1 | grep -oE '[0-9]+\.[0-9]+\.[0-9]+')"; \
    wget -qO /tmp/nginx.tar.gz "https://nginx.org/download/nginx-${NGINX_VERSION}.tar.gz"; \
    tar -C /tmp -xzf /tmp/nginx.tar.gz; \
    git clone --depth=1 --recurse-submodules https://github.com/google/ngx_brotli.git /tmp/ngx_brotli; \
    cd "/tmp/nginx-${NGINX_VERSION}"; \
    ./configure --with-compat --add-dynamic-module=/tmp/ngx_brotli; \
    make -j"$(nproc)" modules; \
    mkdir -p /modules; cp objs/ngx_http_brotli_*.so /modules/

FROM nginx:alpine
# brotli-libs = shared runtime lib the compiled modules link against
RUN apk add --no-cache brotli-libs
COPY --from=brotli-build /modules/ /usr/lib/nginx/modules/
# load_module must live in the main context -> prepend to the root nginx.conf
RUN sed -i '1i load_module /usr/lib/nginx/modules/ngx_http_brotli_filter_module.so;\nload_module /usr/lib/nginx/modules/ngx_http_brotli_static_module.so;' /etc/nginx/nginx.conf

COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
