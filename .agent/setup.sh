#!/usr/bin/env bash
# Roda COM internet, antes do agente começar (ver coding-agent/scripts/prepare-sandbox.sh).
# Baixa tudo o que build e testes precisam, porque depois a rede do container é desligada.
set -euo pipefail

mvn -B -q dependency:go-offline
# Compila e roda os testes uma vez: garante em cache os plugins de compilação e de teste.
mvn -B -q test || true
