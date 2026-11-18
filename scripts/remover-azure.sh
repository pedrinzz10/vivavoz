#!/usr/bin/env bash
# SPEC-024: apaga o grupo de recursos da VozViva na Azure (APIs, Azure SQL, ACR, RabbitMQ, Key Vault) e para a
# cobrança.
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

# O Key Vault apagado iria para a lixeira por 7 dias, com o nome reservado: apaga e expurga antes, para o
# provisionar-azure.sh poder criar o cofre de novo com o mesmo nome (num ensaio seguido da gravação, por exemplo).
for cofre in $(az keyvault list -g "$GRUPO" --query '[].name' -o tsv 2> /dev/null); do
  az keyvault delete -n "$cofre" -o none && az keyvault purge -n "$cofre" -o none \
    && echo "Key Vault ${cofre} apagado e expurgado (as senhas guardadas nele foram junto)." \
    || echo "Não consegui expurgar o Key Vault ${cofre}; ele fica 7 dias na lixeira e o provisionar-azure.sh o recupera."
done

az group delete -n "$GRUPO" --yes --no-wait
echo "Remoção de ${GRUPO} iniciada (leva alguns minutos). Acompanhe com: az group show -n ${GRUPO}"
echo "O orçamento vozviva-creditos é do grupo e sai junto com ele."
