# Global Solution 2026/2 — CitizenTech
**2º ano · Análise e Desenvolvimento de Sistemas · Turmas de Fevereiro (2TDS)**

> Tecnologia com o cidadão no centro da inovação.

---

## Visão geral

| Item | Detalhe |
|---|---|
| Peso | 60% da nota do semestre |
| Disciplinas | 7 — **uma única solução** serve de base para todas |
| Kickoff | 04/11 |
| Cadastro dos grupos | **até 13/11** (RM, nome completo e turma de cada integrante) |
| Entrega | **19/11/2026, até 23h55**, no Portal do Aluno — sem prorrogação |
| Apresentações | 23 a 27/11, presencial, na aula da disciplina (30% da nota da disciplina) |
| Correção | até 30/11 |
| Grupo | individual ou até 5 integrantes (pode misturar turmas/turnos) |
| Identificação | um `.txt` em cada `.zip` com RM, nome completo e turma de cada integrante |

**Grupos não cadastrados no prazo não aparecem no portal de entrega.**

### Uma boa proposta deixa claro
1. Quem é o cidadão beneficiado
2. Qual problema será resolvido
3. Como a solução vai funcionar
4. Quais tecnologias serão usadas
5. Quem participa do ecossistema
6. Como o impacto será medido e ampliado

A solução deve ser simples, segura, inclusiva e acessível: proteger dados, respeitar diferenças e continuar útil mesmo fora do cenário ideal de infraestrutura e conectividade.

**ODS:** o projeto deve identificar a quais ODS da ONU está conectado (2, 3, 4, 9, 10, 11, 13) e explicar como contribui para suas metas.

### Uso de IA generativa
- **Permitido:** apoio para pesquisa, revisão de texto, explicação de erros.
- **Não permitido:** entregar código ou texto gerado sem entender e sem revisar.
- **Como declarar:** seção no README listando as ferramentas usadas e para quê.

### Premiação
Shape e camisetas exclusivas. Elegível quem tiver **nota ≥ 90 em todas as disciplinas e no vídeo pitch de Java Advanced**; Scrum Masters, professores e coordenação escolhem um único vencedor.

---

## Sumário das disciplinas
1. [Advanced Business Development with .NET](#1-advanced-business-development-with-net)
2. [Compliance, Quality Assurance & Tests](#2-compliance-quality-assurance--tests)
3. [DevOps Tools & Cloud Computing](#3-devops-tools--cloud-computing)
4. [Disruptive Architectures: IoT, IoB & Generative IA](#4-disruptive-architectures-iot-iob--generative-ia)
5. [Java Advanced](#5-java-advanced)
6. [Mastering Relational and Non-Relational Database](#6-mastering-relational-and-non-relational-database)
7. [Mobile Application Development](#7-mobile-application-development)

---

## 1. Advanced Business Development with .NET

### Detalhes
O grupo desenvolve, em .NET, o backend da CitizenTech. Essa API é a mesma consumida pelo app de Mobile e precisa ter uma funcionalidade que agregue valor real ao cidadão.

**Tecnologias obrigatórias:** ASP.NET Web API ou Minimal API, EF Core ou Dapper.

### Requisitos
- [ ] API RESTful com ASP.NET Core Minimal APIs, com endpoints, validações e códigos HTTP adequados
- [ ] Documentação interativa com OpenAPI/Swagger (parâmetros, contratos de entrada e saída, respostas HTTP)
- [ ] Idempotência: tratamento de requisições repetidas, evitando operações duplicadas e efeitos colaterais
- [ ] Rate limiting com os recursos do ASP.NET Core (proteção contra uso excessivo)
- [ ] Autenticação e controle de acesso com JWT
- [ ] HealthChecks da API
- [ ] Testes unitários (regras de negócio) e de integração (endpoints) com xUnit

### Pontuação (100 pts)
| # | Critério | Pts |
|---|---|---|
| 1 | Minimal API RESTful funcional, com validações, códigos HTTP e OpenAPI/Swagger | 20 |
| 2 | Autenticação e autorização com JWT, protegendo endpoints e validando tokens | 20 |
| 3 | Idempotência para evitar operações duplicadas | 15 |
| 4 | Rate Limiting funcional, com limites e tratamento HTTP 429 | 15 |
| 5 | Testes unitários e de integração com xUnit.net | 20 |
| 6 | Health Checks da API, banco de dados e serviços externos | 10 |

### O que tira pontos
| Item | Desconto |
|---|---|
| Entrega fora do prazo ou fora do Portal do Aluno | −30 |
| Arquivo corrompido, incompleto ou sem o `.txt` de identificação | −100 |
| Solução sem relação com o tema CitizenTech | −100 |
| API não executa ou tem falhas que impedem o uso | −20 |
| Projeto sem organização mínima, código não estruturado ou credenciais expostas | −20 |

### Entregas
- Repositório no GitHub com README
- PowerPoint da apresentação (se houver)
- **Formato:** `.zip` com o link do GitHub e o `.txt` de identificação

### Apresentação (30% da nota · 100 pts)
> No slide consta "Apresentação online"; a regra geral diz presencial, 23 a 27/11. Confirme com o professor.

| # | Critério | Pts |
|---|---|---|
| 1 | Apresentação mostrando o funcionamento do sistema | 20 |
| 2 | Respostas sobre a estrutura do projeto | 20 |
| 3 | Respostas sobre descrições e critérios de design do projeto | 20 |
| 4 | Execução dos fluxos principais | 20 |
| 5 | Explicar como os componentes do .NET funcionam na implementação | 10 |
| 6 | Explicar como a solução aborda o tema da GS | 10 |

---

## 2. Compliance, Quality Assurance & Tests

### Detalhes
- **Conceito:** atividades de planejamento e qualificação de software para organizar a execução do projeto e controlar a qualidade das entregas.
- **Objetivo:** praticar definição de requisitos e criação de testes funcionais para avaliar e auditar a produção de software.
- **Entregáveis:**
  - Features da aplicação, com descrições e critérios de aceite, nos padrões ensinados em aula.
  - Todos os Test Cases funcionais, positivos e negativos, caixa-preta, necessários para aceitação/homologação, com objetivo, preparação, inputs, outputs esperados e passos.

### Requisitos (Azure DevOps / Excel)
- [ ] Backlog de produto com Epics, Features e Product Backlog Items
- [ ] Descrição e critério de aceite de Features e PBIs
- [ ] Test Cases funcionais de sistema com Sumário (objetivo, preparação, inputs e outputs)
- [ ] Passos de teste com ações do testador e respostas do sistema, para cada Feature
- [ ] Todos os testes positivos e negativos de caixa-preta

### Pontuação (vale 70% da nota · 100 pts)
| # | Critério | Pts |
|---|---|---|
| 1 | **Backlog:** estrutura com títulos de Epic, Feature e PBIs | 20 |
| 2 | **Backlog:** descrição e critério de aceite por Feature | 20 |
| 3 | **Testes:** lista de Test Cases positivos e negativos por Feature | 20 |
| 4 | **Testes:** sumário dos Test Cases | 20 |
| 5 | **Testes:** passos dos Test Cases | 20 |

### O que tira pontos
| Item | Desconto |
|---|---|
| Entrega fora do prazo ou fora do Portal do Aluno | −10 |
| Arquivo corrompido, incompleto ou sem o `.txt` de identificação | −10 |
| Solução sem relação com o tema CitizenTech | **Nota zero** |
| Não usar a ferramenta e os métodos indicados em aula | **Nota zero** |

### Entregas
PDF contendo:

**Plano de projeto Scrum (Azure DevOps)**
- [ ] Link do projeto no Azure Boards, com o professor convidado como **ADMIN**
- [ ] Print da tela principal de planejamento (Backlogs)
- [ ] Print de cada tela com descrição e critério de aceite de cada Feature e cada PBI

**Plano de testes (no mesmo PDF)**
- [ ] Print da tela Boards com a lista de Test Cases por Feature
- [ ] Print da tela de Sumário e da tela de Passos de cada Test Case

**Formato:** `.zip` com o PDF (Plano de Projeto + Plano de Testes) e o `.txt` de identificação.

### Apresentação presencial (30% da nota · 100 pts)
| # | Critério | Pts |
|---|---|---|
| 1 | Apresentação operando o Azure DevOps | 20 |
| 2 | Respostas sobre a estrutura do projeto | 20 |
| 3 | Respostas sobre descrições e critérios de aceite de Features e PBIs | 20 |
| 4 | Operar Test Plans executando os testes manuais planejados | 20 |
| 5 | Apresentar relatórios analíticos de testes do Azure DevOps | 20 |

---

## 3. DevOps Tools & Cloud Computing

### Detalhes
Implementar em nuvem a solução de **Java Advanced** ou **Advanced Business Development with .NET**, usando Azure DevOps (Boards, Repos e Pipelines). Infraestrutura provisionada via **Azure CLI (script)**.

### Tarefas obrigatórias
1. **Provisionamento (Azure CLI):** criar via script os recursos (Resource Group, banco, Web App, container etc.). Build e Release devem usar esses recursos.
2. **Projeto no Azure DevOps:** criar novo projeto e convidar o professor com permissões **Organização: Basic** e **Projeto: Project Administrators** (Organization Settings → Users → Add new users).
3. **Azure Repos:** importar ou criar o repositório com o código da solução escolhida.
4. **Azure Boards:** criar uma Tarefa inicial e vincular commits, branches e PRs.
5. **Pipeline de Build (CI):** YAML ou Classic; roda automaticamente a cada commit na branch principal; publica artefatos (pacote) e testes (JUnit, xUnit, Selenium etc.).
6. **Pipeline de Release:** deploy automático na nuvem, disparado após a CI gerar novo artefato.

### Requisitos
- [ ] Projeto privado, com Git
- [ ] Boards vinculado ao Repos (commits, branches, PRs)
- [ ] Branch principal protegida: revisor obrigatório, vinculação de Work Item, revisor padrão (seu RM)
- [ ] Build acionado somente após merge na main via PR (o aluno pode aprovar a própria PR)
- [ ] Release automática após novo artefato
- [ ] Deploy via Container (ACI/ACR) ou Serviço de Aplicativo
- [ ] Banco em Container (ACI/ACR) ou PaaS (Azure SQL ou Oracle)
- [ ] Scripts de infraestrutura no repositório
- [ ] `script-bd.sql` (DDL) na pasta `/scripts`
- [ ] Scripts Azure CLI na pasta `/scripts`
- [ ] Dockerfiles (se usar Docker) na pasta `/dockerfiles`
- [ ] `azure-pipeline.yml` na raiz (se usar YAML)
- [ ] JSON do CRUD no README (para APIs sem Swagger)
- [ ] Variáveis de ambiente nas pipelines
- [ ] Desenho macro da arquitetura
- [ ] Testes automáticos na CI

### Vídeo (YouTube)
**Requisitos técnicos:** mínimo 720p, áudio claro, explicação narrada por voz (sem legendas).

Etapas obrigatórias:
- [ ] Apresentar o README: solução, conceito e desenho da arquitetura
- [ ] Mostrar no Portal Azure os recursos criados pelos scripts
- [ ] Criar uma nova tarefa no Boards
- [ ] Criar nova branch e alterar o **código-fonte** (não o README)
- [ ] Fazer merge na main e disparar as pipelines (Build + Release) automaticamente
- [ ] Mostrar a execução completa das pipelines
- [ ] Destacar os artefatos publicados na CI (pacote, testes etc.)
- [ ] Demonstrar a alteração publicada na nuvem
- [ ] Testar CRUD em pelo menos duas tabelas
- [ ] Finalizar mostrando a tarefa concluída com os links (commits, PR etc.)

**Dicas:** grave de forma contínua, sem cortes; demonstre todas as operações CRUD de cada tabela e mostre os resultados **diretamente no banco** (use SELECT, não GET); assista ao vídeo já postado no YouTube antes de entregar.

### Pontuação (70 pts)
| # | Critério | Pts |
|---|---|---|
| 1 | Desenho da arquitetura macro (desenho, fluxo, legenda) | 10 |
| 2 | Azure Boards e Repos (histórico, links / branch, commit, merge) | 10 |
| 3 | Azure Pipelines — Build (etapas, tarefas, testes) | 25 |
| 4 | Azure Pipelines — Release (etapas, tarefas) | 25 |

### O que tira pontos
| Item | Desconto |
|---|---|
| Sem código-fonte no repositório | −70 |
| Sem `azure-pipeline.yml` (quando usar YAML) | −70 |
| App ou banco em localhost (não hospedado em nuvem) | −70 |
| Sem acesso ao projeto no Azure DevOps (Boards, Repos ou Pipelines) | −70 |
| Projeto sem uso do Azure DevOps | −70 |
| Plágio ou cópia entre equipes (zero para todos os envolvidos) | −70 |
| Entrega fora da data/horário (não será corrigida) | −70 |
| Vídeo sem narração ou com legendas no lugar da fala | −30 |
| Sem `script-bd.sql` | −10 |
| Sem Dockerfiles (quando usar containers) | −30 |
| Sem JSON dos CRUDs de cada tabela (APIs sem Swagger) | −10 |
| Sem scripts Azure CLI | −30 |
| Item obrigatório omitido no vídeo | −20 por item |
| Vídeo abaixo de 720p ou áudio sem clareza | −30 |
| Sem demonstração de CRUD de cada tabela direto no banco em nuvem (qualquer operação faltando) | −30 |
| Sem variáveis de ambiente ou senhas expostas no código | −20 |
| Branch principal desprotegida | −20 |
| Não criou nova branch para a tarefa | −10 |
| Usar apenas uma tabela | −30 |
| Alteração simulada no README em vez do código | −20 |
| Falta da pasta `scripts` ou `dockerfiles` (se usar Docker) | −10 |
| Sem testes automáticos na CI | −20 |

### Entregas
PDF com:
- [ ] Folha de rosto com nome do grupo, RM e nome completo dos integrantes
- [ ] Link da Organização no Azure DevOps
- [ ] Link do vídeo no YouTube

**Formato:** PDF nomeado com grupo e RM do representante — ex.: `Grupo1_rm9999.pdf`

### Apresentação presencial (30 pts)
| # | Critério | Pts |
|---|---|---|
| 1 | Mostrar a aplicação funcionando | 10 |
| 2 | Explicar a arquitetura | 10 |
| 3 | Perguntas sobre a construção e uso do Azure DevOps | 10 |

---

## 4. Disruptive Architectures: IoT, IoB & Generative IA

### Detalhes
Desenvolver uma funcionalidade de IA integrada à CitizenTech, com benefício claro para o público escolhido. Deve usar **IA generativa** e um aprofundamento em **RAG básico, Machine Learning, Deep Learning e/ou Visão Computacional**. Pode usar APIs, modelos locais ou pré-treinados, justificando as escolhas. Avaliam-se funcionamento, integração, qualidade dos resultados e compreensão das limitações.

### Requisitos
- [ ] **Aplicação ao problema:** definir a funcionalidade de IA, o público beneficiado e a contribuição para a solução
- [ ] **IA generativa:** modelo generativo no fluxo da aplicação (API ou execução local)
- [ ] **Aprofundamento técnico:** RAG básico, ML, DL e/ou Visão Computacional, com justificativa (não precisa usar todos)
- [ ] **Dados e arquitetura:** documentar fontes, preparação dos dados e fluxo entre aplicação, modelos e serviços
- [ ] **Avaliação:** testar entradas representativas e situações de falha, com métricas ou critérios adequados
- [ ] **Organização e uso responsável:** código e instruções de execução, proteção de credenciais e dados pessoais, limitações da IA explícitas

### Pontuação (100 pts)
| # | Critério | Pts |
|---|---|---|
| 1 | Adequação da funcionalidade ao problema e ao público | 10 |
| 2 | IA generativa funcionando e integrada | 25 |
| 3 | Implementação e justificativa do aprofundamento | 25 |
| 4 | Organização dos dados e documentação da arquitetura | 15 |
| 5 | Testes, avaliação e interpretação dos resultados | 15 |
| 6 | Organização, reprodução e uso responsável | 10 |

### O que tira pontos
| Item | Desconto |
|---|---|
| Entrega fora do prazo ou fora do Portal do Aluno | **Nota zero** |
| Arquivo corrompido ou links sem acesso | **Nota zero** |
| Sem `.txt` de identificação ou identificação incompleta | −10 |
| Solução sem relação com o tema CitizenTech | −40 |
| Credenciais expostas ou dados pessoais sem os devidos cuidados | −10 |

Requisitos não atendidos perdem a pontuação correspondente, sem desconto adicional pelo mesmo motivo.

### Entregas
- [ ] **Repositório no GitHub:** código-fonte, dependências, configurações de exemplo sem credenciais e materiais para execução
- [ ] **README:** integrantes, descrição da funcionalidade, instruções de execução, modelos e tecnologias, justificativas técnicas e diagrama da arquitetura
- [ ] **Evidências de avaliação:** dados ou acesso às fontes, casos de teste, resultados, métricas/critérios e análise das limitações

**Formato:** enviar **apenas um `.txt`** com RM e nome completo dos integrantes e o link do repositório.

### Apresentação presencial (30% da nota · 100 pts) — alinhe a data com o professor
| # | Critério | Pts |
|---|---|---|
| 1 | Funcionamento: demonstração da IA integrada à solução | 25 |
| 2 | Domínio técnico: arquitetura, fluxo e limitações | 25 |
| 3 | Adaptação: explicar como adaptar a uma mudança de requisito proposta pelo professor | 25 |
| 4 | Participação dos integrantes | 25 |

---

## 5. Java Advanced

### Detalhes
Backend da CitizenTech em Java com Spring Boot. É a mesma API consumida pelo app de Mobile e precisa ter uma funcionalidade com **IA generativa** que agregue valor real ao cidadão.

**Tecnologias obrigatórias:** Spring Boot, Spring Data JPA, Spring Security com JWT, Spring AI.

### Requisitos
- [ ] **API REST do domínio:** mínimo 3 entidades relacionadas, CRUD com Spring Data JPA e paginação
- [ ] **Validação e erros:** Bean Validation nos DTOs e tratamento global com `@RestControllerAdvice`, respostas padronizadas
- [ ] **Segurança:** Spring Security + JWT, pelo menos 2 papéis (ex.: cidadão e gestor), endpoints protegidos por papel
- [ ] **Spring AI:** ao menos uma funcionalidade com LLM integrada ao fluxo (não pode ser chat genérico solto)
- [ ] **Arquitetura distribuída (escolha):**
  - A) RabbitMQ/Kafka, com evento publicado e consumido de forma assíncrona; e/ou
  - B) microsserviços: mínimo 2 serviços, Eureka e comunicação via OpenFeign, RestClient e/ou WebClient
- [ ] **Documentação e integração:** Swagger/OpenAPI, README de execução, API consumida pelo app Mobile

### Pontuação (100 pts)
| # | Critério | Pts |
|---|---|---|
| 1 | API REST do domínio | 20 |
| 2 | Validação e tratamento de erros | 10 |
| 3 | Segurança com JWT e papéis | 20 |
| 4 | Funcionalidade com Spring AI | 20 |
| 5 | Arquitetura distribuída (A ou B) | 20 |
| 6 | Documentação e integração com Mobile | 10 |

### O que tira pontos
| Item | Desconto |
|---|---|
| Entrega fora do prazo ou fora do Portal do Aluno | −100 |
| Arquivo corrompido, incompleto ou sem o `.txt` de identificação | −10 |
| Solução sem relação com o tema CitizenTech | −40 |
| Projeto não compila ou não sobe seguindo o README | −30 |
| Problema grave de segurança ou arquitetura | −10 por ocorrência |

### Entregas
- [ ] Repositório **público** no GitHub com README contendo: como rodar, usuários de teste, endpoints principais, opção escolhida no requisito 5, link do deploy (se houver) e declaração de uso de IA
- [ ] Link do vídeo pitch (até 5 min)

**Formato:** `.zip` com o `.txt` de identificação, o link do repositório e o link do vídeo.

### Apresentação presencial (30% da nota · 100 pts)
| # | Critério | Pts |
|---|---|---|
| 1 | Demonstração ponta a ponta com o app Mobile | 20 |
| 2 | Explicar modelagem e relacionamentos | 15 |
| 3 | Explicar o fluxo de autenticação JWT | 20 |
| 4 | Explicar como a feature de Spring AI funciona | 15 |
| 5 | Explicar a parte distribuída escolhida | 15 |
| 6 | Explicar como a solução colabora com o cidadão | 15 |

### Vídeo pitch
- **Duração:** até 5 minutos (o que passar não é avaliado)
- **Link público:** YouTube (público ou não listado) ou Drive com acesso aberto — teste numa aba anônima
- **Onde entregar:** na entrega de Java Advanced, no Portal do Aluno
- **Vale para a premiação:** usado para eleger o melhor projeto da GS

**Roteiro sugerido:**
| Tempo | Bloco | Conteúdo |
|---|---|---|
| 0:00 | O problema | Quem é o cidadão afetado e por que importa; traga um dado ou evidência real |
| 0:45 | A solução | O que a CitizenTech faz e qual melhoria concreta entrega |
| 1:30 | Demonstração | Protótipo rodando, não só telas estáticas |
| 3:00 | Modelo de negócio | Quem paga, quem participa do ecossistema, como se sustenta e escala |
| 4:00 | Impacto | Como será medido, quais ODS atende e fechamento |

Dicas: áudio limpo vale mais que edição elaborada; fale com quem vai usar a solução, não com o professor; termine dizendo por que a proposta merece vencer.

---

## 6. Mastering Relational and Non-Relational Database

### Detalhes
**Desafio: Do Oracle ao MongoDB — dados a serviço do cidadão.**
Implementar regras de negócio no Oracle com PL/SQL (funções, procedimentos, gatilhos e pacotes) e representar parte dos mesmos dados no MongoDB. Criar scripts reproduzíveis (database, collections, inserção), consultas e relatórios com resultados equivalentes ao modelo relacional. Explicar escolhas de modelagem (referências × documentos incorporados) e evidenciar a correspondência entre registros Oracle e documentos MongoDB. **Não é exigida integração automática** entre os bancos — a transição é demonstrada por scripts e comparação de resultados.

### Requisitos
- [ ] **Oracle:** mínimo 2 tabelas relacionadas, dados de teste e 2 blocos PL/SQL com decisão, repetição ou cursor
- [ ] 2 funções e 2 procedimentos PL/SQL aplicados a regras reais da solução
- [ ] 2 gatilhos, incluindo ao menos 1 trilha de auditoria (data, operação, valores antigo/novo)
- [ ] Funções e procedimentos em no mínimo 2 pacotes (specification e body), com testes
- [ ] **MongoDB:** script para selecionar/criar database, criar 2 collections e inserir no mínimo 10 documentos no total
- [ ] **Transição e relatórios:** mapa relacional→documental, 3 consultas MongoDB (1 agregação) e comparação de resultados

### Pontuação (70 pts)
| # | Critério | Pts |
|---|---|---|
| 1 | Modelo Oracle + blocos PL/SQL | 10 |
| 2 | 2 funções + 2 procedimentos | 15 |
| 3 | 2 gatilhos e auditoria | 10 |
| 4 | 2 pacotes e testes | 10 |
| 5 | MongoDB: collections + carga de dados | 10 |
| 6 | Mapeamento + 3 relatórios MongoDB | 15 |

### O que tira pontos
| Item | Desconto |
|---|---|
| Entrega fora do prazo ou fora do Portal do Aluno | −50 |
| Arquivo corrompido, incompleto ou sem o `.txt` de identificação | até −20 |
| Solução sem relação com o tema CitizenTech | **Nota zero** |
| Objetos Oracle ou scripts MongoDB não executam | até −50 |
| Relatórios sem resultados ou transição não explicada | até −30 |

### Entregas
- [ ] **Scripts Oracle (`.sql`):** DDL, INSERTs, blocos, funções, procedimentos, gatilhos, pacotes e testes
- [ ] **Scripts MongoDB (`.js`):** `use`, `createCollection`, `insertMany`, `find` e `aggregate`; dados de exemplo
- [ ] **README/PDF:** problema CitizenTech, mapeamento Oracle→MongoDB, 3 relatórios, evidências e execução

**Formato:** exemplo do slide — `.zip` com o código-fonte e o `.txt` de identificação.

### Apresentação presencial (30 pts)
| # | Critério | Pts |
|---|---|---|
| 1 | Explicar o problema CitizenTech e a modelagem Oracle | 5 |
| 2 | Executar função e procedimento PL/SQL | 5 |
| 3 | Demonstrar gatilho, auditoria e pacotes | 5 |
| 4 | Criar collections e inserir documentos MongoDB | 5 |
| 5 | Executar 3 relatórios e comparar com Oracle | 5 |
| 6 | Justificar a transição e responder perguntas | 5 |

---

## 7. Mobile Application Development

### Detalhes
Desenvolver um aplicativo em **React Native** a partir da solução da GS.

### Requisitos
- [ ] **R1 — Telas e navegação:** mínimo 6 telas distintas (React Navigation ou Expo Router), navegação fluida, boas práticas de usabilidade e acessibilidade
- [ ] **R2 — CRUD com API Java/.NET** (Axios ou Fetch): Create, Read, Update, Delete via API RESTful das disciplinas de backend; dados manipulados via API, **nunca apenas no dispositivo**; feedback visual de erro (mensagens, loaders, alertas)
- [ ] **R3 — Autenticação:** tela de cadastro (signup), login com autenticação real, logout que limpa a sessão e bloqueia rotas protegidas; validação de formulários, tratamento de erros, indicadores de carregamento; Firebase Authentication ou API Java/.NET; rotas restritas só após login
- [ ] **R4 — Estilização:** cores, fontes e imagens personalizadas de acordo com o tema; design consistente seguindo guidelines Apple/Google; arquivos de estilo, fontes e imagens nas pastas adequadas
- [ ] **R5 — Arquitetura do código:** organização lógica de arquivos/pastas/componentes; nomes claros e padronizados; separação de responsabilidades (componentes, serviços, estilos, rotas); código limpo e bem formatado; uso de ESLint/Prettier; bibliotecas relevantes, atualizadas e necessárias
- [ ] **R6 — Vídeo:** app funcionando (emulador ou dispositivo) mostrando autenticação, navegação, CRUD, usabilidade; narração opcional; **máximo 5 min**; YouTube público ou não listado; link no `.txt` e no README.md
- [ ] **R7 — Publicação:** app no **Firebase App Distribution** com o e-mail do professor como tester; tela **"Sobre o App"** exibindo o hash do commit de referência (não conta para o R1); versão publicada idêntica ao código enviado

> ⚠️ O backend precisa estar **ativo durante o período de avaliação** para os testes do app.

### Pontuação (100 pts)
| # | Critério | Pts |
|---|---|---|
| 1 | R1 — Telas e navegação | 10 |
| 2 | R2 — CRUD com API | 30 |
| 3 | R3 — Autenticação | 20 |
| 4 | R4 — Estilização | 5 |
| 5 | R5 — Arquitetura do código | 20 |
| 6 | R6 — Vídeo | 10 |
| 7 | R7 — Publicação | 5 |

### O que tira pontos
| Item | Desconto |
|---|---|
| Entrega fora do prazo ou fora do Portal do Aluno | −30 |
| Arquivo corrompido, incompleto ou sem o `.txt` de identificação | −30 |
| Solução sem relação com o tema CitizenTech | −30 |
| Não entregar o GitHub ou não incluir o e-mail do professor como colaborador/leitor | −20 |
| Sem README.md | −10 |
| Aplicativo fora do escopo das aulas | −60 |
| Histórico do Git incoerente/confuso (espera-se árvore de commits sequencial, evolutiva, com mensagens claras) | −50 |
| Não publicar o app ou publicado diferente do código | −20 |
| Menos de 6 telas | −30 |
| Sem estilização ou visual incompatível com um aplicativo | −20 |
| Não entregar o link do vídeo no YouTube | −20 |

### Entregas
Arquivo `.txt` contendo:
- [ ] Nome do grupo
- [ ] Nomes dos integrantes
- [ ] Link do vídeo
- [ ] Endereço do repositório no GitHub (com o e-mail do professor como leitor e/ou colaborador)

### Apresentação presencial (30% da nota · 100 pts)
| # | Critério | Pts |
|---|---|---|
| 1 | Domínio técnico: explicar como o app funciona, relacionando conceitos, código e fluxos | 30 |
| 2 | Fundamentação das decisões técnicas e conhecimento das alternativas | 30 |
| 3 | Adaptação a uma mudança de requisito proposta pelo professor | 20 |
| 4 | Domínio compartilhado: todos participam da defesa técnica | 20 |

---

## Dependências entre disciplinas

| Disciplina | Depende de / alimenta |
|---|---|
| .NET ou Java | API consumida pelo **Mobile** |
| DevOps | Faz deploy em nuvem da solução **Java ou .NET** |
| Java | Precisa de feature com IA generativa (Spring AI); apresentação mostra integração com o **Mobile** |
| IoT/IA | Funcionalidade de IA integrada à solução |
| Mobile | CRUD e (opcionalmente) autenticação via API **Java ou .NET**, que deve estar no ar na avaliação |

## Como entregar no Portal do Aluno
1. Acesse www2.fiap.com.br com RM e senha.
2. Em **Aulas**, clique em **"Entrega de Trabalhos"**.
3. Escolha um trabalho da Global Solution (cada disciplina é uma entrega separada).
4. Anexe o arquivo, conferindo grupo, data de vencimento e descrição. Repita para todas as disciplinas.
5. Confira o arquivo anexado.
