#!/usr/bin/env bash
# Verificação independente, rodada pelo workflow DEPOIS que o agente termina (sem internet).
# O resultado vai para a descrição do PR e para a mensagem final no chat.
set -euo pipefail

mvn -B -o -q test
