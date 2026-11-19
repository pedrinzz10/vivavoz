#!/usr/bin/env bash
# Prepara a demonstração do vídeo pitch de Java na API publicada: cria a cidadã de demonstração com o perfil de
# emergência e um contato, e mostra o token para colar no Authorize do Swagger. Rode antes de gravar (fora do vídeo).
# Uso: bash scripts/preparar-pitch.sh [URL da API]   (padrão: a API da equipe na Azure)
set -euo pipefail

API="${1:-https://app-vozviva-rm562312.azurewebsites.net}"
API="${API%/}/api/v1"
EMAIL="maria.pitch.$(date +%s)@vozviva.dev"
SENHA="pitch2026vozviva"

chamar() {
  local metodo="$1" caminho="$2" corpo="${3:-}"
  local args=(-sS -X "$metodo" -H "Content-Type: application/json; charset=utf-8" --fail-with-body)
  [ -n "${TOKEN:-}" ] && args+=(-H "Authorization: Bearer ${TOKEN}")
  [ -n "$corpo" ] && args+=(-d "$corpo")
  curl "${args[@]}" "${API}${caminho}"
}

campo() {
  python3 -c "import json,sys; print(json.load(sys.stdin)['$1'])"
}

echo "Criando a cidadã de demonstração..."
chamar POST /auth/cadastro "{\"nome\":\"Maria da Silva\",\"email\":\"${EMAIL}\",\"senha\":\"${SENHA}\",\"papel\":\"CIDADAO\",\"consentimento\":true}" > /dev/null
TOKEN="$(chamar POST /auth/login "{\"email\":\"${EMAIL}\",\"senha\":\"${SENHA}\"}" | campo accessToken)"

PERFIL_ID="$(chamar POST /perfis-emergencia '{"mobilidade":"CADEIRANTE","condicoesSaude":"Diabetes tipo 2","medicamentos":"Metformina 850 mg","observacoes":"Mora sozinha"}' | campo id)"
chamar POST "/perfis-emergencia/${PERFIL_ID}/contatos" '{"nome":"Ana Souza","telefone":"(11) 91234-5678","parentesco":"Filha","prioridade":1}' > /dev/null

echo
echo "Pronto. E-mail: ${EMAIL}   Senha: ${SENHA}"
echo "Token (cole em Authorize no Swagger; vale 2 horas):"
echo
echo "${TOKEN}"
