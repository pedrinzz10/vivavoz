#!/usr/bin/env bash
# Diagnóstico da conta OCI para a IA generativa da VozViva. Só lê: não cria, não altera e não apaga nada.
# Mostra região, compartimentos, API keys do Generative AI, limites e uso do serviço, orçamentos e policies.
# IDs e textos longos aparecem cortados, e o valor das chaves nunca é lido.
# Uso, no Cloud Shell da OCI: ./scripts/diagnosticar-oci.sh   (a saída também fica em ~/diagnostico-oci.txt)
set -uo pipefail

REGIAO="${REGIAO:-sa-saopaulo-1}"
SERVICO="${SERVICO:-ai-generative}"
MODELO="${MODELO:-openai.gpt-oss-120b}"
SAIDA="${HOME}/diagnostico-oci.txt"

: "${OCI_TENANCY:?rode no Cloud Shell da OCI (a variável OCI_TENANCY vem configurada)}"
command -v jq > /dev/null || { echo "jq não encontrado"; exit 1; }

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

secao() { printf '\n==> %s\n' "$*"; }
fim() { printf '%s' "${1: -8}"; }
# Roda um comando do OCI CLI e guarda o JSON em $TMP/r.json; em caso de erro, mostra só o código e a mensagem.
ler() {
  if oci "$@" > "$TMP/r.json" 2> "$TMP/e.txt"; then
    [ -s "$TMP/r.json" ] || echo '{}' > "$TMP/r.json"
    return 0
  fi
  local msg
  msg=$(sed -n '/^{/,$p' "$TMP/e.txt" | jq -r '"\(.code // "erro"): \(.message // "")"' 2> /dev/null)
  [ -n "$msg" ] || msg=$(head -3 "$TMP/e.txt")
  echo "    falhou: ${msg}"
  return 1
}

{
  secao "Conta"
  echo "    tenancy (final): $(fim "$OCI_TENANCY")"
  echo "    região do Cloud Shell: ${OCI_REGION:-?}   região usada: ${REGIAO}"
  if ler iam tenancy get --tenancy-id "$OCI_TENANCY"; then
    jq -r '"    nome: \(.data.name)   região de origem: \(.data."home-region-key")"' "$TMP/r.json"
  fi
  if ler iam region-subscription list --tenancy-id "$OCI_TENANCY"; then
    jq -r '"    regiões assinadas: " + ([.data[] | ."region-name" + "(" + .status + ")"] | join(", "))' "$TMP/r.json"
  fi

  secao "Compartimentos (nome, estado, final do id)"
  echo "    (raiz)  ACTIVE  $(fim "$OCI_TENANCY")"
  COMPS="$OCI_TENANCY (raiz)"
  if ler iam compartment list --compartment-id "$OCI_TENANCY" --compartment-id-in-subtree true --all; then
    jq -r '.data[] | "    \(.name)  \(."lifecycle-state")  \(.id[-8:])"' "$TMP/r.json"
    COMPS+=$'\n'$(jq -r '.data[] | select(."lifecycle-state" == "ACTIVE") | .id + " " + .name' "$TMP/r.json")
  fi

  secao "Modelo ${MODELO} em ${REGIAO}"
  if ler --region "$REGIAO" generative-ai model-collection list-models --compartment-id "$OCI_TENANCY" --all; then
    jq -r --arg m "$MODELO" '[.data.items[]? | select(."display-name" == $m)] |
      if length == 0 then "    não encontrado" else .[] | "    \(."lifecycle-state")  capacidades: \(.capabilities // [] | join(","))" end' \
      "$TMP/r.json"
  fi

  secao "API keys do Generative AI em ${REGIAO} (nome, estado, compartimento, expiração)"
  TOTAL=0
  while read -r comp_id comp_nome; do
    [ -n "$comp_id" ] || continue
    if ler --region "$REGIAO" generative-ai api-key-collection list-api-keys --compartment-id "$comp_id" --all; then
      jq -r --arg c "$comp_nome" '.data.items[]? |
        "    \(."display-name")  \(."lifecycle-state")  \($c)  \([.keys[]? | ."time-expiry" // empty] | join(","))"' "$TMP/r.json"
      n=$(jq '[.data.items[]?] | length' "$TMP/r.json")
      TOTAL=$((TOTAL + n))
    else
      echo "    (compartimento ${comp_nome})"
    fi
  done <<< "$COMPS"
  echo "    total: ${TOTAL}"

  secao "Limites do serviço ${SERVICO} (nome, escopo, valor)"
  if ler limits value list --service-name "$SERVICO" --compartment-id "$OCI_TENANCY" --all; then
    jq -r '.data[] | "    \(.name)  \(."scope-type")\(if ."availability-domain" then " " + ."availability-domain" else "" end)  = \(.value)"' \
      "$TMP/r.json" | sort
    LIMITES_CHAVE=$(jq -r '.data[] | select(.name | test("key"; "i")) | .name' "$TMP/r.json" | sort -u)
  fi

  secao "Uso e disponibilidade dos limites de chave"
  for limite in ${LIMITES_CHAVE:-api-key-count}; do
    if ler --region "$REGIAO" limits resource-availability get --service-name "$SERVICO" --limit-name "$limite" \
        --compartment-id "$OCI_TENANCY"; then
      jq -r --arg l "$limite" '"    \($l): usado \(.data.used // "?")  disponível \(.data.available // "?")"' "$TMP/r.json"
    else
      echo "    (limite ${limite})"
    fi
  done

  secao "Definições dos limites de chave (descrição)"
  if ler limits definition list --service-name "$SERVICO" --compartment-id "$OCI_TENANCY" --all; then
    jq -r '.data[] | select(.name | test("key"; "i")) | "    \(.name): \(.description // "")  (pode pedir aumento: \(."is-eligible-for-limit-increase"))"' \
      "$TMP/r.json"
  fi

  secao "Quotas que podem zerar o serviço"
  if ler limits quota list --compartment-id "$OCI_TENANCY" --all; then
    jq -r '.data[]? | "    \(.name)  \(."lifecycle-state")"' "$TMP/r.json"
    [ "$(jq '[.data[]?] | length' "$TMP/r.json")" = 0 ] && echo "    nenhuma"
  fi

  secao "Orçamentos"
  if ler budgets budget budget list --compartment-id "$OCI_TENANCY" --all; then
    jq -r '.data[]? | "    \(."display-name")  US$ \(.amount)  \(."reset-period")  gasto atual: \(."actual-spend" // 0)"' "$TMP/r.json"
    [ "$(jq '[.data[]?] | length' "$TMP/r.json")" = 0 ] && echo "    nenhum"
  fi

  secao "Policies que citam generative-ai (raiz)"
  if ler iam policy list --compartment-id "$OCI_TENANCY" --all; then
    jq -r '.data[] | select([.statements[] | test("generative"; "i")] | any) | "    \(.name):", (.statements[] | select(test("generative"; "i")) | "      " + .)' \
      "$TMP/r.json"
  fi

  secao "Seu usuário"
  if [ -n "${OCI_CS_USER_OCID:-}" ] && ler iam user list-groups --user-id "$OCI_CS_USER_OCID" --compartment-id "$OCI_TENANCY" 2> /dev/null; then
    jq -r '"    grupos: " + ([.data[].name] | join(", "))' "$TMP/r.json"
  else
    echo "    (não consegui ler os grupos do usuário)"
  fi
} 2>&1 | sed -E 's/ocid1\.[a-z0-9._-]+/<ocid>/g' | tee "$SAIDA"

printf '\nSaída salva em %s\n' "$SAIDA"
