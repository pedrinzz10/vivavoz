#!/usr/bin/env bash
# Configura no Azure DevOps o projeto da entrega de DevOps e QA (a organização é criada antes, no navegador,
# em https://aex.dev.azure.com; o CLI não cria organizações).
#
# Cria ou reaproveita:
#   - projeto privado com Git e processo Scrum (o backlog de docs/azure-boards usa Epic, Feature e PBI);
#   - repositório importado do GitHub;
#   - pipeline a partir do azure-pipeline.yml (sem rodar agora: falta a infraestrutura da Azure);
#   - políticas da main: 1 revisor (o autor pode aprovar), Work Item vinculado e revisor obrigatório (seu RM);
#   - tarefa inicial no Boards;
#   - professores na organização (Basic) e no grupo Project Administrators, se os e-mails forem informados;
#   - grupo de variáveis da pipeline, se WEBAPP_NAME e ACR_LOGIN_SERVER forem informados (saem do
#     provisionar-azure.sh).
# As service connections (Azure e ACR) são criadas pelo portal depois do provisionamento: ver README.
#
# Uso (Cloud Shell da Azure, logado com a conta da FIAP):
#   ORGANIZACAO=vozviva REVISOR_EMAIL=RM562312@fiap.com.br ./scripts/configurar-azure-devops.sh
# Opcionais: PROJETO (padrão vozviva), REPOSITORIO (padrão vozviva), REPO_GITHUB, PROFESSOR_DEVOPS_EMAIL, PROFESSOR_QA_EMAIL,
#   WEBAPP_NAME, ACR_LOGIN_SERVER.
set -euo pipefail

: "${ORGANIZACAO:?informe ORGANIZACAO (nome criado em https://aex.dev.azure.com)}"
: "${REVISOR_EMAIL:?informe REVISOR_EMAIL (o seu e-mail RM, revisor obrigatório da main)}"
PROJETO="${PROJETO:-vozviva}"
REPOSITORIO="${REPOSITORIO:-vozviva}"
REPO_GITHUB="${REPO_GITHUB:-https://github.com/pedrinzz10/vivavoz.git}"
PIPELINE="vozviva-ci-cd"
GRUPO_VARIAVEIS="vozviva-azure"
TAREFA="Configurar CI/CD da API VozViva na Azure"
ORG="https://dev.azure.com/${ORGANIZACAO}"

passo() { printf '\n==> %s\n' "$*"; }
criado() { echo "    criado: $*"; }
reaproveitado() { echo "    já existia: $*"; }
ado() { az "$@" --org "$ORG" --only-show-errors; }
adop() { az "$@" --org "$ORG" --project "$PROJETO" --only-show-errors; }

az extension show --name azure-devops > /dev/null 2>&1 || az extension add --name azure-devops --only-show-errors

passo "Organização ${ORG}"
ado devops project list --query 'count' -o tsv > /dev/null \
  || { echo "    sem acesso à organização; confira o nome e se ela usa a conta da FIAP"; exit 1; }
echo "    acessível"

passo "Projeto ${PROJETO} (privado, Git, Scrum)"
if ado devops project show --project "$PROJETO" > /dev/null 2>&1; then
  reaproveitado "$PROJETO"
  PROCESSO=$(ado devops project show --project "$PROJETO" \
    --query 'capabilities.processTemplate.templateName' -o tsv)
  [ "$PROCESSO" = "Scrum" ] || echo "    atenção: o processo é ${PROCESSO}; o backlog.csv precisa de Scrum"
else
  ado devops project create --name "$PROJETO" --process Scrum --source-control git --visibility private \
    --description "API Java do ecossistema Fala Cidadão (FIAP Global Solution - CitizenTech)" -o none
  criado "$PROJETO"
fi

passo "Repositório ${REPOSITORIO} (importado de ${REPO_GITHUB})"
if ! adop repos show --repository "$REPOSITORIO" > /dev/null 2>&1; then
  adop repos create --name "$REPOSITORIO" -o none
  criado "repositório ${REPOSITORIO}"
fi
REPO_ID=$(adop repos show --repository "$REPOSITORIO" --query id -o tsv)
TAMANHO=$(adop repos show --repository "$REPOSITORIO" --query size -o tsv)
if [ "${TAMANHO:-0}" != "0" ]; then
  reaproveitado "repositório com código"
else
  adop repos import create --repository "$REPOSITORIO" --git-source-url "$REPO_GITHUB" -o none
  criado "importação do GitHub"
fi
DEFAULT=$(adop repos show --repository "$REPOSITORIO" --query defaultBranch -o tsv)
[ "$DEFAULT" = "refs/heads/main" ] || adop repos update --repository "$REPOSITORIO" --default-branch main -o none

passo "Pipeline ${PIPELINE} (azure-pipeline.yml)"
if adop pipelines show --name "$PIPELINE" > /dev/null 2>&1; then
  reaproveitado "$PIPELINE"
else
  adop pipelines create --name "$PIPELINE" --repository "$REPOSITORIO" --repository-type tfsgit --branch main \
    --yml-path azure-pipeline.yml --skip-first-run true -o none
  criado "$PIPELINE (a primeira execução fica para depois do provisionamento)"
fi

passo "Políticas da main"
POLITICAS=$(adop repos policy list --repository-id "$REPO_ID" --branch main --query '[].type.displayName' -o tsv)
if grep -q "Minimum number of reviewers" <<< "$POLITICAS"; then
  reaproveitado "mínimo de revisores"
else
  adop repos policy approver-count create --repository-id "$REPO_ID" --branch main --blocking true \
    --enabled true --minimum-approver-count 1 --creator-vote-counts true --allow-downvotes false \
    --reset-on-source-push false -o none
  criado "1 revisor, o autor pode aprovar"
fi
if grep -q "Work item linking" <<< "$POLITICAS"; then
  reaproveitado "Work Item vinculado"
else
  adop repos policy work-item-linking create --repository-id "$REPO_ID" --branch main --blocking true \
    --enabled true -o none
  criado "Work Item vinculado obrigatório"
fi
if grep -q "Required reviewers" <<< "$POLITICAS"; then
  reaproveitado "revisor obrigatório"
else
  adop repos policy required-reviewer create --repository-id "$REPO_ID" --branch main --blocking true \
    --enabled true --required-reviewer-ids "$REVISOR_EMAIL" --message "Revisão do RM responsável" -o none
  criado "revisor obrigatório ${REVISOR_EMAIL}"
fi

passo "Tarefa inicial no Boards"
EXISTE_TAREFA=$(adop boards query --wiql "SELECT [System.Id] FROM workitems WHERE [System.TeamProject] = '${PROJETO}' AND [System.Title] = '${TAREFA}'" \
  --query '[].id' -o tsv | wc -l)
if [ "${EXISTE_TAREFA:-0}" != "0" ]; then
  reaproveitado "$TAREFA"
else
  adop boards work-item create --type Task --title "$TAREFA" \
    --description "Provisionar a Azure com scripts/provisionar-azure.sh, criar as service connections e rodar a pipeline." \
    -o none
  criado "$TAREFA"
fi

passo "Professores"
ADMINS=$(adop devops security group list \
  --query "graphGroups[?displayName=='Project Administrators'].descriptor | [0]" -o tsv)
for email in ${PROFESSOR_DEVOPS_EMAIL:-} ${PROFESSOR_QA_EMAIL:-}; do
  if ado devops user show --user "$email" > /dev/null 2>&1; then
    reaproveitado "$email na organização"
  else
    ado devops user add --email-id "$email" --license-type express --send-email-invite true -o none
    criado "$email na organização (Basic)"
  fi
  ado devops security group membership add --group-id "$ADMINS" --member-id "$email" -o none 2> /dev/null \
    && echo "    ${email} em Project Administrators" \
    || reaproveitado "${email} em Project Administrators"
done
[ -n "${PROFESSOR_DEVOPS_EMAIL:-}${PROFESSOR_QA_EMAIL:-}" ] || echo "    pulado: informe PROFESSOR_DEVOPS_EMAIL e PROFESSOR_QA_EMAIL"

passo "Grupo de variáveis ${GRUPO_VARIAVEIS}"
GRUPO_ID=$(adop pipelines variable-group list --group-name "$GRUPO_VARIAVEIS" --query '[0].id' -o tsv)
if [ -n "$GRUPO_ID" ]; then
  reaproveitado "$GRUPO_VARIAVEIS"
elif [ -n "${WEBAPP_NAME:-}" ] && [ -n "${ACR_LOGIN_SERVER:-}" ]; then
  adop pipelines variable-group create --name "$GRUPO_VARIAVEIS" --authorize true \
    --variables "webAppName=${WEBAPP_NAME}" "acrLoginServer=${ACR_LOGIN_SERVER}" -o none
  criado "$GRUPO_VARIAVEIS"
else
  echo "    pulado: rode de novo com WEBAPP_NAME e ACR_LOGIN_SERVER depois do provisionar-azure.sh"
fi

cat <<EOF

Pronto: ${ORG}/${PROJETO}
Falta, pelo navegador:
  - Organization settings > Pipelines > Parallel jobs: confira se há 1 job Microsoft-hosted para projeto privado;
    se for 0, peça a cota gratuita em https://aka.ms/azpipelines-parallelism-request (leva alguns dias) ou use
    um agente self-hosted.
  - Organization settings > Billing: ative o trial de Test Plans (para executar os testes manuais de QA).
  - Boards > Work items > Import Work Items: docs/azure-boards/backlog.csv; Test Plans: os CSVs de casos de teste.
  - Depois do provisionar-azure.sh: service connections vozviva-azure e vozviva-acr (ver README).
EOF
