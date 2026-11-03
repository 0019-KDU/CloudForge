# Frontend setup

## Requirements

- Node.js 22 (≥ 22.12) and npm 10
- A running CloudForge backend for anything beyond the unit tests (see the root README)

## Run locally

```bash
cd backend      # terminal 1: API on http://localhost:8080
SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run     # or run TestCloudForgeApplication (throwaway DB)

cd frontend     # terminal 2: SPA on http://localhost:5173
npm install
npm run dev
```

Open http://localhost:5173 in Chrome, Edge or Firefox. The dev server proxies `/api` to the backend,
so the SPA and the API share one origin. This matters for two reasons:

- the backend has no CORS configuration, and
- the refresh token cookie is `Secure; SameSite=Strict; Path=/api/auth`. Browsers accept `Secure`
  cookies on `http://localhost`, but not on other plain-HTTP hosts (a LAN IP, a custom hostname), so
  sessions won't survive a reload there.

First sign-in: start the backend once with `CLOUDFORGE_BOOTSTRAP_ADMIN_EMAIL` / `_PASSWORD` to create a
platform administrator, then invite other users from the admin pages.

## Configuration

Copy `.env.example` to `.env.local` if you need to change anything.

| Variable | Exposed to browser | Default | Purpose |
|---|---|---|---|
| `VITE_API_BASE_URL` | **yes** | `/api` | API base path. Keep it relative (same origin) |
| `CLOUDFORGE_BACKEND_URL` | no (dev server only) | `http://localhost:8080` | Proxy target for `/api` |

Everything prefixed `VITE_` is compiled into the bundle and public. There are no secrets in the frontend.

## Scripts

| Command | What it does |
|---|---|
| `npm run dev` | Dev server with HMR and the `/api` proxy |
| `npm run typecheck` | `tsc -b` (strict) |
| `npm run lint` | ESLint (`typescript-eslint` strict, type-checked; React hooks rules) |
| `npm test` / `npm run test:run` | Vitest in watch mode / once |
| `npm run build` | Typecheck and production build into `dist/` |
| `npm run check` | All of the above, as run at the end of every phase |

## Production image

`frontend/Dockerfile` builds a static NGINX image (no Node.js at runtime).

```bash
docker build -t cloudforge-frontend:1.0.0 \
  --build-arg VERSION=1.0.0 --build-arg REVISION=$(git rev-parse --short HEAD) frontend
docker build --target test frontend            # lint + unit tests, for CI

docker run -d --name cloudforge-frontend -p 8080:8080 \
  -e CLOUDFORGE_BACKEND_URL=http://cloudforge-backend:8080 \
  --read-only --tmpfs /tmp \
  --cap-drop ALL --security-opt no-new-privileges \
  cloudforge-frontend:1.0.0
```

| Setting | Kind | Default | Purpose |
|---|---|---|---|
| `VITE_API_BASE_URL` | build arg (public, baked into the bundle) | `/api` | API base path |
| `VERSION`, `REVISION` | build args | `dev`, `unknown` | OCI image labels |
| `CLOUDFORGE_BACKEND_URL` | runtime env | `http://cloudforge-backend:8080` | Where NGINX forwards `/api/` (scheme://host:port, no path). In Kubernetes use the fully qualified service name (`http://cloudforge-backend.<namespace>.svc.cluster.local:8080`): NGINX resolves it per request and ignores DNS search domains |

What the image does:

- **Multi-stage:** `deps` (cached `npm ci`) → `build` (typecheck + Vite) → optional `test` → `runtime`. The runtime image contains only NGINX and the static files.
- **Pinned bases:** `node:22.23.4-alpine3.24` and `nginxinc/nginx-unprivileged:1.31.7-alpine3.24`, each by version and digest. Update both together.
- **Non-root:** runs as uid 101 on port 8080. Site and config files belong to root and are read-only to the server. Works with a read-only root filesystem: everything NGINX writes (pid, temp files, the server config rendered at start-up) goes to `/tmp`, so `/tmp` is the only writable mount (`--tmpfs /tmp`, or an `emptyDir` in Kubernetes).
- **Same-origin API:** `/api/` is proxied to the backend, which keeps the refresh cookie (`Path=/api/auth`, `SameSite=Strict`) working and needs no CORS. The backend is resolved at request time, so the frontend starts and passes its health check even before the backend is up (`/api` answers 502 until then). If an ingress already routes `/api` to the backend, the proxy is simply unused.
- **Caching:** hashed `/assets/*` are immutable for a year; `index.html` and client routes are `no-cache`, so new deployments are picked up at once.
- **Security headers:** strict CSP (`script-src 'self'`, no inline scripts or eval; that's why the theme script is a file and Zod runs jitless), `nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy`, `Permissions-Policy`, `COOP`. HSTS belongs on the TLS-terminating load balancer.
- **No source maps** in the image; JSON access logs on stdout include the request's correlation ID.
- **Health:** `GET /healthz` returns 200; the image has a `HEALTHCHECK`.

Behind a TLS-terminating proxy, configure the backend with `server.forward-headers-strategy=native`
so it sees the client address from `X-Forwarded-For`; the login rate limit is per client address.

## Pinned versions

Dependencies are pinned exactly. Notable choices:

- **TypeScript 6.0**, not 7: `typescript-eslint` supports `< 6.1`.
- **React Router 7.18**, not 8: v8 requires Node ≥ 22.22.
- **MSW 2.x** and **@hookform/resolvers 5.x**: their new majors were only weeks old when the project started.
- **jsdom 29**: v30 requires Node ≥ 22.22.
