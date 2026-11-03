# syntax=docker/dockerfile:1

# CloudForge frontend: production image.
#
#   docker build -t cloudforge-frontend:<version> frontend
#   docker build --target test frontend          # lint + unit tests in CI
#   docker run -p 8080:8080 -e CLOUDFORGE_BACKEND_URL=http://cloudforge-backend:8080 cloudforge-frontend:<version>
#
# Stages: deps (npm ci, cached) → build (typecheck + Vite) → test (optional) → runtime (NGINX, static files only).
# Base images are pinned by version AND digest for reproducible builds; update both together.

ARG NODE_IMAGE=node:22.23.4-alpine3.24@sha256:fe4f6d74e20370acb04ad889077d14d0f64a9c8ea6a42bc60cddf1e844699914
ARG NGINX_IMAGE=nginxinc/nginx-unprivileged:1.31.7-alpine3.24@sha256:b5047b9b76845eac124358373c6852377c52e4b05834a47647e955dd91cb8f1e

# ---- dependencies --------------------------------------------------------------------------------
FROM ${NODE_IMAGE} AS deps
WORKDIR /app
ENV CI=true
# Manifests first: this layer is reused until package.json or the lockfile changes.
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm \
    npm ci --no-audit --no-fund

# ---- build ---------------------------------------------------------------------------------------
FROM deps AS build
# Public, compiled into the bundle at build time (Vite inlines VITE_* variables). Never a secret.
ARG VITE_API_BASE_URL=/api
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
COPY . .
# `npm run build` = strict typecheck + production build. Source maps are produced for error
# tracking uploads but not shipped to browsers.
RUN npm run build \
 && find dist -name '*.map' -delete

# ---- test (optional target, not part of the runtime image) ------------------------------------
FROM build AS test
RUN npm run lint && npm run test:run

# ---- runtime -------------------------------------------------------------------------------------
FROM ${NGINX_IMAGE} AS runtime

ARG VERSION=dev
ARG REVISION=unknown
LABEL org.opencontainers.image.title="cloudforge-frontend" \
      org.opencontainers.image.description="CloudForge web console (React SPA served by NGINX)" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.revision="${REVISION}"

# Config and site files belong to root and are read-only for the nginx user (uid 101) that runs
# the server: a compromised worker can't rewrite the app it serves.
COPY docker/nginx.conf /etc/nginx/nginx.conf
COPY docker/security-headers.conf /etc/nginx/snippets/security-headers.conf
COPY docker/default.conf.template /etc/nginx/templates/nginx/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html

# The entrypoint renders templates/nginx/*.template into /tmp/nginx/ at start-up, so /tmp is the only
# writable path (read-only root filesystem friendly). Only CLOUDFORGE_* and the detected
# nameservers are substituted; NGINX's own $variables in the template are left alone.
ENV CLOUDFORGE_BACKEND_URL=http://cloudforge-backend:8080 \
    NGINX_ENTRYPOINT_LOCAL_RESOLVERS=1 \
    NGINX_ENVSUBST_OUTPUT_DIR=/tmp \
    NGINX_ENVSUBST_FILTER="^(CLOUDFORGE_|NGINX_LOCAL_RESOLVERS$)"

USER 101
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1
