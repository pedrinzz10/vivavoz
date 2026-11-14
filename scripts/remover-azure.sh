#!/usr/bin/env bash
# SPEC-024: apaga o grupo de recursos da VozViva na Azure (API, Azure SQL, ACR, RabbitMQ) e para a cobrança.
# Irreversível: os dados do banco são perdidos. Uso: ./scripts/remover-azure.sh  (ou CONFIRMAR=sim para não perguntar)
set -euo pipefail

GRUPO="${GRUPO:-rg-vozviva}"

if ! az group show -n "$GRUPO" > /dev/null 2>&1; then
  echo "O grupo ${GRUPO} não existe; nada a remover."
  exit 0
fi

echo "Recursos que serão apagados:"
az resource list -g "$GRUPO" --query "[].{nome:name, tipo:type}" -o table

if [ "${CONFIRMAR:-}" != "sim" ]; then
  read -r -p "Apagar o grupo ${GRUPO} e TODOS os recursos acima? Digite o nome do grupo para confirmar: " resposta
  if [ "$resposta" != "$GRUPO" ]; then
    echo "Cancelado."
    exit 1
  fi
fi

az group delete -n "$GRUPO" --yes --no-wait
echo "Remoção de ${GRUPO} iniciada (leva alguns minutos). Acompanhe com: az group show -n ${GRUPO}"
