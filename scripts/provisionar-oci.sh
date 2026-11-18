#!/usr/bin/env bash
# Cria na OCI o que a API precisa para a IA generativa (OCI Generative AI, endpoint compatível com OpenAI):
#   - compartimento "vozviva" (separa a chave e os custos);
#   - API key do Generative AI, válida até a data de EXPIRACAO. Se a conta não permitir API keys (limite
#     api-key-count = 0, comum em contas novas), cria no lugar uma chave de assinatura (API signing key) para o
#     seu usuário, e a API Java assina as chamadas com ela (plano B; force com AUTENTICACAO=usuario);
#   - orçamento mensal no compartimento, com alertas por e-mail em 50% e 100%;
#   - uma chamada de teste ao modelo.
#
# Rode no Cloud Shell da OCI (o CLI já vem autenticado), na região do modelo (padrão sa-saopaulo-1):
#   ORCAMENTO_EMAIL=voce@fiap.com.br ./scripts/provisionar-oci.sh
#
# Idempotente: o que já existe é reaproveitado. A chave criada fica só em ~/.vozviva/oci-genai.env (permissão 600);
# ela não aparece na tela. Para levar à Azure, veja as instruções no fim da execução.
#
# Opcionais: REGIAO (padrão sa-saopaulo-1), COMPARTIMENTO (padrão vozviva), MODELO (padrão meta.llama-3.3-70b-instruct),
# EXPIRACAO (padrão 2026-12-31T23:59:59Z), ORCAMENTO_USD (padrão 300, os créditos da conta), ORCAMENTO_EMAIL (sem ele, não cria orçamento).
set -euo pipefail

REGIAO="${REGIAO:-sa-saopaulo-1}"
COMPARTIMENTO="${COMPARTIMENTO:-vozviva}"
MODELO="${MODELO:-meta.llama-3.3-70b-instruct}"
AUTENTICACAO="${AUTENTICACAO:-}"
EXPIRACAO="${EXPIRACAO:-2026-12-31T23:59:59Z}"
ORCAMENTO_USD="${ORCAMENTO_USD:-300}"
ORCAMENTO_EMAIL="${ORCAMENTO_EMAIL:-}"
NOME_CHAVE="vozviva-api"
NOME_ORCAMENTO="vozviva-ia"
BASE_URL="https://inference.generativeai.${REGIAO}.oci.oraclecloud.com/openai/v1"
PASTA_SEGREDOS="${HOME}/.vozviva"
ARQUIVO_ENV="${PASTA_SEGREDOS}/oci-genai.env"
CHAVE_ASSINATURA="${PASTA_SEGREDOS}/oci-assinatura.pem"

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

mkdir -p "$PASTA_SEGREDOS" && chmod 700 "$PASTA_SEGREDOS"
# Já configurado com a assinatura do usuário numa execução anterior: continua assim.
grep -q '^export OCI_GENAI_USER_OCID=' "$ARQUIVO_ENV" 2> /dev/null && AUTENTICACAO=usuario
EXISTENTE=0
if [ "$AUTENTICACAO" != usuario ]; then
passo "API key ${NOME_CHAVE} (válida até ${EXPIRACAO})"
# Um compartimento recém-criado pode levar alguns segundos para ficar visível ao Generative AI: tenta algumas vezes
# e, se a listagem não funcionar, para com o erro em vez de supor que a chave existe (ou não existe).
ERRO_LISTA=$(mktemp)
for tentativa in 1 2 3 4 5 6; do
  if LISTA=$(oci_r generative-ai api-key-collection list-api-keys --compartment-id "$COMP" --all 2> "$ERRO_LISTA"); then
    break
  fi
  [ "$tentativa" = 6 ] && { echo "    não consegui listar as API keys:"; sed 's/^/      /' "$ERRO_LISTA"; exit 1; }
  sleep 20
done
rm -f "$ERRO_LISTA"
# Sem chaves, o CLI não imprime nada.
EXISTENTE=$(printf '%s' "${LISTA:-{\}}" | jq --arg nome "$NOME_CHAVE" \
  '[.data.items[]? | select(."display-name" == $nome and ."lifecycle-state" == "ACTIVE")] | length')
if [ "$EXISTENTE" -gt 0 ]; then
  reaproveitado "$NOME_CHAVE"
  [ -f "$ARQUIVO_ENV" ] || echo "    o valor da chave só aparece na criação; sem ${ARQUIVO_ENV}, rode ./scripts/remover-oci.sh e depois este script de novo"
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
    if grep -q LimitExceeded "${RESPOSTA}.erro"; then
      echo "    a conta não permite mais API keys do Generative AI (limite api-key-count); usando a assinatura do usuário"
      rm -f "$RESPOSTA" "${RESPOSTA}.erro"
      AUTENTICACAO=usuario
      break
    fi
    [ "$tentativa" = 6 ] && { cat "${RESPOSTA}.erro"; exit 1; }
    sleep 20
  done
  rm -f "${RESPOSTA}.erro"
fi
if [ "$AUTENTICACAO" != usuario ] && [ "$EXISTENTE" -eq 0 ]; then
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
fi

if [ "$AUTENTICACAO" = usuario ]; then
  passo "Chave de assinatura do seu usuário (plano B, sem API key do Generative AI)"
  USUARIO="${OCI_CS_USER_OCID:-}"
  [ -n "$USUARIO" ] || { echo "    não achei o OCID do usuário (OCI_CS_USER_OCID); rode no Cloud Shell da OCI"; exit 1; }
  umask 077
  if [ ! -f "$CHAVE_ASSINATURA" ]; then
    openssl genrsa -out "$CHAVE_ASSINATURA" 2048 2> /dev/null
    criado "chave RSA em ${CHAVE_ASSINATURA}"
  else
    reaproveitado "chave RSA em ${CHAVE_ASSINATURA}"
  fi
  openssl rsa -in "$CHAVE_ASSINATURA" -pubout -out "${CHAVE_ASSINATURA%.pem}-publica.pem" 2> /dev/null
  # O fingerprint vem da própria OCI (o Cloud Shell roda em modo FIPS, que bloqueia o MD5 do openssl): procura a
  # chave pública entre as do usuário e, se não estiver lá, cadastra e lê o fingerprint da resposta.
  PUBLICA=$(grep -v -- '-----' "${CHAVE_ASSINATURA%.pem}-publica.pem" | tr -d '\r\n')
  FINGERPRINT=$(oci iam user api-key list --user-id "$USUARIO" --all 2> /dev/null | jq -r --arg p "$PUBLICA" \
    '[.data[]? | select(."lifecycle-state" != "DELETED")
      | select((."key-value" // "" | gsub("-----[A-Z ]+-----"; "") | gsub("[[:space:]]"; "")) == $p) | .fingerprint][0] // empty' 2> /dev/null || true)
  if [ -n "$FINGERPRINT" ]; then
    reaproveitado "chave pública no usuário (fingerprint ${FINGERPRINT})"
  else
    FINGERPRINT=$(oci iam user api-key upload --user-id "$USUARIO" --key-file "${CHAVE_ASSINATURA%.pem}-publica.pem" \
      | jq -r '.data.fingerprint // empty')
    [ -n "$FINGERPRINT" ] || { echo "    não consegui cadastrar a chave pública no usuário"; exit 1; }
    criado "chave pública no usuário (fingerprint ${FINGERPRINT})"
  fi
  {
    printf 'export OCI_GENAI_TENANCY_OCID=%q\n' "$OCI_TENANCY"
    printf 'export OCI_GENAI_USER_OCID=%q\n' "$USUARIO"
    printf 'export OCI_GENAI_FINGERPRINT=%q\n' "$FINGERPRINT"
    printf 'export OCI_GENAI_COMPARTMENT_OCID=%q\n' "$COMP"
    printf 'export OCI_GENAI_PRIVATE_KEY=%q\n' "$(base64 -w 0 "$CHAVE_ASSINATURA")"
    printf 'export OCI_GENAI_BASE_URL=%q\n' "$BASE_URL"
    printf 'export OCI_GENAI_MODEL=%q\n' "$MODELO"
  } > "$ARQUIVO_ENV"
  chmod 600 "$ARQUIVO_ENV"
  echo "    valores salvos em ${ARQUIVO_ENV} (a chave privada vai em Base64, numa linha só)"
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

passo "Teste: uma chamada curta ao modelo ${MODELO}"
if [ -f "$ARQUIVO_ENV" ]; then
  # shellcheck disable=SC1090
  source "$ARQUIVO_ENV"
  CORPO="{\"model\":\"${MODELO}\",\"messages\":[{\"role\":\"user\",\"content\":\"Responda só: ok\"}],\"max_tokens\":200}"
  if [ -n "${OCI_GENAI_API_KEY:-}" ]; then
    STATUS=$(curl -s -o /dev/null -w '%{http_code}' -m 60 "${BASE_URL}/chat/completions" \
      -H "Authorization: Bearer ${OCI_GENAI_API_KEY}" -H 'Content-Type: application/json' -d "$CORPO")
  else
    # Assina com curl exatamente como o AssinadorRequisicaoOci da API Java (mesmos cabeçalhos, na mesma ordem),
    # usando a chave privada lida do próprio oci-genai.env: confere o formato da assinatura e o arquivo que vai
    # para a Azure.
    TMP_TESTE=$(mktemp -d)
    printf '%s' "$OCI_GENAI_PRIVATE_KEY" | base64 -d > "$TMP_TESTE/chave.pem"
    printf '%s' "$CORPO" > "$TMP_TESTE/corpo.json"
    HOST_IA=$(printf '%s' "$BASE_URL" | sed -E 's#^https://([^/]+).*#\1#')
    CAMINHO="$(printf '%s' "$BASE_URL" | sed -E 's#^https://[^/]+##')/chat/completions"
    TIPO="application/json"
    TAMANHO=$(wc -c < "$TMP_TESTE/corpo.json" | tr -d ' ')
    RESUMO=$(openssl dgst -sha256 -binary "$TMP_TESTE/corpo.json" | base64 -w 0)
    # Uma chave recém-cadastrada leva até alguns minutos para valer.
    for tentativa in 1 2 3 4 5 6 7 8; do
      DATA=$(LC_ALL=C date -u '+%a, %d %b %Y %H:%M:%S GMT')
      printf 'date: %s\n(request-target): post %s\nhost: %s\ncontent-length: %s\ncontent-type: %s\nx-content-sha256: %s' \
        "$DATA" "$CAMINHO" "$HOST_IA" "$TAMANHO" "$TIPO" "$RESUMO" > "$TMP_TESTE/assinar.txt"
      ASSINATURA=$(openssl dgst -sha256 -sign "$TMP_TESTE/chave.pem" "$TMP_TESTE/assinar.txt" | base64 -w 0)
      CHAVE_ID="${OCI_GENAI_TENANCY_OCID}/${OCI_GENAI_USER_OCID}/${OCI_GENAI_FINGERPRINT}"
      STATUS=$(curl -s -o "$TMP_TESTE/resposta.json" -w '%{http_code}' -m 60 -X POST "https://${HOST_IA}${CAMINHO}" \
        -H "date: ${DATA}" -H "host: ${HOST_IA}" -H "content-length: ${TAMANHO}" -H "content-type: ${TIPO}" \
        -H "x-content-sha256: ${RESUMO}" -H "opc-compartment-id: ${OCI_GENAI_COMPARTMENT_OCID}" \
        -H "authorization: Signature version=\"1\",headers=\"date (request-target) host content-length content-type x-content-sha256\",keyId=\"${CHAVE_ID}\",algorithm=\"rsa-sha256\",signature=\"${ASSINATURA}\"" \
        --data-binary @"$TMP_TESTE/corpo.json")
      [ "$STATUS" = 401 ] && [ "$tentativa" != 8 ] && { sleep 20; continue; }
      break
    done
    if [ "$STATUS" = 200 ]; then
      echo "    resposta: $(jq -r '.choices[0].message.content // empty' "$TMP_TESTE/resposta.json" 2> /dev/null | head -c 80)"
    else
      echo "    corpo da resposta: $(head -c 300 "$TMP_TESTE/resposta.json")"
    fi
    rm -rf "$TMP_TESTE"
  fi
  case "$STATUS" in
    200) echo "    o modelo respondeu (HTTP 200)" ;;
    401) echo "    HTTP 401: a credencial não foi aceita (se a chave acabou de ser cadastrada, espere uns minutos e rode de novo)" ;;
    403|404) echo "    HTTP ${STATUS}: falta permissão (policy) ou o modelo ${MODELO} não está disponível sob demanda em ${REGIAO}" ;;
    *) echo "    HTTP ${STATUS:-000}: confira a região, o modelo e se a conta tem créditos ou é Pay As You Go" ;;
  esac
else
  echo "    pulado: sem ${ARQUIVO_ENV}"
fi

cat <<EOF

Pronto. Para levar as credenciais da IA para a Azure (sem digitar valor nenhum):
  1. aqui, mostre o arquivo em Base64:           base64 -w 64 ${ARQUIVO_ENV}
  2. no Cloud Shell da Azure, rode               sed '/^$/q' | base64 -d > ~/oci-genai.env
     cole o bloco do passo 1, aperte Enter numa linha vazia e depois carregue com   . ~/oci-genai.env
  3. rode ./scripts/provisionar-azure.sh no mesmo Cloud Shell.
Endpoint: ${BASE_URL}  modelo: ${MODELO}
Para apagar a API key e o orçamento (e criar de novo): ./scripts/remover-oci.sh
EOF
