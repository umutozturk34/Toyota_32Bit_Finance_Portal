.PHONY: up down build rebuild deploy-common logs ps clean help demo empty reset

SHELL := /bin/bash

export GITHUB_TOKEN := $(shell gh auth token 2>/dev/null)

help:
	@echo "Finance Portal — common targets:"
	@echo "  make demo            clone-and-run: seeded demo data + demo user (recommended, no token)"
	@echo "  make empty           clone-and-run: clean database, no seed (no token)"
	@echo "  make reset           stop and WIPE all data (start over / switch modes)"
	@echo "  make up              start everything (builds finance-common from source, no token)"
	@echo "  make down            stop everything"
	@echo "  make build           build images (builds finance-common from source, no token)"
	@echo "  make rebuild         rebuild from scratch + up"
	@echo "  make deploy-common   publish finance-common to GitHub Packages (maintainers / CI only)"
	@echo "  make logs SERVICE=x  tail logs for a service"
	@echo "  make ps              list running services"
	@echo "  make clean           stop + remove images and volumes"

# Everyday run/build targets. finance-common is compiled from source as the first layer of
# the Docker build (see backend/Dockerfile), so these need no GitHub token and never publish
# the package — a clean clone builds end-to-end on its own.
up:
	docker compose up -d --build

build:
	docker compose build

rebuild:
	docker compose build --no-cache
	docker compose up -d

down:
	docker compose down

# Maintainer / CI only: publish finance-common to GitHub Packages. Local runs never need this —
# the images build finance-common from source, so this exists only to share the artifact.
deploy-common:
	@if [ -z "$(GITHUB_TOKEN)" ]; then \
		echo "ERROR: GITHUB_TOKEN unavailable. Run: gh auth refresh -s write:packages,read:packages"; \
		exit 1; \
	fi
	@echo "→ Deploying finance-common to GitHub Packages..."
	@cd finance-common && mvn -B deploy -DskipTests

logs:
	docker compose logs -f $(SERVICE)

ps:
	docker compose ps

clean:
	docker compose down -v --rmi local

# --- Clone-and-run targets (seeded demo vs empty DB; same tokenless source build) ---
.env:
	@cp .env.example .env && echo ".env created from .env.example — add API keys there for data."

demo: .env
	docker compose -f docker-compose.yml -f docker-compose.demo.yml up -d --build

empty: .env
	docker compose up -d --build

reset:
	docker compose -f docker-compose.yml -f docker-compose.demo.yml down -v
