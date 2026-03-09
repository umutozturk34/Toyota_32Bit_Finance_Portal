FROM node:20-alpine AS build
WORKDIR /app

ARG VITE_BIST_STOCKS
ARG VITE_TRACKED_CRYPTOS
ARG VITE_FUNDS_ALL

ENV VITE_BIST_STOCKS=$VITE_BIST_STOCKS
ENV VITE_TRACKED_CRYPTOS=$VITE_TRACKED_CRYPTOS
ENV VITE_FUNDS_ALL=$VITE_FUNDS_ALL

COPY package*.json ./

RUN npm ci

COPY . .

RUN npm run build

FROM nginx:alpine
WORKDIR /usr/share/nginx/html

RUN rm -rf ./*

COPY --from=build /app/dist .

COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:80 || exit 1

CMD ["nginx", "-g", "daemon off;"]
