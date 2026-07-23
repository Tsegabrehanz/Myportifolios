# --- Build stage ---
FROM node:20-alpine AS build
WORKDIR /app

# Install dependencies first so this layer is cached unless
# package*.json actually changed.
COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build -- --configuration production

# --- Runtime stage ---
FROM nginx:1.27-alpine

# Angular's new (esbuild-based) build system nests the actual static
# site under dist/<project-name>/browser/ - verify this path if you
# rename the project or upgrade to a future Angular CLI that changes
# the output layout again.
COPY --from=build /app/dist/eems-frontend/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
