# ADR-0003: JWT access tokens with rotating refresh tokens

**Status:** Accepted · 2026-10-30. The project owner chose JWT over the originally proposed server-side sessions.

## Context
The main client is a browser SPA. We need horizontally scalable, stateless API instances, brute-force protection, and the ability to cut off disabled users and role changes quickly.

## Options considered
1. **Server-side sessions in an HTTP-only cookie (Spring Session JDBC).** Instant revocation, but a database lookup per request and CSRF protection on every call.
2. **Long-lived JWT only.** Simple, but cannot be revoked; role changes and disabled accounts take effect only on expiry.
3. **Short-lived JWT access token plus an opaque, rotating, database-backed refresh token.** Chosen.

## Decision
- **Access token:** a JWT signed with HS256, valid for **10 minutes**. Claims: `iss`, `aud`, `sub` (user id), `iat`, `exp`, `jti`, `roles`. No email or name, because JWTs are readable by anyone holding them. It is sent as `Authorization: Bearer` and kept **in memory** by the SPA, never in `localStorage`.
- **Verification:** the algorithm is pinned (HS256 only, no `alg: none`), with signature, `exp`/`nbf` (60 s skew), issuer and audience checks. Implemented with Spring Security's OAuth2 resource server (`NimbusJwtDecoder`), not hand-written parsing.
- **Key:** at least 256 bits from `CLOUDFORGE_JWT_SECRET`. Startup fails without it, and there is no default. HS256 is enough while CloudForge is the only issuer and verifier. Move to RS256/ES256 and JWKS if other services need to verify tokens.
- **Refresh token:** a 256-bit random value stored only as a SHA-256 hash, valid for 7 days. It is sent only in an `HttpOnly; Secure; SameSite=Strict; Path=/api/auth` cookie. **Rotated on every use.** Presenting an already-rotated token revokes the whole token family (theft detection). A concurrent double rotation is stopped by optimistic locking.
- **Revocation:** logout revokes the refresh family. Disabling a user or changing their roles revokes all of the user's refresh tokens. Refresh reloads the user and fails if they are disabled, so new tokens always carry current roles.
- **CSRF:** disabled. Bearer headers are not sent automatically by browsers. The single cookie is `SameSite=Strict` and scoped to the token endpoints.
- **Brute force:** per-account lockout after 5 consecutive failures (1, 2, 4, 8, then at most 15 minutes). Unknown account, wrong password, and disabled or locked accounts all give the same response and similar timing.

## Consequences
- **Accepted risk:** after disabling a user or changing roles, an already-issued access token stays valid for up to **10 minutes**. Logout likewise does not invalidate the current access token. If this window becomes unacceptable, add a `jti` denylist or a per-user token version check (one indexed lookup per request).
- The SPA must call `POST /api/auth/refresh` before the access token expires, and again after a page reload, since the access token lives in memory.
- Per-account lockout lets an attacker deliberately lock a known account for short periods. Add per-IP rate limiting at the edge (reverse proxy or WAF) when deploying.
- No server-side session state: any instance can serve any request.
