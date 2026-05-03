.PHONY: up down build rebuild deploy-common logs ps clean help

SHELL := /bin/bash

export GITHUB_TOKEN := $(shell gh auth token 2>/dev/null)

COMMON_HASH := $(shell find finance-common/src finance-common/pom.xml -type f 2>/dev/null | sort | xargs md5 -q 2>/dev/null | md5 -q)
DEPLOYED_HASH_FILE := .common-deployed-hash
DEPLOYED_HASH := $(shell cat $(DEPLOYED_HASH_FILE) 2>/dev/null)

help:
	@echo "Finance Portal — common targets:"
	@echo "  make up              start everything (auto-deploys finance-common if changed)"
	@echo "  make down            stop everything"
	@echo "  make build           build images (auto-deploys finance-common if changed)"
	@echo "  make rebuild         force redeploy + rebuild + up"
	@echo "  make deploy-common   manually publish finance-common to GitHub Packages"
	@echo "  make logs SERVICE=x  tail logs for a service"
	@echo "  make ps              list running services"
	@echo "  make clean           stop + remove images and volumes"

deploy-common:
	@if [ -z "$(GITHUB_TOKEN)" ]; then \
		echo "ERROR: GITHUB_TOKEN unavailable. Run: gh auth refresh -s write:packages,read:packages"; \
		exit 1; \
	fi
	@echo "→ Deploying finance-common to GitHub Packages..."
	@cd finance-common && mvn -B deploy -DskipTests
	@echo "$(COMMON_HASH)" > $(DEPLOYED_HASH_FILE)
	@echo "→ finance-common $(COMMON_HASH) published"

deploy-common-if-changed:
	@if [ "$(COMMON_HASH)" != "$(DEPLOYED_HASH)" ]; then \
		$(MAKE) deploy-common; \
	else \
		echo "→ finance-common unchanged, skip deploy"; \
	fi

build: deploy-common-if-changed
	@if [ -z "$(GITHUB_TOKEN)" ]; then \
		echo "ERROR: GITHUB_TOKEN unavailable. Run: gh auth refresh -s write:packages,read:packages"; \
		exit 1; \
	fi
	docker compose build

up: deploy-common-if-changed
	@if [ -z "$(GITHUB_TOKEN)" ]; then \
		echo "ERROR: GITHUB_TOKEN unavailable. Run: gh auth refresh -s write:packages,read:packages"; \
		exit 1; \
	fi
	docker compose up -d --build

down:
	docker compose down

rebuild: deploy-common
	docker compose build --no-cache
	docker compose up -d

logs:
	docker compose logs -f $(SERVICE)

ps:
	docker compose ps

clean:
	docker compose down -v --rmi local
	rm -f $(DEPLOYED_HASH_FILE)
