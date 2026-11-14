#!/usr/bin/env bash
# SPEC-024: provisiona na Azure tudo o que a API VozViva usa em produção.
#
#   Resource Group -> Azure SQL (servidor + banco) -> Container Registry (ACR)
#   -> RabbitMQ em container (ACI) -> App Service para containers (Linux) puxando a imagem do ACR
#
# Idempotente: cada recurso é conferido antes de ser criado; rodar de novo só cria o que falta e
# atualiza as configurações do App Service. O modelo de IA fica na OCI (fora da Azure).
#
# Uso (Azure Cloud Shell ou Azure CLI 2.60+ com "az login"):
#   export SUFIXO=rm556677                 # deixa os nomes únicos (ex.: RM do representante)
#   export SQL_ADMIN_SENHA='...'           # senha forte do administrador do Azure SQL
#   export RABBITMQ_SENHA='...'            # senha do RabbitMQ
#   export JWT_SECRET='...'                # 32+ caracteres, o mesmo da API .NET
#   ./scripts/provisionar-azure.sh
#
# Opcionais: LOCALIZACAO (padrão brazilsouth), GRUPO, JWT_ISSUER, OCI_GENAI_API_KEY, OCI_GENAI_BASE_URL,
# OCI_GENAI_MODEL, USUARIOS_TESTE_SENHA (cria os usuários de teste, útil na demonstração) e
# LIBERAR_MEU_IP=sim (libera o seu IP no firewall do Azure SQL, para o Query editor e o SELECT do vídeo).
# Nenhuma senha fica neste arquivo.
set -euo pipefail

: "${SUFIXO:?Defina SUFIXO (ex.: o RM do representante) para os nomes ficarem únicos}"
: "${SQL_ADMIN_SENHA:?Defina SQL_ADMIN_SENHA}"
: "${RABBITMQ_SENHA:?Defina RABBITMQ_SENHA}"
: "${JWT_SECRET:?Defina JWT_SECRET (mínimo de 32 caracteres)}"
if [ "${#JWT_SECRET}" -lt 32 ]; then
  echo "JWT_SECRET precisa ter pelo menos 32 caracteres." >&2
  exit 1
fi

SUFIXO_LIMPO="$(echo "$SUFIXO" | tr '[:upper:]' '[:lower:]' | tr -cd '[:alnum:]')"
LOCALIZACAO="${LOCALIZACAO:-brazilsouth}"
GRUPO="${GRUPO:-rg-vozviva}"
SQL_SERVIDOR="sql-vozviva-${SUFIXO_LIMPO}"
SQL_BANCO="vozviva"
SQL_ADMIN="vozvivaadmin"
ACR="acrvozviva${SUFIXO_LIMPO}"
ACI_RABBITMQ="aci-vozviva-rabbitmq"
DNS_RABBITMQ="vozviva-rabbitmq-${SUFIXO_LIMPO}"
RABBITMQ_USUARIO="vozviva"
PLANO="plan-vozviva"
WEBAPP="app-vozviva-${SUFIXO_LIMPO}"
JWT_ISSUER="${JWT_ISSUER:-vozviva}"
IMAGEM_INICIAL="mcr.microsoft.com/appsvc/staticsite:latest"

passo() { printf '\n==> %s\n' "$*"; }
existe() { "$@" > /dev/null 2>&1; }
criado() { echo "    criado: $*"; }
reaproveitado() { echo "    já existia: $*"; }

passo "Assinatura em uso"
az account show --query "{assinatura:name, id:id}" -o table

passo "Grupo de recursos ${GRUPO} (${LOCALIZACAO})"
if existe az group show -n "$GRUPO"; then
  reaproveitado "$GRUPO"
else
  az group create -n "$GRUPO" -l "$LOCALIZACAO" --tags projeto=vozviva disciplina=devops -o none
  criado "$GRUPO"
fi

passo "Azure SQL: servidor ${SQL_SERVIDOR}"
if existe az sql server show -g "$GRUPO" -n "$SQL_SERVIDOR"; then
  reaproveitado "$SQL_SERVIDOR"
else
  az sql server create -g "$GRUPO" -n "$SQL_SERVIDOR" -l "$LOCALIZACAO" \
    --admin-user "$SQL_ADMIN" --admin-password "$SQL_ADMIN_SENHA" --minimal-tls-version 1.2 -o none
  criado "$SQL_SERVIDOR"
fi

# 0.0.0.0 a 0.0.0.0 é a regra especial "permitir serviços da Azure" (o App Service se conecta por ela).
if existe az sql server firewall-rule show -g "$GRUPO" -s "$SQL_SERVIDOR" -n PermitirServicosAzure; then
  reaproveitado "regra de firewall PermitirServicosAzure"
else
  az sql server firewall-rule create -g "$GRUPO" -s "$SQL_SERVIDOR" -n PermitirServicosAzure \
    --start-ip-address 0.0.0.0 --end-ip-address 0.0.0.0 -o none
  criado "regra de firewall PermitirServicosAzure"
fi

if [ "${LIBERAR_MEU_IP:-nao}" = "sim" ]; then
  MEU_IP="$(curl -fsS https://api.ipify.org)"
  az sql server firewall-rule create -g "$GRUPO" -s "$SQL_SERVIDOR" -n MeuIp \
    --start-ip-address "$MEU_IP" --end-ip-address "$MEU_IP" -o none
  echo "    liberado no firewall: ${MEU_IP}"
fi

passo "Azure SQL: banco ${SQL_BANCO} (Basic)"
if existe az sql db show -g "$GRUPO" -s "$SQL_SERVIDOR" -n "$SQL_BANCO"; then
  reaproveitado "$SQL_BANCO"
else
  # As tabelas são criadas pelo Flyway na primeira subida da API (mesmo conteúdo de scripts/script-bd.sql).
  az sql db create -g "$GRUPO" -s "$SQL_SERVIDOR" -n "$SQL_BANCO" --service-objective Basic \
    --backup-storage-redundancy Local -o none
  criado "$SQL_BANCO"
fi

passo "Container Registry ${ACR}"
if existe az acr show -g "$GRUPO" -n "$ACR"; then
  reaproveitado "$ACR"
else
  # Sem usuário admin: o App Service puxa a imagem com identidade gerenciada (nenhuma senha guardada).
  az acr create -g "$GRUPO" -n "$ACR" --sku Basic --admin-enabled false -o none
  criado "$ACR"
fi
ACR_SERVIDOR="$(az acr show -g "$GRUPO" -n "$ACR" --query loginServer -o tsv)"
ACR_ID="$(az acr show -g "$GRUPO" -n "$ACR" --query id -o tsv)"

passo "RabbitMQ em container (ACI) ${ACI_RABBITMQ}"
if existe az container show -g "$GRUPO" -n "$ACI_RABBITMQ"; then
  reaproveitado "$ACI_RABBITMQ (para trocar a senha, apague o container e rode o script de novo)"
else
  az container create -g "$GRUPO" -n "$ACI_RABBITMQ" -l "$LOCALIZACAO" \
    --image docker.io/library/rabbitmq:4 --os-type Linux --cpu 1 --memory 1.5 \
    --ports 5672 --ip-address Public --dns-name-label "$DNS_RABBITMQ" --restart-policy Always \
    --environment-variables RABBITMQ_DEFAULT_USER="$RABBITMQ_USUARIO" \
    --secure-environment-variables RABBITMQ_DEFAULT_PASS="$RABBITMQ_SENHA" -o none
  criado "$ACI_RABBITMQ"
fi
RABBITMQ_HOST="$(az container show -g "$GRUPO" -n "$ACI_RABBITMQ" --query ipAddress.fqdn -o tsv)"

passo "App Service: plano ${PLANO} (Linux B1)"
if existe az appservice plan show -g "$GRUPO" -n "$PLANO"; then
  reaproveitado "$PLANO"
else
  az appservice plan create -g "$GRUPO" -n "$PLANO" -l "$LOCALIZACAO" --is-linux --sku B1 -o none
  criado "$PLANO"
fi

passo "App Service: aplicativo ${WEBAPP}"
if existe az webapp show -g "$GRUPO" -n "$WEBAPP"; then
  reaproveitado "$WEBAPP"
else
  # Imagem provisória: a pipeline de release troca pela imagem da API a cada novo build.
  az webapp create -g "$GRUPO" -p "$PLANO" -n "$WEBAPP" --container-image-name "$IMAGEM_INICIAL" -o none
  criado "$WEBAPP"
fi

passo "Identidade gerenciada do App Service com permissão de leitura no ACR (AcrPull)"
PRINCIPAL="$(az webapp identity assign -g "$GRUPO" -n "$WEBAPP" --query principalId -o tsv)"
if [ -n "$(az role assignment list --assignee "$PRINCIPAL" --scope "$ACR_ID" --role AcrPull --query '[].id' -o tsv)" ]; then
  reaproveitado "permissão AcrPull"
else
  az role assignment create --assignee-object-id "$PRINCIPAL" --assignee-principal-type ServicePrincipal \
    --scope "$ACR_ID" --role AcrPull -o none
  criado "permissão AcrPull"
fi

passo "App Service: configuração (HTTPS, health check, always on)"
az webapp update -g "$GRUPO" -n "$WEBAPP" --https-only true -o none
az webapp config set -g "$GRUPO" -n "$WEBAPP" --always-on true --min-tls-version 1.2 --ftps-state Disabled \
  --generic-configurations '{"acrUseManagedIdentityCreds": true, "healthCheckPath": "/actuator/health/liveness"}' -o none
echo "    ok"

passo "App Service: variáveis de ambiente (segredos ficam só aqui, nunca no repositório)"
DB_URL="jdbc:sqlserver://${SQL_SERVIDOR}.database.windows.net:1433;database=${SQL_BANCO};encrypt=true;trustServerCertificate=false;hostNameInCertificate=*.database.windows.net;loginTimeout=30"
CONFIGURACOES=(
  "SPRING_PROFILES_ACTIVE=prod"
  "WEBSITES_PORT=8080"
  "WEBSITES_CONTAINER_START_TIME_LIMIT=300"
  "DB_URL=${DB_URL}"
  "DB_USERNAME=${SQL_ADMIN}"
  "DB_PASSWORD=${SQL_ADMIN_SENHA}"
  "RABBITMQ_HOST=${RABBITMQ_HOST}"
  "RABBITMQ_PORT=5672"
  "RABBITMQ_USERNAME=${RABBITMQ_USUARIO}"
  "RABBITMQ_PASSWORD=${RABBITMQ_SENHA}"
  "JWT_SECRET=${JWT_SECRET}"
  "JWT_ISSUER=${JWT_ISSUER}"
)
[ -n "${OCI_GENAI_API_KEY:-}" ] && CONFIGURACOES+=("OCI_GENAI_API_KEY=${OCI_GENAI_API_KEY}")
[ -n "${OCI_GENAI_BASE_URL:-}" ] && CONFIGURACOES+=("OCI_GENAI_BASE_URL=${OCI_GENAI_BASE_URL}")
[ -n "${OCI_GENAI_MODEL:-}" ] && CONFIGURACOES+=("OCI_GENAI_MODEL=${OCI_GENAI_MODEL}")
if [ -n "${USUARIOS_TESTE_SENHA:-}" ]; then
  CONFIGURACOES+=("USUARIOS_TESTE_HABILITADO=true" "USUARIOS_TESTE_SENHA=${USUARIOS_TESTE_SENHA}")
fi
az webapp config appsettings set -g "$GRUPO" -n "$WEBAPP" --settings "${CONFIGURACOES[@]}" -o none
echo "    ${#CONFIGURACOES[@]} variáveis definidas"

passo "Pronto"
cat <<RESUMO
    API (depois do primeiro deploy): https://${WEBAPP}.azurewebsites.net
    Swagger:                         https://${WEBAPP}.azurewebsites.net/swagger-ui.html
    Health:                          https://${WEBAPP}.azurewebsites.net/actuator/health
    Azure SQL:                       ${SQL_SERVIDOR}.database.windows.net / banco ${SQL_BANCO} (usuário ${SQL_ADMIN})
    Container Registry:              ${ACR_SERVIDOR}
    RabbitMQ:                        ${RABBITMQ_HOST}:5672

    Próximos passos no Azure DevOps (detalhes no README, seção "Deploy na Azure"):
    1. Service connections: "vozviva-azure" (Azure Resource Manager, grupo ${GRUPO})
       e "vozviva-acr" (Docker Registry -> Azure Container Registry ${ACR}).
    2. Library -> grupo de variáveis "vozviva-azure": webAppName=${WEBAPP}, acrLoginServer=${ACR_SERVIDOR}.
    3. Pipeline a partir do azure-pipeline.yml da raiz.
    Para apagar tudo e parar a cobrança: ./scripts/remover-azure.sh
RESUMO
