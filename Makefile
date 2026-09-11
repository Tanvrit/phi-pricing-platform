.PHONY: help test build server aegis aegis-customer aegis-web core-test docker-up docker-down clean fmt lint coverage mongo-shell seed

# Local dev env for the server (Mongo replica set on host:27017). Override on the CLI.
MONGO_URI ?= mongodb://127.0.0.1:27018/?directConnection=true
MONGO_DB ?= rate_calculator
OTP_TOKEN_SECRET ?= rate_local_dev_otp_secret_7f3a9c21e5d4
JWT_SIGNING_SECRET ?= rate_local_dev_jwt_secret_b2d8f4a16e9c
CORS_ALLOWED_ORIGINS ?= http://localhost:9090,http://localhost:9092
PORT ?= 9090
AEGIS_DEV_PROFILE ?= true
SERVER_ENV = PORT=$(PORT) MONGO_URI='$(MONGO_URI)' MONGO_DB=$(MONGO_DB) \
	OTP_TOKEN_SECRET=$(OTP_TOKEN_SECRET) JWT_SIGNING_SECRET=$(JWT_SIGNING_SECRET) \
	CORS_ALLOWED_ORIGINS=$(CORS_ALLOWED_ORIGINS) AEGIS_DEV_PROFILE=$(AEGIS_DEV_PROFILE)

help:
	@echo "PRUHealth Rate Platform — common targets (MongoDB + core/sdk modules)"
	@echo ""
	@echo "  make build          Build all modules (gradlew build -x test)"
	@echo "                      Needs a headless Chrome on CHROME_BIN: -x test"
	@echo "                      skips only tasks named 'test', so the 19"
	@echo "                      wasmJs modules still run wasmJsBrowserTest."
	@echo "                      See docs/08-development.md."
	@echo "  make test           Run all module tests"
	@echo "  make core-test      Run :shared:core + :shared:sdk commonTest only"
	@echo "  make docker-up      Start MongoDB single-node replica set (docker)"
	@echo "  make docker-down    Stop MongoDB"
	@echo "  make server         Run the Ktor server on port $(PORT) against Mongo"
	@echo "  make seed           Seed catalog + rate tables from /data via the import API"
	@echo "  make aegis          Run Aegis desktop (BUSINESS operator+admin console)"
	@echo "  make aegis-customer Run Aegis desktop in CUSTOMER role (buyonline preview)"
	@echo "  make aegis-web      Build the Aegis WASM bundle for Cloudflare Pages"
	@echo "  make mongo-shell    Open a mongosh shell on the dev replica set"
	@echo "  make clean          Gradle clean"

test:
	./gradlew test

# Pure-KMP module tests (no DB / network) — fast feedback on core + sdk logic.
core-test:
	./gradlew $(shell ./gradlew -q projects 2>/dev/null | grep -oE ":shared:(core|sdk):[a-z-]+" | sed 's/$$/:jvmTest/' | tr '\n' ' ')

build:
	./gradlew build -x test

server:
	$(SERVER_ENV) ./gradlew :server:run

# Seed reference data from the /data CSVs through the running server's import endpoint.
seed:
	@echo "POST /data CSVs to http://localhost:$(PORT)/api/import (server must be running)…"
	@for f in data/*.csv; do curl -s -F "file=@$$f" http://localhost:$(PORT)/api/import >/dev/null && echo "  seeded $$f"; done

aegis:
	./gradlew :aegis:run

aegis-customer:
	./gradlew :aegis:run -Daegis.role=CUSTOMER

aegis-web:
	./gradlew :aegis:wasmJsBrowserDistribution

docker-up:
	docker compose up -d mongo

docker-down:
	docker compose down

mongo-shell:
	docker compose exec mongo mongosh "$(MONGO_DB)"

coverage:
	@echo "Kover deferred — see CHANGELOG.md → Unreleased → Deferred."

clean:
	./gradlew clean
