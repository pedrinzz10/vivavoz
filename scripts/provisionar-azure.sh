#!/usr/bin/env bash
# SPEC-024: provisiona na Azure tudo o que a API VozViva usa em produção.
#
#   Resource Group -> Azure SQL (servidor, banco e usuário da aplicação) -> Container Registry (ACR)
#   -> rede virtual -> RabbitMQ em container (ACI, só IP privado) -> App Service para containers (Linux)
#   ligado à rede virtual e puxando a imagem do ACR
#   -> API .NET de zeladoria (outro app no mesmo plano, sem custo extra, com o próprio banco "zeladoria")
#
# Requer o sqlcmd para criar o usuário da aplicação no banco (se faltar, baixa o go-sqlcmd para ~/bin), e git, dotnet e zip
# (também no Cloud Shell) para publicar a API .NET.
#
# Idempotente: cada recurso é conferido antes de ser criado; rodar de novo só cria o que falta e
# atualiza as configurações do App Service. O modelo de IA fica na OCI (fora da Azure).
#
# Uso (Azure Cloud Shell ou Azure CLI 2.60+ com "az login"):
#   export SUFIXO=rm556677                 # deixa os nomes únicos (ex.: RM do representante)
#   export SQL_ADMIN_SENHA='...'           # senha forte do administrador do Azure SQL (só para provisionar)
#   export APP_SQL_SENHA='...'             # senha do usuário da aplicação no banco (privilégio mínimo)
#   export RABBITMQ_SENHA='...'            # senha do RabbitMQ
#   export JWT_SECRET='...'                # 32+ caracteres, o mesmo da API .NET
#   ./scripts/provisionar-azure.sh
#
# Opcionais: LOCALIZACAO (padrão brazilsouth), SQL_LOCALIZACAO (padrão chilecentral, região do Azure SQL), GRUPO, JWT_ISSUER (padrão voz-e-via-java), JWT_AUDIENCE (padrão
# voz-e-via; os dois iguais aos da API .NET), OCI_GENAI_API_KEY, OCI_GENAI_BASE_URL, OCI_GENAI_MODEL ou, sem API
# key, as variáveis OCI_GENAI_TENANCY_OCID, OCI_GENAI_USER_OCID, OCI_GENAI_FINGERPRINT, OCI_GENAI_PRIVATE_KEY e
# OCI_GENAI_COMPARTMENT_OCID (todas geradas por scripts/provisionar-oci.sh), USUARIOS_TESTE_SENHA (cria os usuários de teste, útil na demonstração) e
# LIBERAR_MEU_IP=sim (libera o seu IP no firewall do Azure SQL, para o Query editor e o SELECT do vídeo).
# API .NET: PUBLICAR_DOTNET (padrão sim; o banco "zeladoria" Basic custa cerca de US$ 5 por mês), DOTNET_REPO e
# DOTNET_BRANCH (padrão main do repositório da equipe .NET).
# Orçamento do grupo: ORCAMENTO_USD (padrão 80, o crédito do Azure for Students) e ORCAMENTO_EMAIL (padrão: o e-mail
# da conta logada) para os alertas de gasto acumulado em 50% e 80% e na previsão de 100%.
#
# Custo estimado em brazilsouth (preços de varejo da Microsoft): cerca de US$ 2,20 por dia, a maior parte do
# RabbitMQ em container. Para parar a cobrança: ./scripts/remover-azure.sh.
# Nenhuma senha fica neste arquivo.
set -euo pipefail

: "${SUFIXO:?Defina SUFIXO (ex.: o RM do representante) para os nomes ficarem únicos}"
: "${SQL_ADMIN_SENHA:?Defina SQL_ADMIN_SENHA}"
: "${APP_SQL_SENHA:?Defina APP_SQL_SENHA (senha do usuário da aplicação no banco)}"
: "${RABBITMQ_SENHA:?Defina RABBITMQ_SENHA}"
: "${JWT_SECRET:?Defina JWT_SECRET (mínimo de 32 caracteres)}"
if [ "${#JWT_SECRET}" -lt 32 ]; then
  echo "JWT_SECRET precisa ter pelo menos 32 caracteres." >&2
  exit 1
fi

SUFIXO_LIMPO="$(echo "$SUFIXO" | tr '[:upper:]' '[:lower:]' | tr -cd '[:alnum:]')"
LOCALIZACAO="${LOCALIZACAO:-brazilsouth}"
# Azure SQL em outra região: em brazilsouth a criação do servidor estourou o tempo da Azure duas vezes seguidas
# ("OperationTimedOut") e eastus2 não aceitava servidores novos ("RegionDoesNotAllowProvisioning") em 18/11/2026.
# chilecentral, permitida na assinatura de estudante, é a mais perto das APIs (cerca de 50 ms).
SQL_LOCALIZACAO="${SQL_LOCALIZACAO:-chilecentral}"
GRUPO="${GRUPO:-rg-vozviva}"
SQL_SERVIDOR="sql-vozviva-${SUFIXO_LIMPO}"
# Com o banco fora da região das APIs, a região entra no nome (nomes de servidor SQL valem para a Azure inteira).
[ "$SQL_LOCALIZACAO" != "$LOCALIZACAO" ] && SQL_SERVIDOR="${SQL_SERVIDOR}-${SQL_LOCALIZACAO}"
SQL_BANCO="vozviva"
SQL_ADMIN="vozvivaadmin"
SQL_APP_USUARIO="vozviva_app"
ACR="acrvozviva${SUFIXO_LIMPO}"
KEYVAULT="kv-vozviva-${SUFIXO_LIMPO}"
VNET="vnet-vozviva"
SUBNET_APP="snet-app"
SUBNET_RABBITMQ="snet-rabbitmq"
ACI_RABBITMQ="aci-vozviva-rabbitmq"
# Mesma imagem oficial do Docker Hub, pelo espelho público da AWS: o Docker Hub limita downloads anônimos e a
# Azure às vezes esbarra nesse limite ao criar o container ("toomanyrequests").
RABBITMQ_IMAGEM="${RABBITMQ_IMAGEM:-public.ecr.aws/docker/library/rabbitmq:4}"
RABBITMQ_USUARIO="vozviva"
PLANO="plan-vozviva"
WEBAPP="app-vozviva-${SUFIXO_LIMPO}"
JWT_ISSUER="${JWT_ISSUER:-voz-e-via-java}"
JWT_AUDIENCE="${JWT_AUDIENCE:-voz-e-via}"
IMAGEM_INICIAL="mcr.microsoft.com/appsvc/staticsite:latest"
PUBLICAR_DOTNET="${PUBLICAR_DOTNET:-sim}"
DOTNET_REPO="${DOTNET_REPO:-https://github.com/Fabriciopereira-sp/DOTNETGS.git}"
DOTNET_BRANCH="${DOTNET_BRANCH:-main}"
SQL_BANCO_DOTNET="zeladoria"
WEBAPP_DOTNET="app-zeladoria-${SUFIXO_LIMPO}"
ORCAMENTO="vozviva-creditos"
ORCAMENTO_USD="${ORCAMENTO_USD:-80}"
# As duas APIs dividem os 1,75 GB do plano B1: sem limite, a JVM usaria até 75% da memória e o .NET ficaria sem.
JAVA_OPTS_APP="-XX:MaxRAMPercentage=40 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"

passo() { printf '\n==> %s\n' "$*"; }
existe() { "$@" > /dev/null 2>&1; }
criado() { echo "    criado: $*"; }
reaproveitado() { echo "    já existia: $*"; }

passo "Assinatura em uso"
az account show --query "{assinatura:name, id:id}" -o table

# Assinaturas novas (como as de estudante) vêm com alguns provedores desligados, e o primeiro "create" falharia.
passo "Provedores de recursos da assinatura"
for provedor in Microsoft.Web Microsoft.Sql Microsoft.ContainerRegistry Microsoft.ContainerInstance Microsoft.Network \
    Microsoft.KeyVault; do
  if [ "$(az provider show -n "$provedor" --query registrationState -o tsv)" = "Registered" ]; then
    reaproveitado "$provedor"
  else
    az provider register -n "$provedor" --wait -o none
    criado "$provedor (registrado)"
  fi
done

passo "Grupo de recursos ${GRUPO} (${LOCALIZACAO})"
if existe az group show -n "$GRUPO"; then
  reaproveitado "$GRUPO"
else
  az group create -n "$GRUPO" -l "$LOCALIZACAO" --tags projeto=vozviva disciplina=devops -o none
  criado "$GRUPO"
fi

passo "Key Vault ${KEYVAULT}: cofre com as senhas (as APIs leem daqui; nenhuma senha fica nas configurações)"
if existe az keyvault show -g "$GRUPO" -n "$KEYVAULT"; then
  reaproveitado "$KEYVAULT"
elif [ -n "$(az keyvault list-deleted --resource-type vault --query "[?name=='${KEYVAULT}'].name" -o tsv 2> /dev/null)" ]; then
  # Um cofre apagado fica 7 dias na lixeira com o nome reservado: recupera em vez de criar outro.
  az keyvault recover -n "$KEYVAULT" -o none
  criado "$KEYVAULT (recuperado da lixeira)"
else
  az keyvault create -g "$GRUPO" -n "$KEYVAULT" -l "$LOCALIZACAO" --enable-rbac-authorization true \
    --retention-days 7 -o none
  criado "$KEYVAULT"
fi
KEYVAULT_ID="$(az keyvault show -g "$GRUPO" -n "$KEYVAULT" --query id -o tsv)"

# Dá um papel a uma identidade num escopo (o cofre inteiro ou um segredo só), se ela ainda não tiver.
papel_em() {
  local escopo="$1" objeto="$2" tipo="$3" papel="$4" descricao="$5"
  # Uma identidade recém-criada pode ainda não aparecer na busca: aí a lista vem vazia e o papel é criado pelo id.
  if [ -n "$(az role assignment list --assignee "$objeto" --scope "$escopo" --role "$papel" --query '[].id' -o tsv 2> /dev/null)" ]; then
    reaproveitado "$descricao"
  else
    az role assignment create --assignee-object-id "$objeto" --assignee-principal-type "$tipo" \
      --scope "$escopo" --role "$papel" -o none
    criado "$descricao"
  fi
}
papel_no_cofre() { papel_em "$KEYVAULT_ID" "$1" "$2" "$3" "${3} para ${4}"; }
# As APIs leem só os segredos que usam (nunca a sql-admin-senha): leitura em cada segredo, não no cofre inteiro.
leitura_dos_segredos() {
  local objeto="$1" quem="$2" segredo
  shift 2
  for segredo in "$@"; do
    papel_em "${KEYVAULT_ID}/secrets/${segredo}" "$objeto" ServicePrincipal "Key Vault Secrets User" \
      "leitura de ${segredo} para ${quem}"
  done
}
# Quem roda o script grava os segredos (e consegue copiá-los no portal, como a senha do SQL para o Query editor).
EU="$(az ad signed-in-user show --query id -o tsv)"
papel_no_cofre "$EU" User "Key Vault Secrets Officer" "você"

# Grava um segredo; a permissão recém-dada pode levar alguns minutos para valer, então tenta de novo.
guardar() {
  local nome="$1" valor="$2" tentativa
  for tentativa in 1 2 3 4 5 6 7 8 9 10; do
    if az keyvault secret set --vault-name "$KEYVAULT" -n "$nome" --value "$valor" -o none 2> /dev/null; then
      echo "    segredo ${nome}"
      return 0
    fi
    [ "$tentativa" = 1 ] && echo "    esperando a permissão no cofre valer..."
    sleep 20
  done
  echo "    não consegui gravar o segredo ${nome} no ${KEYVAULT}" >&2
  exit 1
}
# Referência que o App Service resolve na hora de subir a API, com a identidade gerenciada dela.
ref() { printf '@Microsoft.KeyVault(VaultName=%s;SecretName=%s)' "$KEYVAULT" "$1"; }

guardar sql-admin-senha "$SQL_ADMIN_SENHA"
guardar app-sql-senha "$APP_SQL_SENHA"
guardar rabbitmq-senha "$RABBITMQ_SENHA"
guardar jwt-secret "$JWT_SECRET"
[ -n "${USUARIOS_TESTE_SENHA:-}" ] && guardar usuarios-teste-senha "$USUARIOS_TESTE_SENHA"
[ -n "${OCI_GENAI_API_KEY:-}" ] && guardar oci-genai-api-key "$OCI_GENAI_API_KEY"
[ -n "${OCI_GENAI_PRIVATE_KEY:-}" ] && guardar oci-genai-private-key "$OCI_GENAI_PRIVATE_KEY"

passo "Azure SQL: servidor ${SQL_SERVIDOR} (${SQL_LOCALIZACAO})"
if existe az sql server show -g "$GRUPO" -n "$SQL_SERVIDOR"; then
  reaproveitado "$SQL_SERVIDOR"
else
  az sql server create -g "$GRUPO" -n "$SQL_SERVIDOR" -l "$SQL_LOCALIZACAO" \
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

if [ "$PUBLICAR_DOTNET" = "sim" ]; then
  passo "Azure SQL: banco ${SQL_BANCO_DOTNET} da API .NET (Basic)"
  if existe az sql db show -g "$GRUPO" -s "$SQL_SERVIDOR" -n "$SQL_BANCO_DOTNET"; then
    reaproveitado "$SQL_BANCO_DOTNET"
  else
    # Banco próprio: a API .NET cria as tabelas no primeiro start (EF Core), o que só acontece em banco vazio.
    az sql db create -g "$GRUPO" -s "$SQL_SERVIDOR" -n "$SQL_BANCO_DOTNET" --service-objective Basic \
      --backup-storage-redundancy Local -o none
    criado "$SQL_BANCO_DOTNET"
  fi
fi

passo "Azure SQL: usuário da aplicação ${SQL_APP_USUARIO} (privilégio mínimo, só nos bancos das APIs)"
# A API não usa o administrador do servidor: o usuário contido no banco só lê, grava e altera o esquema
# deste banco (o Flyway precisa de DDL). O administrador fica só para o provisionamento.
if ! command -v sqlcmd > /dev/null 2>&1; then
  # O Cloud Shell novo não traz mais o sqlcmd: baixa o go-sqlcmd oficial da Microsoft (versão fixa) para ~/bin.
  case "$(uname -m)" in x86_64) ARQ_SQLCMD=amd64 ;; aarch64|arm64) ARQ_SQLCMD=arm64 ;; *) ARQ_SQLCMD="" ;; esac
  mkdir -p "${HOME}/bin"
  if [ -n "$ARQ_SQLCMD" ] && curl -fsSL "https://github.com/microsoft/go-sqlcmd/releases/download/v1.10.0/sqlcmd-linux-${ARQ_SQLCMD}.tar.bz2" \
      | tar -xj -C "${HOME}/bin" sqlcmd; then
    export PATH="${HOME}/bin:${PATH}"
    criado "sqlcmd em ${HOME}/bin (go-sqlcmd 1.10.0)"
  else
    echo "sqlcmd não encontrado e não consegui baixar o go-sqlcmd; instale-o e rode de novo." >&2
    exit 1
  fi
fi
IP_PROVISIONAMENTO="$(curl -fsS https://api.ipify.org)"
az sql server firewall-rule create -g "$GRUPO" -s "$SQL_SERVIDOR" -n ProvisionamentoTemporario \
  --start-ip-address "$IP_PROVISIONAMENTO" --end-ip-address "$IP_PROVISIONAMENTO" -o none
SENHA_APP_SQL_ESCAPADA="${APP_SQL_SENHA//\'/\'\'}"
BANCOS_DA_APLICACAO=("$SQL_BANCO")
[ "$PUBLICAR_DOTNET" = "sim" ] && BANCOS_DA_APLICACAO+=("$SQL_BANCO_DOTNET")
# Usuário contido: cada banco tem o seu, com o mesmo nome e a mesma senha, e um não enxerga o outro banco.
for banco in "${BANCOS_DA_APLICACAO[@]}"; do
SQLCMDPASSWORD="$SQL_ADMIN_SENHA" sqlcmd -S "tcp:${SQL_SERVIDOR}.database.windows.net,1433" -d "$banco" \
  -U "$SQL_ADMIN" -b -Q "
IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = N'${SQL_APP_USUARIO}')
    CREATE USER [${SQL_APP_USUARIO}] WITH PASSWORD = N'${SENHA_APP_SQL_ESCAPADA}';
ELSE
    ALTER USER [${SQL_APP_USUARIO}] WITH PASSWORD = N'${SENHA_APP_SQL_ESCAPADA}';
ALTER ROLE db_datareader ADD MEMBER [${SQL_APP_USUARIO}];
ALTER ROLE db_datawriter ADD MEMBER [${SQL_APP_USUARIO}];
ALTER ROLE db_ddladmin ADD MEMBER [${SQL_APP_USUARIO}];
GRANT REFERENCES ON SCHEMA::dbo TO [${SQL_APP_USUARIO}];"
echo "    usuário ${SQL_APP_USUARIO} pronto (leitura, escrita e DDL no banco ${banco})"
done
az sql server firewall-rule delete -g "$GRUPO" -s "$SQL_SERVIDOR" -n ProvisionamentoTemporario -o none

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

passo "Rede virtual ${VNET} (o RabbitMQ não fica exposto na internet)"
if existe az network vnet show -g "$GRUPO" -n "$VNET"; then
  reaproveitado "$VNET"
else
  az network vnet create -g "$GRUPO" -n "$VNET" -l "$LOCALIZACAO" --address-prefixes 10.40.0.0/16 -o none
  criado "$VNET"
fi
if existe az network vnet subnet show -g "$GRUPO" --vnet-name "$VNET" -n "$SUBNET_APP"; then
  reaproveitado "sub-rede ${SUBNET_APP}"
else
  az network vnet subnet create -g "$GRUPO" --vnet-name "$VNET" -n "$SUBNET_APP" \
    --address-prefixes 10.40.1.0/24 --delegations Microsoft.Web/serverFarms -o none
  criado "sub-rede ${SUBNET_APP} (integração do App Service)"
fi
if existe az network vnet subnet show -g "$GRUPO" --vnet-name "$VNET" -n "$SUBNET_RABBITMQ"; then
  reaproveitado "sub-rede ${SUBNET_RABBITMQ}"
else
  az network vnet subnet create -g "$GRUPO" --vnet-name "$VNET" -n "$SUBNET_RABBITMQ" \
    --address-prefixes 10.40.2.0/24 --delegations Microsoft.ContainerInstance/containerGroups -o none
  criado "sub-rede ${SUBNET_RABBITMQ} (RabbitMQ)"
fi

passo "RabbitMQ em container (ACI) ${ACI_RABBITMQ}, só com IP privado"
if existe az container show -g "$GRUPO" -n "$ACI_RABBITMQ"; then
  reaproveitado "$ACI_RABBITMQ (para trocar a senha, apague o container e rode o script de novo)"
else
  # Sem IP público: só o App Service (pela integração com a rede virtual) alcança a porta 5672.
  # 0,5 vCPU e 1 GB sobram para a carga da demonstração e custam metade de 1 vCPU (o container é o item mais caro).
  for tentativa in 1 2 3; do
    if az container create -g "$GRUPO" -n "$ACI_RABBITMQ" -l "$LOCALIZACAO" \
        --image "$RABBITMQ_IMAGEM" --os-type Linux --cpu 0.5 --memory 1 \
        --ports 5672 --vnet "$VNET" --subnet "$SUBNET_RABBITMQ" --restart-policy Always \
        --environment-variables RABBITMQ_DEFAULT_USER="$RABBITMQ_USUARIO" \
        --secure-environment-variables RABBITMQ_DEFAULT_PASS="$RABBITMQ_SENHA" -o none; then
      break
    fi
    [ "$tentativa" = 3 ] && { echo "Não foi possível criar o container do RabbitMQ." >&2; exit 1; }
    echo "    falhou (tentativa ${tentativa}); nova tentativa em 30 s"
    sleep 30
  done
  criado "$ACI_RABBITMQ"
fi
# O IP privado vai para RABBITMQ_HOST da API. Se o container for recriado (ou a Azure o mover) e o IP mudar, rodar
# este script de novo atualiza a configuração; até lá, as emergências ficam ACIONADA e o reenvio tenta depois.
RABBITMQ_HOST="$(az container show -g "$GRUPO" -n "$ACI_RABBITMQ" --query ipAddress.ip -o tsv)"

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
if [ -n "$(az role assignment list --assignee "$PRINCIPAL" --scope "$ACR_ID" --role AcrPull --query '[].id' -o tsv 2> /dev/null)" ]; then
  reaproveitado "permissão AcrPull"
else
  az role assignment create --assignee-object-id "$PRINCIPAL" --assignee-principal-type ServicePrincipal \
    --scope "$ACR_ID" --role AcrPull -o none
  criado "permissão AcrPull"
fi
SEGREDOS_JAVA=(app-sql-senha rabbitmq-senha jwt-secret)
[ -n "${USUARIOS_TESTE_SENHA:-}" ] && SEGREDOS_JAVA+=(usuarios-teste-senha)
[ -n "${OCI_GENAI_API_KEY:-}" ] && SEGREDOS_JAVA+=(oci-genai-api-key)
[ -n "${OCI_GENAI_PRIVATE_KEY:-}" ] && SEGREDOS_JAVA+=(oci-genai-private-key)
leitura_dos_segredos "$PRINCIPAL" "$WEBAPP" "${SEGREDOS_JAVA[@]}"


passo "App Service: integração com a rede virtual (acesso privado ao RabbitMQ)"
if [ -n "$(az webapp vnet-integration list -g "$GRUPO" -n "$WEBAPP" --query '[].name' -o tsv)" ]; then
  reaproveitado "integração com ${VNET}"
else
  az webapp vnet-integration add -g "$GRUPO" -n "$WEBAPP" --vnet "$VNET" --subnet "$SUBNET_APP" -o none
  criado "integração com ${VNET}/${SUBNET_APP}"
fi

passo "App Service: configuração (HTTPS, health check, always on)"
az webapp update -g "$GRUPO" -n "$WEBAPP" --https-only true -o none
az webapp config set -g "$GRUPO" -n "$WEBAPP" --always-on true --min-tls-version 1.2 --ftps-state Disabled \
  --generic-configurations '{"acrUseManagedIdentityCreds": true, "healthCheckPath": "/actuator/health/liveness"}' -o none
# Logs do container (stdout da API) no próprio App Service: Log stream no portal e "az webapp log tail".
az webapp log config -g "$GRUPO" -n "$WEBAPP" --docker-container-logging filesystem -o none
echo "    ok (logs do container ligados)"

passo "App Service: variáveis de ambiente (as senhas são referências ao ${KEYVAULT}, nunca o valor)"
DB_URL="jdbc:sqlserver://${SQL_SERVIDOR}.database.windows.net:1433;database=${SQL_BANCO};encrypt=true;trustServerCertificate=false;hostNameInCertificate=*.database.windows.net;loginTimeout=30"
CONFIGURACOES=(
  "SPRING_PROFILES_ACTIVE=prod"
  "WEBSITES_PORT=8080"
  "WEBSITES_CONTAINER_START_TIME_LIMIT=300"
  "WEBSITE_HTTPLOGGING_RETENTION_DAYS=3"
  "JAVA_OPTS=${JAVA_OPTS_APP}"
  "DB_URL=${DB_URL}"
  "DB_USERNAME=${SQL_APP_USUARIO}"
  "DB_PASSWORD=$(ref app-sql-senha)"
  "RABBITMQ_HOST=${RABBITMQ_HOST}"
  "RABBITMQ_PORT=5672"
  "RABBITMQ_USERNAME=${RABBITMQ_USUARIO}"
  "RABBITMQ_PASSWORD=$(ref rabbitmq-senha)"
  "JWT_SECRET=$(ref jwt-secret)"
  "JWT_ISSUER=${JWT_ISSUER}"
  "JWT_AUDIENCE=${JWT_AUDIENCE}"
)
[ -n "${OCI_GENAI_API_KEY:-}" ] && CONFIGURACOES+=("OCI_GENAI_API_KEY=$(ref oci-genai-api-key)")
[ -n "${OCI_GENAI_BASE_URL:-}" ] && CONFIGURACOES+=("OCI_GENAI_BASE_URL=${OCI_GENAI_BASE_URL}")
[ -n "${OCI_GENAI_MODEL:-}" ] && CONFIGURACOES+=("OCI_GENAI_MODEL=${OCI_GENAI_MODEL}")
# Plano B da IA (conta sem API key do Generative AI): a API assina as chamadas com a chave do usuário da OCI.
# Os OCIDs e o fingerprint não são segredos; a chave privada fica no cofre.
for var in OCI_GENAI_TENANCY_OCID OCI_GENAI_USER_OCID OCI_GENAI_FINGERPRINT OCI_GENAI_COMPARTMENT_OCID; do
  [ -n "${!var:-}" ] && CONFIGURACOES+=("${var}=${!var}")
done
[ -n "${OCI_GENAI_PRIVATE_KEY:-}" ] && CONFIGURACOES+=("OCI_GENAI_PRIVATE_KEY=$(ref oci-genai-private-key)")
if [ -n "${USUARIOS_TESTE_SENHA:-}" ]; then
  CONFIGURACOES+=("USUARIOS_TESTE_HABILITADO=true" "USUARIOS_TESTE_SENHA=$(ref usuarios-teste-senha)")
fi
az webapp config appsettings set -g "$GRUPO" -n "$WEBAPP" --settings "${CONFIGURACOES[@]}" -o none
echo "    ${#CONFIGURACOES[@]} variáveis definidas"

if [ "$PUBLICAR_DOTNET" = "sim" ]; then
  passo "API .NET (zeladoria): aplicativo ${WEBAPP_DOTNET} no mesmo plano"
  if existe az webapp show -g "$GRUPO" -n "$WEBAPP_DOTNET"; then
    reaproveitado "$WEBAPP_DOTNET"
  else
    # Aplicativo .NET nativo (não container): o Cloud Shell compila e publica, sem depender de ACR Tasks.
    # O App Service não cria mais apps com .NET 8 (fim do suporte em 10/11/2026): roda no .NET 10 (LTS), e a API,
    # compilada para net8.0, sobe nele com DOTNET_ROLL_FORWARD=Major (variável abaixo).
    az webapp create -g "$GRUPO" -p "$PLANO" -n "$WEBAPP_DOTNET" --runtime "DOTNETCORE:10.0" -o none
    criado "$WEBAPP_DOTNET"
  fi
  az webapp update -g "$GRUPO" -n "$WEBAPP_DOTNET" --https-only true -o none
  az webapp config set -g "$GRUPO" -n "$WEBAPP_DOTNET" --always-on true --min-tls-version 1.2 \
    --ftps-state Disabled --generic-configurations '{"healthCheckPath": "/health/live"}' -o none
  az webapp log config -g "$GRUPO" -n "$WEBAPP_DOTNET" --docker-container-logging filesystem -o none

  passo "API .NET: identidade gerenciada com leitura só dos segredos dela no ${KEYVAULT}"
  SENHA_CONEXAO_DOTNET="${APP_SQL_SENHA//\'/\'\'}"
  CONEXAO_DOTNET="Server=tcp:${SQL_SERVIDOR}.database.windows.net,1433;Database=${SQL_BANCO_DOTNET};User ID=${SQL_APP_USUARIO};Password='${SENHA_CONEXAO_DOTNET}';Encrypt=True;TrustServerCertificate=False;Connection Timeout=30"
  # A string de conexão tem a senha dentro: vai inteira para o cofre.
  guardar zeladoria-conexao-sql "$CONEXAO_DOTNET"
  PRINCIPAL_DOTNET="$(az webapp identity assign -g "$GRUPO" -n "$WEBAPP_DOTNET" --query principalId -o tsv)"
  leitura_dos_segredos "$PRINCIPAL_DOTNET" "$WEBAPP_DOTNET" jwt-secret zeladoria-conexao-sql

  passo "API .NET: variáveis de ambiente (mesmo JWT da API Java, banco ${SQL_BANCO_DOTNET}, senhas no cofre)"
  CONFIGURACOES_DOTNET=(
    "ASPNETCORE_ENVIRONMENT=Production"
    "DOTNET_ROLL_FORWARD=Major"
    "JWT_SECRET=$(ref jwt-secret)"
    "Jwt__Issuer=${JWT_ISSUER}"
    "Jwt__Audience=${JWT_AUDIENCE}"
    "Database__Provider=SqlServer"
    "Database__CriarTabelasAoIniciar=true"
    "ConnectionStrings__SqlServer=$(ref zeladoria-conexao-sql)"
    "ServicosExternos__ApiJavaHealthUrl=https://${WEBAPP}.azurewebsites.net/actuator/health/liveness"
    "Uploads__Pasta=/home/uploads"
    "WEBSITES_ENABLE_APP_SERVICE_STORAGE=true"
    "WEBSITE_HTTPLOGGING_RETENTION_DAYS=3"
  )
  az webapp config appsettings set -g "$GRUPO" -n "$WEBAPP_DOTNET" --settings "${CONFIGURACOES_DOTNET[@]}" -o none
  echo "    ${#CONFIGURACOES_DOTNET[@]} variáveis definidas (fotos em /home/uploads, que sobrevive aos deploys)"

  passo "API .NET: compilar ${DOTNET_REPO} (${DOTNET_BRANCH}) e publicar"
  for ferramenta in git dotnet zip; do
    command -v "$ferramenta" > /dev/null 2>&1 || { echo "${ferramenta} não encontrado. Rode no Azure Cloud Shell." >&2; exit 1; }
  done
  PASTA_DOTNET="$(mktemp -d)"
  git clone -q --depth 1 -b "$DOTNET_BRANCH" "$DOTNET_REPO" "${PASTA_DOTNET}/codigo"
  dotnet publish "${PASTA_DOTNET}/codigo/src/CaminhoLivre.Api/CaminhoLivre.Api.csproj" -c Release \
    -o "${PASTA_DOTNET}/publicado" --nologo -v q
  (cd "${PASTA_DOTNET}/publicado" && zip -qr "${PASTA_DOTNET}/zeladoria.zip" .)
  az webapp deploy -g "$GRUPO" -n "$WEBAPP_DOTNET" --src-path "${PASTA_DOTNET}/zeladoria.zip" --type zip -o none
  COMMIT_DOTNET="$(git -C "${PASTA_DOTNET}/codigo" rev-parse --short HEAD)"
  rm -rf "$PASTA_DOTNET"
  echo "    publicado o commit ${COMMIT_DOTNET}"
fi

ASSINATURA="$(az account show --query id -o tsv)"
# O App Service resolve as referências ao cofre com a identidade da API; logo depois de dar a permissão, a
# resolução pode falhar por alguns minutos. Confere e reinicia a API até todas ficarem resolvidas.
referencias_resolvidas() {
  local app="$1" tentativa pendentes
  for tentativa in 1 2 3 4 5 6 7 8 9 10; do
    pendentes=$(MSYS_NO_PATHCONV=1 az rest --method get \
      --url "https://management.azure.com/subscriptions/${ASSINATURA}/resourceGroups/${GRUPO}/providers/Microsoft.Web/sites/${app}/config/configreferences/appsettings?api-version=2022-03-01" \
      --query "value[?properties.status!='Resolved'].name" -o tsv 2> /dev/null || echo "erro")
    if [ -z "$pendentes" ]; then
      echo "    ${app}: todas as referências ao cofre resolvidas"
      return 0
    fi
    [ "$tentativa" = 1 ] && echo "    ${app}: esperando o App Service ler o cofre..."
    sleep 30
    az webapp restart -g "$GRUPO" -n "$app" -o none 2> /dev/null || true
  done
  echo "    ${app}: referências ainda não resolvidas (${pendentes//$'\n'/, }); confira em Configuração > Variáveis de ambiente" >&2
  return 1
}
passo "Key Vault: o App Service consegue ler as senhas do ${KEYVAULT}"
referencias_resolvidas "$WEBAPP"
[ "$PUBLICAR_DOTNET" = "sim" ] && referencias_resolvidas "$WEBAPP_DOTNET"

passo "Orçamento ${ORCAMENTO} do grupo ${GRUPO}: US\$ ${ORCAMENTO_USD} acumulados, alertas em 50%, 80% e na previsão de 100%"
# Azure for Students tem limite de gastos: quando o crédito acaba, os recursos param (nada é cobrado). O alerta
# avisa antes disso, para a API não sair do ar durante a avaliação.
# Período anual, e não mensal: o crédito não renova a cada mês, então o gasto tem que ser somado desde o início
# (um orçamento mensal zeraria na virada do mês e nunca chegaria ao alerta). E o orçamento é do grupo de
# recursos: mede só o custo deste ambiente e é apagado junto com o grupo pelo remover-azure.sh.
ORCAMENTO_EMAIL="${ORCAMENTO_EMAIL:-$(az account show --query user.name -o tsv)}"
INICIO_ORCAMENTO="$(date -u +%Y-%m-01)"
FIM_ORCAMENTO="$(date -u -d "${INICIO_ORCAMENTO} +1 year" +%Y-%m-%d)"
alerta() {
  printf '"%s": { "enabled": true, "operator": "GreaterThanOrEqualTo", "threshold": %s, "thresholdType": "%s", "contactEmails": ["%s"] }' \
    "$1" "$2" "$3" "$ORCAMENTO_EMAIL"
}
cat > /tmp/orcamento-vozviva.json <<JSON
{
  "properties": {
    "category": "Cost",
    "amount": ${ORCAMENTO_USD},
    "timeGrain": "Annually",
    "timePeriod": { "startDate": "${INICIO_ORCAMENTO}T00:00:00Z", "endDate": "${FIM_ORCAMENTO}T00:00:00Z" },
    "notifications": {
      $(alerta Gasto50 50 Actual),
      $(alerta Gasto80 80 Actual),
      $(alerta Previsao100 100 Forecasted)
    }
  }
}
JSON
if MSYS_NO_PATHCONV=1 az rest --method put -o none \
    --url "https://management.azure.com/subscriptions/${ASSINATURA}/resourceGroups/${GRUPO}/providers/Microsoft.Consumption/budgets/${ORCAMENTO}?api-version=2023-05-01" \
    --body @/tmp/orcamento-vozviva.json; then
  echo "    ok (alertas para ${ORCAMENTO_EMAIL})"
else
  echo "    não foi possível criar o orçamento; crie em Cost Management > Budgets (o resto do ambiente está pronto)"
fi
rm -f /tmp/orcamento-vozviva.json

passo "Pronto"
cat <<RESUMO
    API (depois do primeiro deploy): https://${WEBAPP}.azurewebsites.net
    Swagger:                         https://${WEBAPP}.azurewebsites.net/swagger-ui.html
    Health:                          https://${WEBAPP}.azurewebsites.net/actuator/health
    Azure SQL:                       ${SQL_SERVIDOR}.database.windows.net / banco ${SQL_BANCO}
                                     (API usa ${SQL_APP_USUARIO}; ${SQL_ADMIN} só para administração)
    Container Registry:              ${ACR_SERVIDOR}
    Key Vault (senhas):              ${KEYVAULT} (a senha do ${SQL_ADMIN} é o segredo sql-admin-senha)
    RabbitMQ (rede privada):         ${RABBITMQ_HOST}:5672
    API .NET (zeladoria):            https://${WEBAPP_DOTNET}.azurewebsites.net/swagger (se PUBLICAR_DOTNET=sim)

    Próximos passos no Azure DevOps (detalhes no README, seção "Deploy na Azure"):
    1. Service connections: "vozviva-azure" (Azure Resource Manager, grupo ${GRUPO})
       e "vozviva-acr" (Docker Registry -> Azure Container Registry ${ACR}).
    2. Library -> grupo de variáveis "vozviva-azure": webAppName=${WEBAPP}, acrLoginServer=${ACR_SERVIDOR}.
    3. Pipeline a partir do azure-pipeline.yml da raiz.
    Para apagar tudo e parar a cobrança: ./scripts/remover-azure.sh
RESUMO
