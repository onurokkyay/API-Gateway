# API Gateway

The single door into the estate. It routes requests, verifies every access token, and tells the
service behind it who the caller is.

## What it does, and what it does not

It **authenticates**: every request that is not on a short public list must carry a valid RS256
access token issued by the [auth service](https://github.com/onurokkyay/AuthService). Tokens are
verified against that service's JWKS, so this gateway holds a public key and nothing capable of
minting a token.

It **propagates identity**: a verified token's `sub` and `role` are written onto the outgoing
request as `X-User-Id` and `X-User-Role`.

It does **not authorize**. Whether a user may touch a particular resource is a question only the
owning service can answer; encoding those rules here would mean editing the gateway every time a
service changed its mind. The one exception is `/actuator/**`, which describes the estate rather
than any user's data and requires `ADMIN`.

## The trust model, and the two things it rests on

Routed services read `X-User-Id` and cannot verify it. That is a deliberate trade — no service
needs a JWT library or a key — but it is only sound while both of these hold:

1. **The gateway strips these headers from every inbound request**, before routing and on every
   path, including the public ones. Spring Cloud Gateway forwards unknown headers by default, so
   without this a caller could present their own valid token alongside
   `X-User-Id: <someone else>` and be served as that person. `IdentityPropagationFilterTest`
   exists to make sure this never regresses.
2. **No routed service is reachable except through the gateway.** If a service's port is
   published where anyone else can reach it, `X-User-Id` becomes an unauthenticated login form.
   Publish the gateway's port; do not publish theirs.

## Contract for a routed service

| Header | Value |
| --- | --- |
| `X-User-Id` | The account id — a UUIDv7, the token's `sub`. **Use this as the foreign key.** |
| `X-User-Role` | `USER` or `ADMIN` |

Both are absent on an anonymous request rather than empty, so "no header" means anonymous without
having to be told apart from "header present but blank".

## Configuration

Routes, JWKS location and CORS come from the config server
([Config-Repo](https://github.com/onurokkyay/Config-Repo) → `api-gateway.yml`).

The import is **mandatory**, not `optional:`. A gateway that starts without its routes answers
404 to everything while reporting itself healthy, which is worse than not starting at all. The
retry settings in `application.yml` cover the ordinary case of the config server simply being
slower to come up than its clients.

This is also the only service still reading from the config server, and that is deliberate: its
configuration is **routes**, which change whenever a service is added or moved — exactly what a
config server makes cheap. The auth service's configuration is signing keys and lifetimes, which
change almost never and need a restart anyway, so it reads its environment directly.

### Running without a config server

For a container smoke test, replace the configuration entirely and turn the client off — an empty
or `optional:` import is not enough, because disabling the client removes the resolver that
understands the `configserver:` prefix:

```bash
docker run -e SPRING_CONFIG_LOCATION=file:/app/config/standalone.yml \
           -v "$PWD/standalone:/app/config:ro" api-gateway:local
```

with `spring.cloud.config.enabled: false` and no `spring.config.import` in that file.

## Public paths

`/api/auth/register`, `/api/auth/login`, `/api/auth/refresh`, `/api/auth/logout`,
`/.well-known/**`, the per-service Swagger paths, and `/fallback`. Every one of them is a door in
the authentication boundary, so the list is kept as short as it can be. `/api/auth/me` is
deliberately absent: it answers "who am I", which requires being someone.

## Logging

One line per request: method, path, status, duration. No headers, no bodies, no query strings.

The three filters this replaced logged whole request and response bodies at INFO, which meant
every password sent to `/api/auth/login` and every token it answered with was written to the
gateway's log in plain text, alongside the Authorization header. They also buffered every JSON
body into memory to do it.
