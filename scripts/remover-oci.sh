#!/usr/bin/env bash
# Apaga na OCI a API key do Generative AI e o orçamento criados por scripts/provisionar-oci.sh, para recriar do zero.
# O compartimento vozviva fica (vazio, não custa nada) e é reaproveitado na próxima execução do provisionar-oci.sh.
# Uso, no Cloud Shell da OCI: ./scripts/remover-oci.sh  (ou CONFIRMAR=sim para não perguntar)
set -euo pipefail

REGIAO="${REGIAO:-sa-saopaulo-1}"
COMPARTIMENTO="${COMPARTIMENTO:-vozviva}"
NOME_ORCAMENTO="vozviva-ia"
ARQUIVO_ENV="${HOME}/.vozviva/oci-genai.env"

: "${OCI_TENANCY:?rode no Cloud Shell da OCI (a variável OCI_TENANCY vem configurada)}"
oci_r() { oci --region "$REGIAO" "$@"; }

COMP=$(oci iam compartment list --compartment-id "$OCI_TENANCY" --name "$COMPARTIMENTO" \
  --lifecycle-state ACTIVE --query 'data[0].id' --raw-output 2> /dev/null || true)
CHAVES=""
if [ -n "$COMP" ] && [ "$COMP" != "null" ]; then
  CHAVES=$(oci_r generative-ai api-key list --compartment-id "$COMP" --all \
    --query 'data.items[?"lifecycle-state"!=`DELETED` && "lifecycle-state"!=`DELETING`].id' --raw-output 2> /dev/null \
    | jq -r '.[]?' 2> /dev/null || true)
fi
ORC=$(oci budgets budget budget list --compartment-id "$OCI_TENANCY" --all \
  --query "data[?\"display-name\"=='${NOME_ORCAMENTO}'].id | [0]" --raw-output 2> /dev/null || true)
[ "$ORC" = "null" ] && ORC=""

echo "Vai apagar:"
echo "  - API keys no compartimento ${COMPARTIMENTO}: $(printf '%s' "$CHAVES" | grep -c . || true)"
echo "  - orçamento ${NOME_ORCAMENTO}: $([ -n "$ORC" ] && echo sim || echo 'não existe')"
echo "  - arquivo ${ARQUIVO_ENV}: $([ -f "$ARQUIVO_ENV" ] && echo sim || echo 'não existe')"

if [ "${CONFIRMAR:-}" != "sim" ]; then
  read -r -p "Confirmar? Digite sim: " resposta
  [ "$resposta" = "sim" ] || { echo "Cancelado."; exit 1; }
fi

for id in $CHAVES; do
  oci_r generative-ai api-key delete --api-key-id "$id" --force > /dev/null
  echo "    API key apagada"
done
if [ -n "$ORC" ]; then
  oci budgets budget budget delete --budget-id "$ORC" --force > /dev/null
  echo "    orçamento apagado"
fi
rm -f "$ARQUIVO_ENV"
echo "Pronto. Para criar de novo: ORCAMENTO_EMAIL=voce@fiap.com.br ./scripts/provisionar-oci.sh"
