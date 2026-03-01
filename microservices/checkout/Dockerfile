# ── Stage 1: Dependencies ─────────────────────────────────────────────────────
FROM node:18-alpine AS deps

WORKDIR /app
COPY package*.json ./
RUN npm ci --omit=dev

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM node:18-alpine

WORKDIR /app

RUN addgroup -S guitarshop && adduser -S guitarshop -G guitarshop

COPY --from=deps /app/node_modules ./node_modules
COPY src ./src
COPY package.json .

RUN chown -R guitarshop:guitarshop /app
USER guitarshop

EXPOSE 8080

CMD ["node", "src/index.js"]
