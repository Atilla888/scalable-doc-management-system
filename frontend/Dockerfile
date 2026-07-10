# ── Development stage ────────────────────────────────────────────────────────
FROM node:lts-trixie-slim AS dev

WORKDIR /app

COPY package*.json ./
# npm ci installs exactly the committed lockfile (reproducible, fails on drift)
RUN npm ci

COPY . .

EXPOSE 5173

CMD ["npm", "run", "dev"]

# ── Build stage ───────────────────────────────────────────────────────────────
FROM node:lts-trixie-slim AS builder

WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build

# ── Production stage (nginx on Debian 13) ─────────────────────────────────────
FROM debian:trixie-slim AS prod

RUN apt-get update \
    && apt-get install -y --no-install-recommends nginx \
    && rm -rf /var/lib/apt/lists/* \
    # Debian ships a default site that grabs port 80 and 404s unknown paths,
    # shadowing our SPA fallback. Drop it so client-side routes work.
    && rm -f /etc/nginx/sites-enabled/default

COPY --from=builder /app/dist /var/www/html

COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
