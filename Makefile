# Short names for scripts/portable (the runtime knowledge is in compose.yaml) and Maven.
RUNTIME ?= quarkus

.PHONY: help build build-dev test format lint up verify logs down matrix bench clean

help:
	@bash scripts/portable help
	@printf '\nmake: build build-dev test format lint up verify logs down matrix bench clean (RUNTIME=%s)\n' "$(RUNTIME)"

build:
	bash scripts/portable build

build-dev:
	bash scripts/portable build --dev

# Unit tests only: the conformance suite skips itself without a running runtime.
test:
	mvn -B test

format:
	mvn -q spotless:apply

lint:
	bash scripts/portable lint

up:
	bash scripts/portable up $(RUNTIME)

verify:
	bash scripts/portable verify $(RUNTIME)

logs:
	bash scripts/portable logs $(RUNTIME)

down:
	bash scripts/portable down

matrix:
	bash scripts/portable matrix

bench:
	bash scripts/portable bench $(RUNTIMES)

clean: down
	mvn -q clean
