#!/usr/bin/env bash
# Testa se o modelo do OCI Generative AI responde a chamadas assinadas com o usuário da OCI (IAM), sem API key do
# Generative AI. É o "plano B" para quando o limite api-key-count da conta é 0.
# Não cria nem apaga nada; faz algumas chamadas curtas ao modelo (custo de frações de centavo de dólar).
# Uso, no Cloud Shell da OCI: ./scripts/testar-ia-oci-iam.sh
set -uo pipefail

REGIAO="${REGIAO:-sa-saopaulo-1}"
COMPARTIMENTO="${COMPARTIMENTO:-vozviva}"
MODELO="${MODELO:-openai.gpt-oss-120b}"
HOST="https://inference.generativeai.${REGIAO}.oci.oraclecloud.com"

: "${OCI_TENANCY:?rode no Cloud Shell da OCI (a variável OCI_TENANCY vem configurada)}"
command -v jq > /dev/null || { echo "jq não encontrado"; exit 1; }

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
passo() { printf '\n==> %s\n' "$*"; }
oci_r() { oci --region "$REGIAO" "$@"; }

COMP=$(oci iam compartment list --compartment-id "$OCI_TENANCY" --name "$COMPARTIMENTO" \
  --lifecycle-state ACTIVE --query 'data[0].id' --raw-output 2> /dev/null || true)
[ -n "$COMP" ] && [ "$COMP" != "null" ] || COMP="$OCI_TENANCY"

# Mostra o status HTTP e o começo da resposta do modelo (ou a mensagem de erro). Devolve 0 se o modelo respondeu.
resultado() {
  jq -r '
    "    status: \(.status // "?")",
    ( .data as $d
      | if ($d | type) == "object" then
          ( ($d.choices[0].message.content // $d.chatResponse.choices[0].message.content[0].text // null) as $t
            | if $t then "    resposta do modelo: \($t | tostring | .[0:120])"
              else "    corpo: \($d | tostring | .[0:300])" end )
        else "    corpo: \($d | tostring | .[0:300])" end )' "$1" 2> /dev/null || head -c 600 "$1"
  jq -e '(.status // "") | startswith("200")' "$1" > /dev/null 2>&1
}

# Chama o endpoint compatível com OpenAI e a API nativa com o identificador de modelo $1.
testar() {
  local id="$1" rotulo="$2" ok=1
  jq -n --arg m "$id" \
    '{model: $m, messages: [{role: "user", content: "Responda só: ok"}], max_tokens: 200}' > "$TMP/corpo.json"
  jq -n --arg c "$COMP" '{"opc-compartment-id": $c, "CompartmentId": $c}' > "$TMP/cabecalhos.json"
  echo "  - compatível com OpenAI, modelo ${rotulo}:"
  oci raw-request --http-method POST --target-uri "${HOST}/openai/v1/chat/completions" \
    --request-body "$(cat "$TMP/corpo.json")" --request-headers "$(cat "$TMP/cabecalhos.json")" > "$TMP/r.json" 2>&1
  resultado "$TMP/r.json" && ok=0
  jq -n --arg c "$COMP" --arg m "$id" '{
    compartmentId: $c,
    servingMode: {servingType: "ON_DEMAND", modelId: $m},
    chatRequest: {apiFormat: "GENERIC", maxTokens: 200,
      messages: [{role: "USER", content: [{type: "TEXT", text: "Responda só: ok"}]}]}
  }' > "$TMP/corpo2.json"
  echo "  - API nativa, modelo ${rotulo}:"
  oci raw-request --http-method POST --target-uri "${HOST}/20231130/actions/chat" \
    --request-body "$(cat "$TMP/corpo2.json")" > "$TMP/r.json" 2>&1
  resultado "$TMP/r.json" && ok=0
  return $ok
}

passo "Modelos de chat sob demanda em ${REGIAO} (nome, estado, final do id)"
oci_r generative-ai model-collection list-models --compartment-id "$OCI_TENANCY" --all > "$TMP/modelos.json" 2> /dev/null \
  || echo '{}' > "$TMP/modelos.json"
[ -s "$TMP/modelos.json" ] || echo '{}' > "$TMP/modelos.json"
jq -r '[.data.items[]? | select((.capabilities // []) | index("CHAT")) | select(."lifecycle-state" == "ACTIVE")]
  | unique_by(."display-name") | .[] | "    \(."display-name")  \(."lifecycle-state")  \(.id[-8:])"' "$TMP/modelos.json"
MODELO_ID=$(jq -r --arg m "$MODELO" '[.data.items[]? | select(."display-name" == $m and ."lifecycle-state" == "ACTIVE")][0].id // empty' \
  "$TMP/modelos.json")

passo "1. ${MODELO} pelo ID (OCID) do modelo"
FUNCIONOU=""
if [ -n "$MODELO_ID" ]; then
  testar "$MODELO_ID" "${MODELO} (OCID)" && FUNCIONOU="${MODELO} pelo OCID"
else
  echo "    o modelo ${MODELO} não está na lista"
fi

if [ -z "$FUNCIONOU" ]; then
  passo "2. Outros modelos de chat (pelo nome e pelo OCID)"
  # Alguns da OpenAI, Meta e Cohere, que a API Java também poderia usar.
  while read -r nome id; do
    [ -n "$nome" ] || continue
    if testar "$nome" "$nome"; then FUNCIONOU="$nome pelo nome"; break; fi
    if testar "$id" "$nome (OCID)"; then FUNCIONOU="$nome pelo OCID"; break; fi
  done < <(jq -r --arg m "$MODELO" '[.data.items[]? | select((.capabilities // []) | index("CHAT"))
      | select(."lifecycle-state" == "ACTIVE" and ."display-name" != $m)
      | select(."display-name" | test("^(openai|meta|cohere)\\."))]
    | unique_by(."display-name") | .[0:3][] | ."display-name" + " " + .id' "$TMP/modelos.json")
fi

passo "Resultado"
if [ -n "$FUNCIONOU" ]; then
  echo "    o plano B funciona: ${FUNCIONOU}"
else
  echo "    nenhum modelo respondeu com a assinatura do usuário"
fi

passo "Chaves de assinatura (API signing keys) do seu usuário"
if [ -n "${OCI_CS_USER_OCID:-}" ] && oci iam user api-key list --user-id "$OCI_CS_USER_OCID" > "$TMP/k.json" 2> /dev/null; then
  [ -s "$TMP/k.json" ] || echo '{}' > "$TMP/k.json"
  echo "    $(jq '[.data[]?] | length' "$TMP/k.json") de 3 (o plano B precisa de 1 livre)"
else
  echo "    (não consegui ler)"
fi
