#!/usr/bin/env bash
# Apaga na OCI o que scripts/provisionar-oci.sh criou, para recriar do zero ou desligar a IA: a API key do
# Generative AI ou, no plano B, a chave de assinatura cadastrada no usuário (e a chave privada local), e o orçamento.
# O compartimento vozviva fica (vazio, não custa nada) e é reaproveitado na próxima execução do provisionar-oci.sh.
# Uso, no Cloud Shell da OCI: ./scripts/remover-oci.sh  (ou CONFIRMAR=sim para não perguntar).
# Procura as chaves em todos os compartimentos e apaga só as vozviva-api; APAGAR_TODAS=sim apaga também as outras.
set -euo pipefail

REGIAO="${REGIAO:-sa-saopaulo-1}"
NOME_CHAVE="vozviva-api"
NOME_ORCAMENTO="vozviva-ia"
ARQUIVO_ENV="${HOME}/.vozviva/oci-genai.env"
CHAVE_ASSINATURA="${HOME}/.vozviva/oci-assinatura.pem"

: "${OCI_TENANCY:?rode no Cloud Shell da OCI (a variável OCI_TENANCY vem configurada)}"
oci_r() { oci --region "$REGIAO" "$@"; }

# Se uma listagem falhar, para sem apagar nada: apagar só o arquivo local deixaria a chave remota sem o valor.
ERRO=$(mktemp)
trap 'rm -f "$ERRO"' EXIT
falhou() { echo "Não consegui listar $1: $(cat "$ERRO")"; echo "Nada foi apagado."; exit 1; }

# O limite de API keys vale para a conta inteira, então procura em todos os compartimentos (o raiz e os de baixo).
LISTA=$(oci iam compartment list --compartment-id "$OCI_TENANCY" --compartment-id-in-subtree true \
  --lifecycle-state ACTIVE --all 2> "$ERRO") || falhou "os compartimentos"
COMPARTIMENTOS=$(printf '%s' "${LISTA:-{\}}" | jq -r --arg raiz "$OCI_TENANCY" \
  '([{id: $raiz, name: "(raiz)"}] + (.data // [])) | .[] | .id + " " + .name')

# Cada linha, separada por tab: id da chave, nome, estado, compartimento
TODAS=""
while read -r comp_id comp_nome; do
  LISTA=$(oci_r generative-ai api-key-collection list-api-keys --compartment-id "$comp_id" --all 2> "$ERRO") \
    || falhou "as API keys do compartimento ${comp_nome}"
  # O $( ) tira a quebra de linha do fim; ela é recolocada para a chave seguinte não grudar nesta.
  TODAS+="$(printf '%s' "${LISTA:-{\}}" | jq -r --arg comp "$comp_nome" \
    '.data.items[]? | [.id, ."display-name", ."lifecycle-state", $comp] | join("\t")' | tr -d '\r')"$'\n'
done <<< "$COMPARTIMENTOS"
TODAS=$(printf '%s' "$TODAS" | sed '/^$/d')

# Apaga só as chaves da VozViva; com APAGAR_TODAS=sim, apaga também as outras (de testes antigos, por exemplo).
CHAVES=$(printf '%s\n' "$TODAS" | awk -F '\t' -v nome="$NOME_CHAVE" -v todas="${APAGAR_TODAS:-}" \
  'NF && $3 != "DELETED" && $3 != "DELETING" && (todas == "sim" || $2 == nome) {print $1}')

LISTA=$(oci budgets budget budget list --compartment-id "$OCI_TENANCY" --all 2> "$ERRO") || falhou "os orçamentos"
ORC=$(printf '%s' "${LISTA:-{\}}" | jq -r --arg nome "$NOME_ORCAMENTO" \
  '[.data[]? | select(."display-name" == $nome) | .id][0] // empty')

# Chave de assinatura do plano B (provisionar-oci.sh): acha no usuário a chave pública que corresponde ao PEM local.
FINGERPRINT=""
if [ -f "$CHAVE_ASSINATURA" ] && [ -n "${OCI_CS_USER_OCID:-}" ]; then
  PUBLICA=$(openssl rsa -in "$CHAVE_ASSINATURA" -pubout 2> /dev/null | grep -v -- '-----' | tr -d '[:space:]')
  LISTA=$(oci iam user api-key list --user-id "$OCI_CS_USER_OCID" --all 2> "$ERRO") || falhou "as chaves de assinatura do usuário"
  FINGERPRINT=$(printf '%s' "${LISTA:-{\}}" | jq -r --arg p "$PUBLICA" '[.data[]? | select(."lifecycle-state" != "DELETED")
    | select((."key-value" // "" | gsub("-----[A-Z ]+-----"; "") | gsub("[[:space:]]"; "")) == $p) | .fingerprint][0] // empty')
fi

echo "API keys do Generative AI na conta (nome, estado, compartimento):"
if [ -n "$TODAS" ]; then
  printf '%s\n' "$TODAS" | awk -F '\t' '{print "    " $2 "  " $3 "  " $4}'
else
  echo "    nenhuma"
fi
echo
echo "Vai apagar:"
echo "  - API keys: $(printf '%s' "$CHAVES" | grep -c . || true)$([ "${APAGAR_TODAS:-}" = sim ] && echo ' (todas)' || echo " (só as ${NOME_CHAVE})")"
echo "  - orçamento ${NOME_ORCAMENTO}: $([ -n "$ORC" ] && echo sim || echo 'não existe')"
echo "  - arquivo ${ARQUIVO_ENV}: $([ -f "$ARQUIVO_ENV" ] && echo sim || echo 'não existe')"
echo "  - chave de assinatura do usuário: $([ -n "$FINGERPRINT" ] && echo "sim (fingerprint ${FINGERPRINT})" || echo 'não cadastrada')"
echo "  - chave privada ${CHAVE_ASSINATURA}: $([ -f "$CHAVE_ASSINATURA" ] && echo sim || echo 'não existe')"
OUTRAS=$(printf '%s\n' "$TODAS" | awk -F '\t' -v nome="$NOME_CHAVE" 'NF && $3 != "DELETED" && $3 != "DELETING" && $2 != nome' | grep -c . || true)
if [ "${APAGAR_TODAS:-}" != sim ] && [ "$OUTRAS" -gt 0 ]; then
  echo "  Há ${OUTRAS} outra(s) chave(s) ativa(s), que contam no limite da conta. Se forem de testes antigos, rode"
  echo "  APAGAR_TODAS=sim ./scripts/remover-oci.sh"
fi

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
if [ -n "$FINGERPRINT" ]; then
  oci iam user api-key delete --user-id "$OCI_CS_USER_OCID" --fingerprint "$FINGERPRINT" --force > /dev/null
  echo "    chave de assinatura removida do usuário"
fi
rm -f "$ARQUIVO_ENV" "$CHAVE_ASSINATURA" "${CHAVE_ASSINATURA%.pem}-publica.pem"
echo "Pronto. Para criar de novo: ORCAMENTO_EMAIL=voce@fiap.com.br ./scripts/provisionar-oci.sh"
