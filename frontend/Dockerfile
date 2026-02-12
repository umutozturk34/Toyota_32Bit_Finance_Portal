  # Build stage
FROM node:20-alpine AS build
WORKDIR /app

# Accept build arguments
ARG VITE_BIST_STOCKS
ARG VITE_TRACKED_CRYPTOS

# Set environment variables for Vite
ENV VITE_BIST_STOCKS=$VITE_BIST_STOCKS
ENV VITE_TRACKED_CRYPTOS=$VITE_TRACKED_CRYPTOS

# Copy package files
COPY package*.json ./

# Install dependencies
RUN npm ci

# Copy source code
COPY . .

# Build application (env vars baked into build)
RUN npm run build

# Run stage
FROM nginx:alpine
WORKDIR /usr/share/nginx/html

# Remove default nginx static assets
RUN rm -rf ./*

# Copy built assets from build stage
COPY --from=build /app/dist .

# Copy nginx configuration
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Expose port
EXPOSE 80

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:80 || exit 1

# Start nginx
CMD ["nginx", "-g", "daemon off;"]
