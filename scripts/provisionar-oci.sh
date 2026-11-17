#!/usr/bin/env bash
# Cria na OCI o que a API precisa para a IA generativa (OCI Generative AI, endpoint compatível com OpenAI):
#   - compartimento "vozviva" (separa a chave e os custos);
#   - API key do Generative AI, válida até a data de EXPIRACAO;
#   - orçamento mensal no compartimento, com alertas por e-mail em 50% e 100%;
#   - uma chamada de teste ao modelo.
#
# Rode no Cloud Shell da OCI (o CLI já vem autenticado), na região do modelo (padrão sa-saopaulo-1):
#   ORCAMENTO_EMAIL=voce@fiap.com.br ./scripts/provisionar-oci.sh
#
# Idempotente: o que já existe é reaproveitado. A chave criada fica só em ~/.vozviva/oci-genai.env (permissão 600);
# ela não aparece na tela. Para levar à Azure, veja as instruções no fim da execução.
#
# Opcionais: REGIAO (padrão sa-saopaulo-1), COMPARTIMENTO (padrão vozviva), MODELO (padrão openai.gpt-oss-120b),
# EXPIRACAO (padrão 2026-12-31T23:59:59Z), ORCAMENTO_USD (padrão 10), ORCAMENTO_EMAIL (sem ele, não cria orçamento).
set -euo pipefail

REGIAO="${REGIAO:-sa-saopaulo-1}"
COMPARTIMENTO="${COMPARTIMENTO:-vozviva}"
MODELO="${MODELO:-openai.gpt-oss-120b}"
EXPIRACAO="${EXPIRACAO:-2026-12-31T23:59:59Z}"
ORCAMENTO_USD="${ORCAMENTO_USD:-10}"
ORCAMENTO_EMAIL="${ORCAMENTO_EMAIL:-}"
NOME_CHAVE="vozviva-api"
NOME_ORCAMENTO="vozviva-ia"
BASE_URL="https://inference.generativeai.${REGIAO}.oci.oraclecloud.com/openai/v1"
PASTA_SEGREDOS="${HOME}/.vozviva"
ARQUIVO_ENV="${PASTA_SEGREDOS}/oci-genai.env"

: "${OCI_TENANCY:?rode no Cloud Shell da OCI (a variável OCI_TENANCY vem configurada)}"
command -v jq > /dev/null || { echo "jq não encontrado"; exit 1; }

passo() { printf '\n==> %s\n' "$*"; }
criado() { echo "    criado: $*"; }
reaproveitado() { echo "    já existia: $*"; }
oci_r() { oci --region "$REGIAO" "$@"; }

passo "Modelo ${MODELO} em ${REGIAO}"
ATIVO=$(oci_r generative-ai model-collection list-models --compartment-id "$OCI_TENANCY" --all \
  --query "length(data.items[?\"display-name\"=='${MODELO}' && \"lifecycle-state\"=='ACTIVE'])" --raw-output)
if [ "${ATIVO:-0}" = "0" ]; then
  echo "    o modelo não está ativo nessa região; escolha outro MODELO ou outra REGIAO"
  exit 1
fi
echo "    ativo"

passo "Compartimento ${COMPARTIMENTO}"
COMP=$(oci iam compartment list --compartment-id "$OCI_TENANCY" --name "$COMPARTIMENTO" \
  --lifecycle-state ACTIVE --query 'data[0].id' --raw-output 2> /dev/null || true)
if [ -n "$COMP" ] && [ "$COMP" != "null" ]; then
  reaproveitado "$COMPARTIMENTO"
else
  COMP=$(oci iam compartment create --compartment-id "$OCI_TENANCY" --name "$COMPARTIMENTO" \
    --description "VozViva - IA generativa da Global Solution" --wait-for-state ACTIVE \
    --query 'data.id' --raw-output)
  criado "$COMPARTIMENTO"
fi

passo "API key ${NOME_CHAVE} (válida até ${EXPIRACAO})"
mkdir -p "$PASTA_SEGREDOS" && chmod 700 "$PASTA_SEGREDOS"
EXISTENTE=$(oci_r generative-ai api-key list --compartment-id "$COMP" --all \
  --query "length(data.items[?\"display-name\"=='${NOME_CHAVE}' && \"lifecycle-state\"=='ACTIVE'])" \
  --raw-output 2> /dev/null || echo 0)
if [ "${EXISTENTE:-0}" != "0" ]; then
  reaproveitado "$NOME_CHAVE"
  [ -f "$ARQUIVO_ENV" ] || echo "    o valor da chave só aparece na criação; sem ${ARQUIVO_ENV}, apague a chave no console e rode de novo"
else
  RESPOSTA="${PASTA_SEGREDOS}/oci-apikey-criacao.json"
  umask 077
  # Um compartimento recém-criado pode levar alguns segundos para ficar visível ao Generative AI.
  for tentativa in 1 2 3 4 5 6; do
    if oci_r generative-ai api-key create --compartment-id "$COMP" --display-name "$NOME_CHAVE" \
        --description "API Java VozViva (Spring AI)" \
        --key-details "[{\"keyName\":\"principal\",\"timeExpiry\":\"${EXPIRACAO}\"}]" \
        --wait-for-state ACTIVE > "$RESPOSTA" 2> "${RESPOSTA}.erro"; then
      break
    fi
    [ "$tentativa" = 6 ] && { cat "${RESPOSTA}.erro"; exit 1; }
    sleep 20
  done
  rm -f "${RESPOSTA}.erro"
  # Procura o valor da chave na resposta sem imprimi-lo.
  CHAVE=$(jq -r '[.. | objects | (.key? // .value? // .secret? // empty) | strings | select(length > 20)][0] // empty' "$RESPOSTA")
  if [ -z "$CHAVE" ]; then
    echo "    chave criada, mas o valor não veio no formato esperado. Campos da resposta (sem valores):"
    jq -r '[paths(scalars) | map(tostring) | join(".")] | .[]' "$RESPOSTA" | sed 's/^/      /'
    echo "    a resposta completa está em ${RESPOSTA} (não compartilhe)"
    exit 1
  fi
  printf 'export OCI_GENAI_API_KEY=%q\nexport OCI_GENAI_BASE_URL=%q\nexport OCI_GENAI_MODEL=%q\n' \
    "$CHAVE" "$BASE_URL" "$MODELO" > "$ARQUIVO_ENV"
  chmod 600 "$ARQUIVO_ENV"
  rm -f "$RESPOSTA"
  criado "$NOME_CHAVE (valor salvo em ${ARQUIVO_ENV})"
fi

passo "Orçamento ${NOME_ORCAMENTO} (US\$ ${ORCAMENTO_USD} por mês)"
if [ -z "$ORCAMENTO_EMAIL" ]; then
  echo "    pulado: defina ORCAMENTO_EMAIL para criar o orçamento com alertas"
else
  ORC=$(oci budgets budget budget list --compartment-id "$OCI_TENANCY" --all \
    --query "data[?\"display-name\"=='${NOME_ORCAMENTO}'].id | [0]" --raw-output 2> /dev/null || true)
  if [ -n "$ORC" ] && [ "$ORC" != "null" ]; then
    reaproveitado "$NOME_ORCAMENTO"
  else
    ORC=$(oci budgets budget budget create --compartment-id "$OCI_TENANCY" --target-type COMPARTMENT \
      --targets "[\"${COMP}\"]" --amount "$ORCAMENTO_USD" --reset-period MONTHLY \
      --display-name "$NOME_ORCAMENTO" --query 'data.id' --raw-output)
    for limite in 50 100; do
      oci budgets budget alert-rule create --budget-id "$ORC" --type ACTUAL --threshold "$limite" \
        --threshold-type PERCENTAGE --recipients "$ORCAMENTO_EMAIL" --display-name "alerta-${limite}" > /dev/null
    done
    criado "$NOME_ORCAMENTO (alertas em 50% e 100% para ${ORCAMENTO_EMAIL})"
  fi
fi

passo "Teste: uma chamada curta ao modelo"
if [ -f "$ARQUIVO_ENV" ]; then
  # shellcheck disable=SC1090
  source "$ARQUIVO_ENV"
  STATUS=$(curl -s -o /dev/null -w '%{http_code}' -m 60 "${BASE_URL}/chat/completions" \
    -H "Authorization: Bearer ${OCI_GENAI_API_KEY}" -H 'Content-Type: application/json' \
    -d "{\"model\":\"${MODELO}\",\"messages\":[{\"role\":\"user\",\"content\":\"Responda só: ok\"}],\"max_tokens\":200}")
  case "$STATUS" in
    200) echo "    o modelo respondeu (HTTP 200)" ;;
    401|403|404) echo "    HTTP ${STATUS}: a chave existe, mas falta permissão (policy) para usá-la; veja a seção de API keys na documentação do Generative AI" ;;
    *) echo "    HTTP ${STATUS}: confira a região, o modelo e se a conta tem créditos ou é Pay As You Go" ;;
  esac
else
  echo "    pulado: sem ${ARQUIVO_ENV}"
fi

cat <<EOF

Pronto. Para usar a chave:
  - neste Cloud Shell:  source ${ARQUIVO_ENV}
  - na Azure: no Cloud Shell da Azure, rode   read -rs OCI_GENAI_API_KEY && export OCI_GENAI_API_KEY
    e cole o valor (exibido com: cat ${ARQUIVO_ENV}). Depois rode ./scripts/provisionar-azure.sh.
  - endpoint: ${BASE_URL}  modelo: ${MODELO}
Para apagar tudo: exclua a API key e o orçamento no console e depois o compartimento ${COMPARTIMENTO}.
EOF
