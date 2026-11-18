#!/usr/bin/env bash
# Testa se o modelo do OCI Generative AI responde a chamadas assinadas com o usuário da OCI (IAM), sem API key do
# Generative AI. É o "plano B" para quando o limite api-key-count da conta é 0.
# Não cria nem apaga nada; faz três chamadas curtas ao modelo (custo de alguns centavos de dólar, no máximo).
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

COMP=$(oci iam compartment list --compartment-id "$OCI_TENANCY" --name "$COMPARTIMENTO" \
  --lifecycle-state ACTIVE --query 'data[0].id' --raw-output 2> /dev/null || true)
[ -n "$COMP" ] && [ "$COMP" != "null" ] || COMP="$OCI_TENANCY"

# Mostra o status HTTP e o começo da resposta do modelo (ou a mensagem de erro).
resultado() {
  jq -r '
    "    status: \(.status // "?")",
    ( .data as $d
      | if ($d | type) == "object" then
          ( ($d.choices[0].message.content // $d.chatResponse.choices[0].message.content[0].text // null) as $t
            | if $t then "    resposta do modelo: \($t | tostring | .[0:120])"
              else "    corpo: \($d | tostring | .[0:300])" end )
        else "    corpo: \($d | tostring | .[0:300])" end )' "$1" 2> /dev/null || head -c 600 "$1"
}

passo "1. Endpoint compatível com OpenAI (/openai/v1/chat/completions), assinado com o usuário"
jq -n --arg m "$MODELO" \
  '{model: $m, messages: [{role: "user", content: "Responda só: ok"}], max_tokens: 200}' > "$TMP/corpo.json"
jq -n --arg c "$COMP" '{"opc-compartment-id": $c, "CompartmentId": $c}' > "$TMP/cabecalhos.json"
oci raw-request --http-method POST --target-uri "${HOST}/openai/v1/chat/completions" \
  --request-body "$(cat "$TMP/corpo.json")" --request-headers "$(cat "$TMP/cabecalhos.json")" > "$TMP/r1.json" 2>&1
resultado "$TMP/r1.json"

passo "2. API nativa do Generative AI (/20231130/actions/chat), assinada com o usuário"
jq -n --arg c "$COMP" --arg m "$MODELO" '{
  compartmentId: $c,
  servingMode: {servingType: "ON_DEMAND", modelId: $m},
  chatRequest: {apiFormat: "GENERIC", maxTokens: 200,
    messages: [{role: "USER", content: [{type: "TEXT", text: "Responda só: ok"}]}]}
}' > "$TMP/corpo2.json"
oci raw-request --http-method POST --target-uri "${HOST}/20231130/actions/chat" \
  --request-body "$(cat "$TMP/corpo2.json")" > "$TMP/r2.json" 2>&1
resultado "$TMP/r2.json"

passo "3. Endpoint compatível com OpenAI sem o cabeçalho de compartimento"
oci raw-request --http-method POST --target-uri "${HOST}/openai/v1/chat/completions" \
  --request-body "$(cat "$TMP/corpo.json")" > "$TMP/r3.json" 2>&1
resultado "$TMP/r3.json"

passo "Chaves de assinatura (API signing keys) do seu usuário"
if [ -n "${OCI_CS_USER_OCID:-}" ] && oci iam user api-key list --user-id "$OCI_CS_USER_OCID" > "$TMP/k.json" 2> /dev/null; then
  echo "    $(jq '[.data[]?] | length' "$TMP/k.json") de 3 (o plano B precisa de 1 livre)"
else
  echo "    (não consegui ler)"
fi
