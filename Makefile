.PHONY: help test build server aegis aegis-customer aegis-web shared-test docker-up docker-down clean fmt lint coverage

help:
	@echo "PRUHealth Rate Platform — common targets"
	@echo ""
	@echo "  make test           Run all tests + coverage report"
	@echo "  make shared-test    Run :shared tests only (no DB / network needed)"
	@echo "  make build          Build all modules"
	@echo "  make server         Run the Ktor server on port 9090"
	@echo "  make aegis          Run Aegis desktop (BUSINESS role — operator console)"
	@echo "  make aegis-customer Run Aegis desktop in CUSTOMER role (buyonline preview)"
	@echo "  make aegis-web      Build the Aegis WASM bundle for Cloudflare Pages"
	@echo "  make docker-up      docker compose up -d (Postgres + server)"
	@echo "  make docker-down    docker compose down"
	@echo "  make coverage       Open Kover HTML coverage report"
	@echo "  make clean          Gradle clean"

test:
	./gradlew test

shared-test:
	./gradlew :shared:jvmTest

build:
	./gradlew build -x test

server:
	./gradlew :server:run

aegis:
	./gradlew :aegis:run

aegis-customer:
	./gradlew :aegis:run -Daegis.role=CUSTOMER

aegis-web:
	./gradlew :aegis:wasmJsBrowserDistribution

docker-up:
	docker compose up -d --build

docker-down:
	docker compose down

coverage:
	@echo "Kover deferred — see CHANGELOG.md → Unreleased → Deferred."

clean:
	./gradlew clean
