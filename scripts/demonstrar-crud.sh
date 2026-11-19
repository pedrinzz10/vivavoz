#!/usr/bin/env bash
# Demonstração do CRUD para o vídeo de DevOps: chama a API publicada na Azure e, depois de cada operação, confere o
# resultado direto no Azure SQL com SELECT (a prova que a disciplina pede, em vez do GET).
#
# Três tabelas: usuarios, perfis_emergencia e contatos_emergencia. Cria uma pessoa de teste, lê, altera e apaga tudo
# no fim (a conta por último, porque apagar a conta apaga o perfil e os contatos).
#
# Uso, no Cloud Shell da Azure, depois do provisionar-azure.sh e do primeiro deploy da pipeline:
#   ./scripts/demonstrar-crud.sh            (pausa a cada passo: aperte Enter para seguir)
#   PAUSA=nao ./scripts/demonstrar-crud.sh  (sem pausas)
# A senha do administrador do banco é lida do Key Vault e não aparece na tela.
set -euo pipefail

SUFIXO="${SUFIXO:-rm562312}"
SUFIXO_LIMPO="$(echo "$SUFIXO" | tr '[:upper:]' '[:lower:]' | tr -cd '[:alnum:]')"
LOCALIZACAO="${LOCALIZACAO:-brazilsouth}"
SQL_LOCALIZACAO="${SQL_LOCALIZACAO:-chilecentral}"
GRUPO="${GRUPO:-rg-vozviva}"
API="https://app-vozviva-${SUFIXO_LIMPO}.azurewebsites.net/api/v1"
# Mesma regra do provisionar-azure.sh: com o banco fora da região das APIs, a região entra no nome.
SQL_SERVIDOR="sql-vozviva-${SUFIXO_LIMPO}"
[ "$SQL_LOCALIZACAO" != "$LOCALIZACAO" ] && SQL_SERVIDOR="${SQL_SERVIDOR}-${SQL_LOCALIZACAO}"
KEYVAULT="kv-vozviva-${SUFIXO_LIMPO}"
# E-mail novo a cada execução: rodar de novo não esbarra numa conta que já existe.
EMAIL="maria.video.$(date +%H%M%S)@vozviva.dev"
SENHA="girassol2026"

command -v jq > /dev/null || { echo "jq não encontrado"; exit 1; }
if ! command -v sqlcmd > /dev/null 2>&1; then
  export PATH="${HOME}/bin:${PATH}"
  if ! command -v sqlcmd > /dev/null 2>&1; then
    mkdir -p "${HOME}/bin"
    curl -fsSL "https://github.com/microsoft/go-sqlcmd/releases/download/v1.10.0/sqlcmd-linux-amd64.tar.bz2" \
      | tar -xj -C "${HOME}/bin" sqlcmd
  fi
fi

# A resposta do login traz o token: o arquivo temporário só pode ser lido por você e é apagado no fim.
umask 077
ARQUIVO_RESPOSTA="$(mktemp)"
trap 'rm -f "$ARQUIVO_RESPOSTA"' EXIT

titulo() { printf '\n\033[1;34m==> %s\033[0m\n' "$*"; }
pausa() { [ "${PAUSA:-sim}" = nao ] || read -r -p "    (Enter para continuar) " _; }

# Chama a API e mostra o status HTTP e a resposta formatada. A resposta fica em $RESPOSTA.
chamar() {
  local metodo="$1" caminho="$2" corpo="${3:-}"
  local args=(-s -o "$ARQUIVO_RESPOSTA" -w '%{http_code}' -X "$metodo")
  [ -n "${TOKEN:-}" ] && args+=(-H "Authorization: Bearer ${TOKEN}")
  [ -n "$corpo" ] && args+=(-H "Content-Type: application/json" -d "$corpo")
  echo "    ${metodo} ${caminho}"
  [ -n "$corpo" ] && echo "    corpo: ${corpo}"
  local status
  status=$(curl "${args[@]}" "${API}${caminho}")
  RESPOSTA=$(cat "$ARQUIVO_RESPOSTA" 2> /dev/null || true)
  echo "    HTTP ${status}"
  [ -n "$RESPOSTA" ] && jq -C . <<< "$RESPOSTA" 2> /dev/null | sed 's/^/    /' || true
  case "$status" in 2*) ;; *) echo "    a chamada falhou; confira a API e rode de novo" >&2; exit 1 ;; esac
}

# Roda um SELECT no Azure SQL e mostra o resultado.
selecionar() {
  echo "    SQL> $1"
  SQLCMDPASSWORD="$SENHA_SQL" sqlcmd -S "tcp:${SQL_SERVIDOR}.database.windows.net,1433" -d vozviva \
    -U vozvivaadmin -W -s " | " -Q "SET NOCOUNT ON; $1" | sed 's/^/    /'
}

titulo "Preparação: senha do banco (do Key Vault) e acesso do Cloud Shell ao Azure SQL"
SENHA_SQL="$(az keyvault secret show --vault-name "$KEYVAULT" -n sql-admin-senha --query value -o tsv)"
MEU_IP="$(curl -fsS https://api.ipify.org)"
az sql server firewall-rule create -g "$GRUPO" -s "$SQL_SERVIDOR" -n DemonstracaoCrud \
  --start-ip-address "$MEU_IP" --end-ip-address "$MEU_IP" -o none
echo "    API: ${API}"
echo "    banco: ${SQL_SERVIDOR} / vozviva (senha lida do ${KEYVAULT}, sem aparecer)"

# Apaga as pessoas de teste de execuções anteriores desta demonstração (e o que depende delas), sem mostrar.
SQLCMDPASSWORD="$SENHA_SQL" sqlcmd -S "tcp:${SQL_SERVIDOR}.database.windows.net,1433" -d vozviva -U vozvivaadmin -b -Q "
DELETE c FROM contatos_emergencia c JOIN perfis_emergencia p ON p.id = c.perfil_id
  JOIN usuarios u ON u.id = p.cidadao_id WHERE u.email LIKE 'maria.video.%@vozviva.dev';
DELETE p FROM perfis_emergencia p JOIN usuarios u ON u.id = p.cidadao_id WHERE u.email LIKE 'maria.video.%@vozviva.dev';
DELETE FROM usuarios WHERE email LIKE 'maria.video.%@vozviva.dev';" > /dev/null

titulo "O banco antes do CRUD: tabelas criadas pelo Flyway, locais carregados e usuários de teste"
selecionar "SELECT COUNT(*) AS locais FROM pontos_atendimento;"
selecionar "SELECT email, papel FROM usuarios;"
selecionar "SELECT COUNT(*) AS perfis FROM perfis_emergencia;"
pausa

# ---------- usuarios ----------
titulo "usuarios: CREATE (cadastro)"
chamar POST /auth/cadastro "{\"nome\":\"Maria da Silva\",\"email\":\"${EMAIL}\",\"senha\":\"${SENHA}\",\"papel\":\"CIDADAO\",\"consentimento\":true}"
selecionar "SELECT id, nome, email, papel, consentimento_em FROM usuarios WHERE email = '${EMAIL}';"
pausa

titulo "Login: a API devolve o token JWT usado nas próximas chamadas"
chamar POST /auth/login "{\"email\":\"${EMAIL}\",\"senha\":\"${SENHA}\"}" > /dev/null
TOKEN="$(jq -r .accessToken <<< "$RESPOSTA")"
echo "    token recebido (${#TOKEN} caracteres)"

titulo "usuarios: READ"
chamar GET /auth/eu
selecionar "SELECT id, nome, email, papel FROM usuarios WHERE email = '${EMAIL}';"
pausa

titulo "usuarios: UPDATE (nome)"
chamar PUT /auth/eu '{"nome":"Maria da Silva Souza"}'
selecionar "SELECT id, nome, email FROM usuarios WHERE email = '${EMAIL}';"
pausa

# ---------- perfis_emergencia ----------
titulo "perfis_emergencia: CREATE"
chamar POST /perfis-emergencia '{"mobilidade":"CADEIRANTE","condicoesSaude":"Hipertens\u00e3o","medicamentos":"Losartana 50 mg","observacoes":"Mora sozinha"}'
PERFIL_ID="$(jq -r .id <<< "$RESPOSTA")"
selecionar "SELECT id, cidadao_id, mobilidade, medicamentos, atualizado_em FROM perfis_emergencia WHERE id = ${PERFIL_ID};"
pausa

titulo "perfis_emergencia: READ"
chamar GET /perfis-emergencia/meu
selecionar "SELECT id, cidadao_id, mobilidade, condicoes_saude, medicamentos FROM perfis_emergencia WHERE id = ${PERFIL_ID};"
pausa

titulo "perfis_emergencia: UPDATE (mobilidade e remédio)"
chamar PUT "/perfis-emergencia/${PERFIL_ID}" '{"mobilidade":"ANDADOR","condicoesSaude":"Hipertens\u00e3o","medicamentos":"Losartana 100 mg","observacoes":"Mora sozinha"}'
selecionar "SELECT id, mobilidade, medicamentos, atualizado_em FROM perfis_emergencia WHERE id = ${PERFIL_ID};"
pausa

# ---------- contatos_emergencia ----------
titulo "contatos_emergencia: CREATE"
chamar POST "/perfis-emergencia/${PERFIL_ID}/contatos" '{"nome":"Ana Souza","telefone":"(11) 91234-5678","parentesco":"Filha","prioridade":1}'
CONTATO_ID="$(jq -r .id <<< "$RESPOSTA")"
selecionar "SELECT id, perfil_id, nome, telefone, prioridade FROM contatos_emergencia WHERE perfil_id = ${PERFIL_ID};"
pausa

titulo "contatos_emergencia: READ"
chamar GET "/perfis-emergencia/${PERFIL_ID}/contatos"
selecionar "SELECT id, perfil_id, nome, telefone, parentesco, prioridade FROM contatos_emergencia WHERE perfil_id = ${PERFIL_ID};"
pausa

titulo "contatos_emergencia: UPDATE (telefone e prioridade)"
chamar PUT "/perfis-emergencia/${PERFIL_ID}/contatos/${CONTATO_ID}" '{"nome":"Ana Souza","telefone":"(11) 98888-7777","parentesco":"Filha","prioridade":2}'
selecionar "SELECT id, nome, telefone, prioridade FROM contatos_emergencia WHERE id = ${CONTATO_ID};"
pausa

titulo "contatos_emergencia: DELETE"
chamar DELETE "/perfis-emergencia/${PERFIL_ID}/contatos/${CONTATO_ID}"
selecionar "SELECT COUNT(*) AS contatos FROM contatos_emergencia WHERE id = ${CONTATO_ID};"
pausa

# ---------- deletes finais ----------
titulo "perfis_emergencia: DELETE"
chamar DELETE "/perfis-emergencia/${PERFIL_ID}"
selecionar "SELECT COUNT(*) AS perfis FROM perfis_emergencia WHERE id = ${PERFIL_ID};"
pausa

titulo "usuarios: DELETE (exclusão da conta, LGPD)"
chamar DELETE /auth/eu
selecionar "SELECT COUNT(*) AS usuarios FROM usuarios WHERE email = '${EMAIL}';"

titulo "Pronto: CRUD completo nas três tabelas, conferido no Azure SQL"
