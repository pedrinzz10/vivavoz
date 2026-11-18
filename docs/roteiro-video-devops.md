# Roteiro do vídeo de DevOps

Guia passo a passo para a entrega de **DevOps Tools & Cloud Computing**: primeiro colocar tudo no ar na Azure,
depois gravar o vídeo com os 10 itens que o professor pede. Cada comando vem com uma explicação do que ele faz.

## Visão geral

| Fase | O que acontece | Tempo |
|---|---|---|
| **A. Preparação** (fora do vídeo) | Cria a IA na Oracle, a infraestrutura na Azure, liga o Azure DevOps à Azure e roda a pipeline uma vez | 40 a 60 min |
| **B. Gravação** (vídeo contínuo, sem cortes) | Os 10 itens obrigatórios, do README até a tarefa concluída | 15 a 20 min |
| **C. Depois de gravar** | Conferir o vídeo, montar o PDF e entregar | 20 min |

Nomes que vão aparecer (o sufixo `rm562312` deixa os nomes únicos na Azure):

| O quê | Nome |
|---|---|
| Grupo de recursos (a "pasta" com tudo na Azure) | `rg-vozviva` |
| API Java | `app-vozviva-rm562312` |
| API .NET | `app-zeladoria-rm562312` |
| Banco de dados (Azure SQL) | `sql-vozviva-rm562312`, com os bancos `vozviva` e `zeladoria` |
| Registro de imagens (Container Registry) | `acrvozvivarm562312` |
| Azure DevOps | organização `vozviva`, projeto `vozviva` |

### Como usar o Cloud Shell

O Cloud Shell é um terminal no navegador que já vem logado na sua conta. Há dois: o da **Oracle (OCI)**, aberto
pelo ícone `>_` no topo do console da OCI, e o da **Azure**, aberto pelo ícone `>_` no topo do portal da Azure
(escolha **Bash**).

- Cole **um comando por vez** e aperte Enter. Linhas longas costumam quebrar quando se cola várias de uma vez.
- Copie **só o que está dentro dos blocos cinza**, nunca o texto explicativo.
- Quando o Cloud Shell reinicia, ele **esquece as variáveis e a pasta baixada**. Por isso cada etapa começa
  baixando o projeto de novo.

---

## Fase A – Preparação (antes de gravar)

### A1. Criar a IA na Oracle (Cloud Shell da **OCI**)

```bash
git clone -q https://github.com/pedrinzz10/vivavoz.git ~/vivavoz
```
**O que faz:** baixa o projeto do GitHub para a pasta `vivavoz` do Cloud Shell.

```bash
cd ~/vivavoz
```
**O que faz:** entra na pasta do projeto, para os próximos comandos encontrarem os scripts.

```bash
ORCAMENTO_EMAIL=RM562312@fiap.com.br ./scripts/provisionar-oci.sh
```
**O que faz:** roda o script que prepara a IA na Oracle. Ele confere se o modelo `openai.gpt-oss-120b` existe em
São Paulo, cria um compartimento `vozviva` (uma "pasta" para organizar), cria a **chave de acesso da IA**, cria um
alerta de gastos que avisa no seu e-mail e faz uma pergunta de teste ao modelo.
**Resultado esperado:** a última parte mostra `o modelo respondeu (HTTP 200)`.

```bash
cat ~/.vozviva/oci-genai.env
```
**O que faz:** mostra na tela o arquivo onde o script guardou a chave. **Copie só o valor depois de
`OCI_GENAI_API_KEY=`** para usar na etapa A2. Não mostre essa tela no vídeo nem cole a chave em nenhum chat.

### A2. Criar a infraestrutura na Azure (Cloud Shell da **Azure**, em Bash)

```bash
git clone -q https://github.com/pedrinzz10/vivavoz.git ~/vivavoz
```
**O que faz:** baixa o projeto, agora no Cloud Shell da Azure.

```bash
cd ~/vivavoz
```
**O que faz:** entra na pasta do projeto.

```bash
export SUFIXO=rm562312 LIBERAR_MEU_IP=sim
```
**O que faz:** define duas configurações para o script. `SUFIXO` deixa os nomes dos recursos únicos;
`LIBERAR_MEU_IP=sim` libera o seu acesso ao banco, para você poder rodar os `SELECT` do vídeo.

```bash
export SQL_ADMIN_SENHA="$(openssl rand -hex 10)Aa1#"
```
**O que faz:** gera uma senha aleatória e forte para o administrador do banco (usada só para criar o banco).

```bash
export APP_SQL_SENHA="$(openssl rand -hex 10)Bb2#"
```
**O que faz:** gera a senha do usuário que a API usa no banco. Esse usuário só tem as permissões necessárias.

```bash
export RABBITMQ_SENHA="$(openssl rand -hex 16)"
```
**O que faz:** gera a senha da fila de mensagens (RabbitMQ), usada pelo botão de emergência.

```bash
export JWT_SECRET="$(openssl rand -hex 32)"
```
**O que faz:** gera o segredo que assina os tokens de login. As duas APIs (Java e .NET) recebem o mesmo valor, por
isso o mesmo login vale nas duas.

```bash
export USUARIOS_TESTE_SENHA="<uma senha com letra e número>"
```
**O que faz:** define a senha dos usuários de teste que a API cria (`cidadao.teste@vozviva.dev`,
`cuidador.teste@vozviva.dev` e os gestores). Troque o texto entre aspas pela senha que você quiser.

```bash
read -rs OCI_GENAI_API_KEY && export OCI_GENAI_API_KEY
```
**O que faz:** pede a chave da IA copiada na etapa A1. Cole e aperte Enter: **nada aparece na tela**, de
propósito, para a chave não ficar visível nem no histórico.

```bash
printf 'SQL admin: %s\nSQL app: %s\nRabbitMQ: %s\nJWT: %s\n' "$SQL_ADMIN_SENHA" "$APP_SQL_SENHA" "$RABBITMQ_SENHA" "$JWT_SECRET"
```
**O que faz:** mostra as senhas geradas. **Copie para um gerenciador de senhas** (ou um arquivo seguro): se o
Cloud Shell cair, você vai precisar das mesmas senhas. Depois limpe a tela:

```bash
clear
```
**O que faz:** apaga o que estava na tela, para as senhas não ficarem expostas.

```bash
./scripts/provisionar-azure.sh 2>&1 | tee ~/provisionar.log
```
**O que faz:** roda o script principal, que cria tudo na Azure: o grupo de recursos, o banco Azure SQL (com os
dois bancos e o usuário da aplicação), o registro de imagens, a rede privada, o RabbitMQ, o App Service com as
duas APIs (a .NET já é compilada e publicada aqui) e o alerta de gastos. O `tee` guarda tudo o que aparece na tela
no arquivo `provisionar.log`, para consultar depois.
**Tempo:** 15 a 20 minutos. **Deixe a aba aberta e em primeiro plano**: o Cloud Shell desconecta se ficar parado.
**Resultado esperado:** termina com o título `Pronto` e um resumo com os endereços.

```bash
az resource list -g rg-vozviva --query "[].{nome:name,tipo:type}" -o table
```
**O que faz:** lista tudo o que foi criado no grupo `rg-vozviva`, para conferir.

Se der erro, veja onde parou:

```bash
tail -40 ~/provisionar.log
```
**O que faz:** mostra as 40 últimas linhas do log, onde aparece a mensagem de erro.

### A3. Ligar o Azure DevOps à Azure (navegador)

A pipeline precisa de duas "service connections", que são as permissões para o Azure DevOps publicar na Azure.
Abra `https://dev.azure.com/vozviva/vozviva/_settings/adminservices` e clique em **New service connection**:

1. **Azure Resource Manager** → **Workload identity federation (automatic)**
   - Subscription: `2tdspw-rm562312-pedrooliveira`; Resource group: `rg-vozviva`
   - Nome: **`vozviva-azure`** (exatamente assim: a pipeline procura por esse nome)
   - Marque **Grant access permission to all pipelines** e salve
2. **Docker Registry** → **Azure Container Registry**
   - Subscription: a mesma; registro: `acrvozvivarm562312`
   - Nome: **`vozviva-acr`**
   - Marque **Grant access permission to all pipelines** e salve

**Por que marcar "Grant access…":** sem isso, na primeira execução a pipeline para e pede autorização. Se isso
acontecer no vídeo, ela não roda "de forma automática", como o professor exige.

### A4. Criar o grupo de variáveis da pipeline (Cloud Shell da **Azure**)

```bash
cd ~/vivavoz
```
**O que faz:** volta para a pasta do projeto (se o Cloud Shell reiniciou, faça antes o `git clone` da etapa A2).

```bash
export ORGANIZACAO=vozviva REVISOR_EMAIL=RM562312@fiap.com.br
```
**O que faz:** informa ao script a organização do Azure DevOps e quem é o revisor obrigatório da main.

```bash
export WEBAPP_NAME=app-vozviva-rm562312
```
**O que faz:** informa o nome do App Service da API Java, para onde a pipeline vai publicar.

```bash
export ACR_LOGIN_SERVER=acrvozvivarm562312.azurecr.io
```
**O que faz:** informa o endereço do registro de imagens, para onde a pipeline vai enviar a imagem.

```bash
./scripts/configurar-azure-devops.sh
```
**O que faz:** confere o projeto, o repositório, a pipeline, as proteções da main e a tarefa inicial (tudo isso
já existe e é só reaproveitado) e **cria o grupo de variáveis `vozviva-azure`** com os dois valores acima. As
variáveis ficam no Azure DevOps, nunca no código.
Os professores (`pf1776@fiap.com.br` de DevOps e `pf1972@fiap.com.br` de QA) **já foram convidados**.

### A5. Atualizar o Azure Repos com o GitHub

O vídeo mostra o código e o README a partir do Azure Repos, então ele precisa estar igual ao GitHub. **Peça para o
Claude fazer a sincronização** (ele envia a branch `sync/github` e abre o PR). Você só abre o PR no navegador,
clica em **Approve** e depois em **Complete**, escolhendo **Rebase and fast-forward**.

### A6. Rodar a pipeline uma vez (ensaio)

1. Abra `https://dev.azure.com/vozviva/vozviva/_build`, clique em **vozviva-ci-cd** → **Run pipeline** → branch
   `main` → **Run**.
2. Se aparecer **"This pipeline needs permission to access a resource"**, clique em **View** e depois em
   **Permit** em cada item.
3. Espere os dois estágios ficarem verdes (6 a 10 minutos). Depois confira no Cloud Shell:

```bash
curl -s https://app-vozviva-rm562312.azurewebsites.net/actuator/health/readiness
```
**O que faz:** pergunta à API publicada se ela está pronta (e com o banco funcionando).
**Resultado esperado:** `{"status":"UP"}`.

### A7. Antes de apertar o REC

Deixe estas abas abertas, na ordem em que vão aparecer:

| Aba | Endereço |
|---|---|
| README no Azure Repos | `https://dev.azure.com/vozviva/vozviva/_git/vozviva` |
| Desenho no draw.io | [abrir `docs/arquitetura.drawio`](https://app.diagrams.net/#Uhttps%3A%2F%2Fraw.githubusercontent.com%2Fpedrinzz10%2Fvivavoz%2Fmain%2Fdocs%2Farquitetura.drawio) |
| Portal Azure | `https://portal.azure.com` → Resource groups → `rg-vozviva` |
| Boards | `https://dev.azure.com/vozviva/vozviva/_boards/board` |
| Pipelines | `https://dev.azure.com/vozviva/vozviva/_build` |
| Swagger publicado | `https://app-vozviva-rm562312.azurewebsites.net/swagger-ui.html` |
| Query editor do banco | Portal → SQL databases → `vozviva` → **Query editor**, entrando com o usuário `vozvivaadmin` e a `SQL_ADMIN_SENHA` (se pedir, clique em **Allowlist IP**) |

**Checklist técnico:** resolução de 1280×720 ou mais, microfone testado, **narração falada** (legendas no lugar
da voz tiram 30 pontos), notificações desligadas e **nenhuma senha na tela**.

---

## Fase B – Gravação (sem cortes)

Cada item diz o que **fazer**, o que **mostrar** e uma sugestão do que **falar**. A pipeline leva alguns minutos:
aproveite para comentar o que cada etapa faz.

### Item 1 – README, solução e desenho da arquitetura (≈ 3 min)

**Mostrar:** o README no Azure Repos e depois o desenho no draw.io. O professor avalia **desenho, fluxo e
legenda** (10 pontos).

1. **O problema**, na seção "O problema e quem a solução atende":
   > "O Brasil tem 32 milhões de pessoas com mais de 60 anos e 14 milhões de pessoas com deficiência, e 68,8% dos
   > moradores urbanos vivem em ruas sem rampa. Elas ficam presas em casa e mais expostas a golpes."
2. **A solução**, na tabela de "A solução":
   > "O VozViva é a API Java do ecossistema Fala Cidadão. A pessoa usa o app falando ou com um toque: pede socorro
   > e a rede de apoio é avisada em segundos, confere se uma mensagem é golpe com IA, e acha o atendimento
   > acessível mais perto, com a rota no celular."
3. **O desenho**: apresente as áreas e depois **aponte cada número** enquanto fala.
   - **Áreas:** "Em azul, o grupo de recursos na Azure, em São Paulo, criado pelo script. Dentro dele, o App
     Service Plan roda as duas APIs; ao lado, o RabbitMQ, a rede privada, o Azure SQL com dois bancos e o
     registro de imagens. Em laranja, os sistemas de fora: o app, a IA na Oracle e o OpenStreetMap. Em verde, o
     Azure DevOps."
   - **1** "O app chama a API Java por HTTPS, com o token de login."
   - **2** "A API Java grava no banco `vozviva`; a API .NET usa o banco `zeladoria`. Cada uma com um usuário de
     permissão mínima."
   - **3** "No botão de emergência, a API grava, responde na hora e manda o evento para o RabbitMQ, que só existe
     na rede privada. Em segundo plano, os contatos são avisados, com novas tentativas se algo falhar."
   - **4** "A verificação de golpes e a voz usam IA generativa na Oracle, pelo Spring AI, com RAG. Sem IA, regras
     respondem."
   - **5** "O app também usa a API .NET de zeladoria, que aceita o mesmo login."
   - **6** "No Azure DevOps, a tarefa vira branch e pull request; o merge dispara o build com os testes, a imagem
     vai para o registro e a release publica na Azure."
   - **7** "Os locais de atendimento vêm do OpenStreetMap, carregados no banco."
   - **Legenda:** "Linha cheia é chamada direta; tracejada é assíncrona; verde é build e deploy; laranja é a IA."

### Item 2 – Recursos criados pelos scripts (≈ 1 min)

**Mostrar:** o arquivo `scripts/provisionar-azure.sh` no Repos (role rápido) e, no portal, o grupo `rg-vozviva`
com os recursos: os dois App Services, o plano, o Azure SQL, o registro de imagens, o RabbitMQ (Container
Instance) e a rede virtual.

> "Tudo isso foi criado por este script, com comandos Azure CLI. Ele pode rodar de novo sem duplicar nada. O
> RabbitMQ não tem endereço público: só as APIs chegam até ele, pela rede privada."

### Item 3 – Nova tarefa no Boards (≈ 30 s)

**Fazer:** Boards → **New item → Task** → título **"Descrever no Swagger que o cidadão também mantém o perfil"** →
**Save**. **Anote o número da tarefa** (por exemplo, 101).

### Item 4 – Nova branch e alteração no código-fonte (≈ 2 min)

**Fazer:**
1. Na tarefa, em **Development**, clique em **Create a branch**: nome `feature/swagger-perfil`, a partir da
   `main`. Assim a branch já fica ligada à tarefa.
2. No Repos, escolha a branch `feature/swagger-perfil`, abra
   `src/main/java/br/com/fiap/vozviva/config/OpenApiConfig.java` e clique em **Edit**. Na linha 81, troque:

```java
new Tag().name(TAG_PERFIL).description("Perfil de emergência mantido pelo cuidador"),
```
por:
```java
new Tag().name(TAG_PERFIL).description("Perfil de emergência mantido pelo cidadão ou pelo cuidador"),
```
**O que muda:** só o texto que descreve o módulo de perfil na documentação (Swagger). Hoje o próprio cidadão
também mantém o perfil, então o texto antigo estava desatualizado.

3. Clique em **Commit** com a mensagem `Swagger: o cidadão também mantém o perfil #101` (troque 101 pelo número da
   tarefa). O `#101` liga o commit à tarefa.

> "A alteração é no código-fonte, não no README, e fica ligada à tarefa."

### Item 5 – Merge na main e pipelines automáticas (≈ 1 min)

**Fazer:** **Create a pull request** para a `main` → confira a tarefa em **Work items** → **Create** →
**Approve** → **Complete**, marcando **Complete associated work items after merging** e **Delete branch** →
**Complete merge**.

> "A main é protegida: só recebe código por pull request, com revisor e tarefa vinculada. O merge dispara a
> pipeline sozinho."

### Item 6 – Execução completa das pipelines (≈ 6 a 10 min)

**Mostrar:** em Pipelines, a execução que acabou de começar (gatilho *Individual CI*, branch `main`). Abra cada
estágio e comente:

- **Build e testes (CI):** compila com o Gradle, roda centenas de testes (incluindo testes com banco SQL Server e
  RabbitMQ de verdade), mede a cobertura, gera o pacote e envia a imagem para o registro.
- **Deploy no App Service (CD):** troca a imagem da API Java pela nova e confere se a API respondeu "pronta".

### Item 7 – Artefatos da CI (≈ 1 min)

**Mostrar,** na execução:

- aba **Tests**: os testes publicados, todos aprovados;
- aba **Code Coverage**: a cobertura dos testes;
- **Related → Published** → artefato **`vozviva`**, com o `vozviva.jar` (a API), o `openapi.json` (contrato da API)
  e o `script-bd.sql` (o banco);
- no portal, o registro de imagens → **Repositories** → `vozviva-api`, com a tag do número do build.

### Item 8 – Alteração publicada na nuvem (≈ 30 s)

**Mostrar:** atualize o Swagger publicado. O módulo de perfil agora diz **"mantido pelo cidadão ou pelo
cuidador"**.

### Item 9 – CRUD em três tabelas, conferindo direto no banco (≈ 4 min)

**Regra da disciplina:** para provar que gravou, use **`SELECT` no Query editor**, nunca o GET. Faça o `SELECT`
depois de **cada** operação. Faltar qualquer operação tira 30 pontos.

**9.1 Tabela `usuarios`** (cadastro e login não precisam de token)

| Operação | No Swagger | Corpo da requisição |
|---|---|---|
| **Create** | `POST /api/v1/auth/cadastro` | `{"nome": "Maria da Silva", "email": "maria.video@vozviva.dev", "senha": "girassol2026", "papel": "CIDADAO", "consentimento": true}` |
| (login) | `POST /api/v1/auth/login` | `{"email": "maria.video@vozviva.dev", "senha": "girassol2026"}`. Copie o `accessToken` e cole em **Authorize** |
| **Read** | `GET /api/v1/auth/eu` | — |
| **Update** | `PUT /api/v1/auth/eu` | `{"nome": "Maria da Silva Souza"}` |

```sql
SELECT id, nome, email, papel, consentimento_em FROM usuarios WHERE email = 'maria.video@vozviva.dev';
```
**O que faz:** mostra a linha da Maria na tabela de usuários (rode depois do Create e depois do Update).
O **Delete** desta tabela fica para o fim (9.4), porque apagar a conta apaga também o perfil e os contatos.

**9.2 Tabela `perfis_emergencia`** (com o token da Maria)

| Operação | No Swagger | Corpo da requisição |
|---|---|---|
| **Create** | `POST /api/v1/perfis-emergencia` | `{"mobilidade": "CADEIRANTE", "condicoesSaude": "Hipertensão", "medicamentos": "Losartana 50 mg", "observacoes": "Mora sozinha"}` |
| **Read** | `GET /api/v1/perfis-emergencia/meu` | — (anote o `id` do perfil) |
| **Update** | `PUT /api/v1/perfis-emergencia/{id}` | `{"mobilidade": "ANDADOR", "condicoesSaude": "Hipertensão", "medicamentos": "Losartana 100 mg", "observacoes": "Mora sozinha"}` |

```sql
SELECT id, cidadao_id, mobilidade, medicamentos, atualizado_em FROM perfis_emergencia ORDER BY id DESC;
```
**O que faz:** mostra os perfis, do mais novo para o mais antigo. O primeiro é o da Maria.

**9.3 Tabela `contatos_emergencia`** (use o `id` do perfil no lugar de `{perfilId}`)

| Operação | No Swagger | Corpo da requisição |
|---|---|---|
| **Create** | `POST /api/v1/perfis-emergencia/{perfilId}/contatos` | `{"nome": "Ana Souza", "telefone": "(11) 91234-5678", "parentesco": "Filha", "prioridade": 1}` |
| **Read** | `GET /api/v1/perfis-emergencia/{perfilId}/contatos` | — (anote o `id` do contato) |
| **Update** | `PUT /api/v1/perfis-emergencia/{perfilId}/contatos/{contatoId}` | `{"nome": "Ana Souza", "telefone": "(11) 98888-7777", "parentesco": "Filha", "prioridade": 2}` |
| **Delete** | `DELETE /api/v1/perfis-emergencia/{perfilId}/contatos/{contatoId}` | — |

```sql
SELECT id, perfil_id, nome, telefone, prioridade FROM contatos_emergencia ORDER BY id DESC;
```
**O que faz:** mostra os contatos. Repare que o telefone fica gravado no padrão `+5511912345678`.

**9.4 Os deletes que faltam**

| Operação | No Swagger | Conferência no banco |
|---|---|---|
| **Delete** do perfil | `DELETE /api/v1/perfis-emergencia/{id}` | `SELECT * FROM perfis_emergencia WHERE id = <id>;` → nenhuma linha |
| **Delete** do usuário | `DELETE /api/v1/auth/eu` | `SELECT * FROM usuarios WHERE email = 'maria.video@vozviva.dev';` → nenhuma linha |

> "Cada operação foi conferida direto no Azure SQL. A exclusão da conta atende a LGPD: apaga a conta e tudo o
> que depende dela, e o token antigo para de valer na hora."

### Item 10 – Tarefa concluída com os links (≈ 30 s)

**Mostrar:** Boards → a tarefa do item 3 → estado **Done** → seção **Development**, com a **branch**, o
**commit** e o **pull request**. Se ela não fechou sozinha, mude o estado para **Done** na hora.

> "A tarefa virou branch, commit e pull request, passou pelos testes e foi publicada na Azure automaticamente."

---

## Fase C – Depois de gravar

1. **Assista ao vídeo já publicado no YouTube** (público ou não listado): 720p, áudio claro e os 10 itens.
2. **Peça para o Claude trazer a alteração do vídeo para o GitHub.** Ela foi feita no Azure Repos e precisa voltar
   para o GitHub.
3. **Monte o PDF** `VozViva_rm562312.pdf` a partir de `docs/entrega/devops-folha-de-rosto.md`: folha de rosto com
   o nome do grupo e o RM e nome completo dos integrantes, o link da organização (`https://dev.azure.com/vozviva`)
   e o link do vídeo.
4. **Mantenha tudo no ar até a correção (30/11).** Depois, para parar o consumo dos créditos:

```bash
./scripts/remover-azure.sh
```
**O que faz:** mostra o que vai ser apagado, pede para você digitar o nome do grupo para confirmar e apaga o
grupo `rg-vozviva` inteiro (com o alerta de gastos). Não tem volta: os dados do banco são perdidos.

---

## Problemas comuns

| O que aconteceu | O que fazer |
|---|---|
| O comando colado deu erro estranho (`command not found`, `expected one argument`) | A linha quebrou ao colar. Cole de novo, um comando por vez, ou digite à mão |
| O Cloud Shell reiniciou no meio | Refaça o `git clone` e o `cd`, exporte as variáveis **com as mesmas senhas guardadas** e rode o script de novo: ele continua de onde parou |
| O `provisionar-azure.sh` parou com erro | Rode `tail -40 ~/provisionar.log` e mande as linhas para o Claude (confira antes que não há senha nelas) |
| A pipeline pediu **Permit** | Clique em **View** e em **Permit** em cada item. Se for no vídeo, a pipeline não conta como automática: grave de novo |
| O Query editor não entra | Clique em **Allowlist IP** quando ele pedir, ou rode o provisionamento de novo com `LIBERAR_MEU_IP=sim` |
| O Swagger publicado não abre logo depois do deploy | A API leva 1 ou 2 minutos para subir. Espere e atualize a página |

## O que tira ponto e onde está coberto

| Item | Desconto | Coberto em |
|---|---|---|
| Código, `azure-pipeline.yml`, scripts, `script-bd.sql`, Dockerfile e pastas `scripts` e `dockerfiles` no repositório | −70 a −10 | Já atendido |
| App ou banco em localhost | −70 | Etapa A2 |
| Professor sem acesso ao Azure DevOps | −70 | Já atendido (professores convidados) |
| Branch principal desprotegida | −20 | Já atendido |
| Sem variáveis de ambiente ou senhas no código | −20 | Etapas A2 e A4 |
| Sem testes automáticos na CI | −20 | Itens 6 e 7 |
| Não criou branch para a tarefa | −10 | Item 4 |
| Alteração no README em vez do código | −20 | Item 4 |
| CRUD sem `SELECT` no banco em nuvem | −30 | Item 9 |
| Vídeo sem narração, abaixo de 720p ou com áudio ruim | −30 | Etapa A7 |
| Item obrigatório faltando no vídeo | −20 cada | Itens 1 a 10 |
| Entrega fora do prazo (19/11, 23h55) | −70 | Fase C |
