# ---- build ----------------------------------------------------------------
FROM node:22-alpine AS build
WORKDIR /build

# Dependencies resolve on their own layer, so a source-only change does not
# reinstall node_modules on every rebuild.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build -- --configuration docker

# ---- serve ----------------------------------------------------------------
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist/frontend/browser /usr/share/nginx/html
EXPOSE 80
