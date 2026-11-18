#!/usr/bin/env bash
# Testa se o modelo do OCI Generative AI responde a chamadas assinadas com o usuário da OCI (IAM), sem API key do
# Generative AI. É o "plano B" para quando o limite api-key-count da conta é 0.
# Não cria nem apaga nada; faz algumas chamadas curtas a cada modelo candidato (custo de frações de centavo de dólar).
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
          ( ($d.choices[0].message.content // $d.chatResponse.choices[0].message.content[0].text
              // $d.chatResponse.text // null) as $t
            | if $t then "    resposta do modelo: \($t | tostring | .[0:120])"
              else "    corpo: \($d | tostring | .[0:300])" end )
        else "    corpo: \($d | tostring | .[0:300])" end )' "$1" 2> /dev/null || head -c 600 "$1"
  jq -e '(.status // "") | startswith("200")' "$1" > /dev/null 2>&1
}

# Chama o modelo $1 na API nativa (formato COHERE para modelos cohere.*, GENERIC para os outros) e, se não for
# Cohere, também no endpoint compatível com OpenAI. Imprime uma linha por chamada e guarda o resumo em $TMP/resumo.
testar() {
  local nome="$1" st1="-" st2="-"
  if [[ "$nome" == cohere.* ]]; then
    jq -n --arg c "$COMP" --arg m "$nome" '{compartmentId: $c, servingMode: {servingType: "ON_DEMAND", modelId: $m},
      chatRequest: {apiFormat: "COHERE", message: "Responda só: ok", maxTokens: 200}}' > "$TMP/corpo2.json"
  else
    jq -n --arg c "$COMP" --arg m "$nome" '{compartmentId: $c, servingMode: {servingType: "ON_DEMAND", modelId: $m},
      chatRequest: {apiFormat: "GENERIC", maxTokens: 200,
        messages: [{role: "USER", content: [{type: "TEXT", text: "Responda só: ok"}]}]}}' > "$TMP/corpo2.json"
  fi
  echo "  - ${nome}, API nativa:"
  oci raw-request --http-method POST --target-uri "${HOST}/20231130/actions/chat" \
    --request-body "$(cat "$TMP/corpo2.json")" > "$TMP/r.json" 2>&1
  if resultado "$TMP/r.json"; then st2="OK"; else st2=$(jq -r '.status // "erro"' "$TMP/r.json" 2> /dev/null | cut -c1-3); fi
  if [[ "$nome" != cohere.* ]]; then
    jq -n --arg m "$nome" \
      '{model: $m, messages: [{role: "user", content: "Responda só: ok"}], max_tokens: 200}' > "$TMP/corpo.json"
    jq -n --arg c "$COMP" '{"opc-compartment-id": $c, "CompartmentId": $c}' > "$TMP/cabecalhos.json"
    echo "  - ${nome}, compatível com OpenAI:"
    oci raw-request --http-method POST --target-uri "${HOST}/openai/v1/chat/completions" \
      --request-body "$(cat "$TMP/corpo.json")" --request-headers "$(cat "$TMP/cabecalhos.json")" > "$TMP/r.json" 2>&1
    if resultado "$TMP/r.json"; then st1="OK"; else st1=$(jq -r '.status // "erro"' "$TMP/r.json" 2> /dev/null | cut -c1-3); fi
  fi
  printf '    %-46s nativa: %-5s compatível OpenAI: %s\n' "$nome" "$st2" "$st1" >> "$TMP/resumo"
}

passo "Modelos de chat em ${REGIAO}"
oci_r generative-ai model-collection list-models --compartment-id "$OCI_TENANCY" --all > "$TMP/modelos.json" 2> /dev/null \
  || echo '{}' > "$TMP/modelos.json"
[ -s "$TMP/modelos.json" ] || echo '{}' > "$TMP/modelos.json"
DISPONIVEIS=$(jq -r '[.data.items[]? | select((.capabilities // []) | index("CHAT")) | select(."lifecycle-state" == "ACTIVE")
  | ."display-name"] | unique | .[]' "$TMP/modelos.json")
echo "    $(printf '%s\n' "$DISPONIVEIS" | grep -c .) modelos de chat na lista"

# Candidatos: o gpt-oss e outros que a API Java poderia usar. Só testa os que aparecem na lista da região.
CANDIDATOS="${CANDIDATOS:-openai.gpt-oss-120b openai.gpt-oss-20b meta.llama-3.3-70b-instruct meta.llama-4-maverick-17b-128e-instruct-fp8 meta.llama-4-scout-17b-16e-instruct meta.llama-3.1-405b-instruct google.gemini-2.5-flash cohere.command-a-03-2025 cohere.command-r-plus-08-2024}"
passo "Chamadas assinadas com o usuário"
: > "$TMP/resumo"
for nome in $CANDIDATOS; do
  if printf '%s\n' "$DISPONIVEIS" | grep -qx "$nome"; then
    testar "$nome"
  else
    printf '    %-46s não está na lista da região\n' "$nome" >> "$TMP/resumo"
  fi
done

passo "Resumo (OK = o modelo respondeu)"
cat "$TMP/resumo"

passo "Chaves de assinatura (API signing keys) do seu usuário"
if [ -n "${OCI_CS_USER_OCID:-}" ] && oci iam user api-key list --user-id "$OCI_CS_USER_OCID" > "$TMP/k.json" 2> /dev/null; then
  [ -s "$TMP/k.json" ] || echo '{}' > "$TMP/k.json"
  echo "    $(jq '[.data[]?] | length' "$TMP/k.json") de 3 (o plano B precisa de 1 livre)"
else
  echo "    (não consegui ler)"
fi
