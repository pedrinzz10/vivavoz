-- VozViva - DDL e carga de dados do banco relacional (Azure SQL / SQL Server)
-- Gerado por ./gradlew gerarScriptBd a partir de src/main/resources/db/migration. Não edite à mão.

-- V1__usuarios.sql
-- SPEC-023: usuários do ecossistema (cidadãos e cuidadores). A API Java é a dona da identidade.
CREATE TABLE usuarios (
    id            BIGINT IDENTITY(1,1) NOT NULL,
    nome          NVARCHAR(120)        NOT NULL,
    email         NVARCHAR(160)        NOT NULL,
    senha_hash    NVARCHAR(100)        NOT NULL,
    papel         NVARCHAR(20)         NOT NULL,
    ativo         BIT                  NOT NULL DEFAULT 1,
    criado_em     DATETIME2            NOT NULL,
    atualizado_em DATETIME2            NOT NULL,
    CONSTRAINT pk_usuarios PRIMARY KEY (id),
    CONSTRAINT uk_usuarios_email UNIQUE (email),
    CONSTRAINT ck_usuarios_papel CHECK (papel IN ('CIDADAO', 'CUIDADOR'))
);

-- V2__perfis_e_contatos_emergencia.sql
-- SPEC-017: perfil de emergência do cidadão, gerenciado pelo cuidador, e seus contatos.
CREATE TABLE perfis_emergencia (
    id              BIGINT IDENTITY(1,1) NOT NULL,
    cidadao_id      BIGINT               NOT NULL,
    cuidador_id     BIGINT               NOT NULL,
    mobilidade      NVARCHAR(30)         NOT NULL,
    condicoes_saude NVARCHAR(500)        NULL,
    medicamentos    NVARCHAR(500)        NULL,
    observacoes     NVARCHAR(500)        NULL,
    criado_em       DATETIME2            NOT NULL,
    atualizado_em   DATETIME2            NOT NULL,
    CONSTRAINT pk_perfis_emergencia PRIMARY KEY (id),
    CONSTRAINT uk_perfis_emergencia_cidadao UNIQUE (cidadao_id),
    CONSTRAINT fk_perfis_emergencia_cidadao FOREIGN KEY (cidadao_id) REFERENCES usuarios (id),
    CONSTRAINT fk_perfis_emergencia_cuidador FOREIGN KEY (cuidador_id) REFERENCES usuarios (id),
    CONSTRAINT ck_perfis_emergencia_mobilidade CHECK (mobilidade IN
        ('CADEIRANTE', 'ANDADOR', 'BENGALA', 'DEFICIENCIA_VISUAL', 'DEFICIENCIA_AUDITIVA', 'NENHUMA'))
);

CREATE INDEX ix_perfis_emergencia_cuidador ON perfis_emergencia (cuidador_id);

CREATE TABLE contatos_emergencia (
    id            BIGINT IDENTITY(1,1) NOT NULL,
    perfil_id     BIGINT               NOT NULL,
    nome          NVARCHAR(120)        NOT NULL,
    telefone      NVARCHAR(20)         NOT NULL,
    parentesco    NVARCHAR(60)         NULL,
    prioridade    INT                  NOT NULL,
    criado_em     DATETIME2            NOT NULL,
    atualizado_em DATETIME2            NOT NULL,
    CONSTRAINT pk_contatos_emergencia PRIMARY KEY (id),
    CONSTRAINT fk_contatos_emergencia_perfil FOREIGN KEY (perfil_id) REFERENCES perfis_emergencia (id) ON DELETE CASCADE,
    CONSTRAINT ck_contatos_emergencia_prioridade CHECK (prioridade BETWEEN 1 AND 10)
);

CREATE INDEX ix_contatos_emergencia_perfil ON contatos_emergencia (perfil_id);

-- V3__emergencias.sql
-- SPEC-008: acionamentos do botão de emergência e os avisos enviados aos contatos.
CREATE TABLE emergencias (
    id                   BIGINT IDENTITY(1,1) NOT NULL,
    perfil_id            BIGINT               NOT NULL,
    status               NVARCHAR(20)         NOT NULL,
    latitude             DECIMAL(9,6)         NULL,
    longitude            DECIMAL(9,6)         NULL,
    texto_original       NVARCHAR(300)        NULL,
    contatos_notificados INT                  NOT NULL DEFAULT 0,
    reenvios             INT                  NOT NULL DEFAULT 0,
    acionada_em          DATETIME2            NOT NULL,
    notificada_em        DATETIME2            NULL,
    encerrada_em         DATETIME2            NULL,
    CONSTRAINT pk_emergencias PRIMARY KEY (id),
    CONSTRAINT fk_emergencias_perfil FOREIGN KEY (perfil_id) REFERENCES perfis_emergencia (id) ON DELETE CASCADE,
    CONSTRAINT ck_emergencias_status CHECK (status IN ('ACIONADA', 'NOTIFICADA', 'FALHA_NO_AVISO', 'ATENDIDA', 'CANCELADA')),
    CONSTRAINT ck_emergencias_coordenadas CHECK (
        (latitude IS NULL AND longitude IS NULL)
        OR (latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180))
);

CREATE INDEX ix_emergencias_perfil_acionada ON emergencias (perfil_id, acionada_em);

CREATE TABLE avisos_emergencia (
    id             BIGINT IDENTITY(1,1) NOT NULL,
    emergencia_id  BIGINT               NOT NULL,
    contato_nome   NVARCHAR(120)        NOT NULL,
    telefone       NVARCHAR(20)         NOT NULL,
    mensagem       NVARCHAR(500)        NOT NULL,
    enviado_em     DATETIME2            NOT NULL,
    CONSTRAINT pk_avisos_emergencia PRIMARY KEY (id),
    CONSTRAINT fk_avisos_emergencia_emergencia FOREIGN KEY (emergencia_id) REFERENCES emergencias (id) ON DELETE CASCADE
);

CREATE INDEX ix_avisos_emergencia_emergencia ON avisos_emergencia (emergencia_id);

-- V4__verificacoes_golpe.sql
-- SPEC-015: estatísticas anonimizadas das verificações de golpe. O texto da mensagem e o usuário
-- nunca são gravados (LGPD): só o tipo de golpe, a classificação, o canal e a data.
CREATE TABLE verificacoes_golpe (
    id             BIGINT IDENTITY(1,1) NOT NULL,
    tipo_golpe     NVARCHAR(30)         NOT NULL,
    classificacao  NVARCHAR(30)         NOT NULL,
    canal          NVARCHAR(20)         NOT NULL,
    analisado_por  NVARCHAR(20)         NOT NULL,
    criada_em      DATETIME2            NOT NULL,
    CONSTRAINT pk_verificacoes_golpe PRIMARY KEY (id),
    CONSTRAINT ck_verificacoes_golpe_classificacao CHECK (classificacao IN
        ('SEM_SINAIS_DE_RISCO', 'NAO_E_POSSIVEL_AVALIAR', 'SUSPEITA', 'PROVAVEL_GOLPE')),
    CONSTRAINT ck_verificacoes_golpe_analisado_por CHECK (analisado_por IN ('IA_E_REGRAS', 'REGRAS'))
);

CREATE INDEX ix_verificacoes_golpe_criada_em ON verificacoes_golpe (criada_em);

-- V5__pontos_atendimento.sql
-- SPEC-007: locais de atendimento (dados abertos do OpenStreetMap, licença ODbL). Somente leitura pela API;
-- a carga fica em R__carga_pontos_atendimento.sql, gerada por ./gradlew importarOsm.
CREATE TABLE pontos_atendimento (
    id                 BIGINT IDENTITY(1,1) NOT NULL,
    osm_id             NVARCHAR(30)         NOT NULL,
    nome               NVARCHAR(200)        NOT NULL,
    tipo               NVARCHAR(20)         NOT NULL,
    endereco           NVARCHAR(300)        NULL,
    telefone           NVARCHAR(40)         NULL,
    latitude           DECIMAL(9,6)         NOT NULL,
    longitude          DECIMAL(9,6)         NOT NULL,
    acessibilidade     NVARCHAR(20)         NOT NULL,
    banheiro_adaptado  BIT                  NULL,
    funciona_24h       BIT                  NULL,
    fonte              NVARCHAR(60)         NOT NULL,
    atualizado_em      DATETIME2            NOT NULL,
    CONSTRAINT pk_pontos_atendimento PRIMARY KEY (id),
    CONSTRAINT uq_pontos_atendimento_osm_id UNIQUE (osm_id),
    CONSTRAINT ck_pontos_atendimento_tipo CHECK (tipo IN
        ('HOSPITAL', 'UPA', 'UBS', 'FARMACIA', 'CRAS', 'DELEGACIA', 'DEFENSORIA')),
    CONSTRAINT ck_pontos_atendimento_acessibilidade CHECK (acessibilidade IN ('SIM', 'PARCIAL', 'NAO', 'DESCONHECIDA')),
    CONSTRAINT ck_pontos_atendimento_latitude CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_pontos_atendimento_longitude CHECK (longitude BETWEEN -180 AND 180)
);

CREATE INDEX ix_pontos_atendimento_tipo_posicao ON pontos_atendimento (tipo, latitude, longitude);

-- V6__interacoes_voz.sql
-- SPEC-003: histórico das falas classificadas, visível só para o próprio usuário. A localização nunca é gravada.
CREATE TABLE interacoes_voz (
    id             BIGINT IDENTITY(1,1) NOT NULL,
    usuario_id     BIGINT               NOT NULL,
    texto          NVARCHAR(500)        NOT NULL,
    intencao       NVARCHAR(30)         NOT NULL,
    acao           NVARCHAR(30)         NOT NULL,
    urgencia       NVARCHAR(10)         NOT NULL,
    confianca      DECIMAL(4,3)         NULL,
    latencia_ms    INT                  NOT NULL,
    analisado_por  NVARCHAR(20)         NOT NULL,
    criada_em      DATETIME2            NOT NULL,
    CONSTRAINT pk_interacoes_voz PRIMARY KEY (id),
    CONSTRAINT fk_interacoes_voz_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id) ON DELETE CASCADE,
    CONSTRAINT ck_interacoes_voz_intencao CHECK (intencao IN
        ('SOCORRO', 'BUSCAR_ATENDIMENTO', 'VERIFICAR_INFORMACAO', 'ZELADORIA_REPORT', 'DESCONHECIDA')),
    CONSTRAINT ck_interacoes_voz_acao CHECK (acao IN
        ('ACIONAR_SOCORRO', 'MOSTRAR_ATENDIMENTOS', 'MOSTRAR_VERIFICACAO', 'ABRIR_ZELADORIA_DOTNET', 'PEDIR_REPETICAO')),
    CONSTRAINT ck_interacoes_voz_urgencia CHECK (urgencia IN ('BAIXA', 'MEDIA', 'ALTA', 'CRITICA')),
    CONSTRAINT ck_interacoes_voz_analisado_por CHECK (analisado_por IN ('IA_E_REGRAS', 'REGRAS'))
);

CREATE INDEX ix_interacoes_voz_usuario_criada ON interacoes_voz (usuario_id, criada_em);

-- V7__lgpd_e_perfil_do_cidadao.sql
-- LGPD e autonomia do cidadão:
-- 1) o cidadão pode criar e manter o próprio perfil de emergência, sem cuidador (cuidador_id opcional);
-- 2) a mobilidade é dado de saúde: pode ficar NAO_INFORMADO;
-- 3) o consentimento do cadastro fica registrado, com a data do aceite.
-- O SQL Server não altera a nulabilidade de uma coluna usada em índice ou chave estrangeira: os dois são
-- recriados em volta da alteração.
ALTER TABLE perfis_emergencia DROP CONSTRAINT fk_perfis_emergencia_cuidador;
DROP INDEX ix_perfis_emergencia_cuidador ON perfis_emergencia;
ALTER TABLE perfis_emergencia ALTER COLUMN cuidador_id BIGINT NULL;
CREATE INDEX ix_perfis_emergencia_cuidador ON perfis_emergencia (cuidador_id);
ALTER TABLE perfis_emergencia ADD CONSTRAINT fk_perfis_emergencia_cuidador
    FOREIGN KEY (cuidador_id) REFERENCES usuarios (id);

ALTER TABLE perfis_emergencia DROP CONSTRAINT ck_perfis_emergencia_mobilidade;
ALTER TABLE perfis_emergencia ADD CONSTRAINT ck_perfis_emergencia_mobilidade CHECK (mobilidade IN
    ('CADEIRANTE', 'ANDADOR', 'BENGALA', 'DEFICIENCIA_VISUAL', 'DEFICIENCIA_AUDITIVA', 'NENHUMA', 'NAO_INFORMADO'));

ALTER TABLE usuarios ADD consentimento BIT NOT NULL DEFAULT 0;
ALTER TABLE usuarios ADD consentimento_em DATETIME2 NULL;

-- V8__contas_excluidas.sql
-- Contas excluídas (LGPD): só o id numérico, sem nenhum dado pessoal. O validador do JWT recusa tokens ainda
-- válidos de uma conta que já foi excluída. Ids de IDENTITY nunca são reaproveitados.
CREATE TABLE contas_excluidas (
    usuario_id   BIGINT     NOT NULL,
    excluida_em  DATETIME2  NOT NULL,
    CONSTRAINT pk_contas_excluidas PRIMARY KEY (usuario_id)
);

-- V9__papel_gestor.sql
-- Papel GESTOR: servidor da prefeitura que atende a zeladoria na API .NET. O órgão vai no token (claim "orgao").
ALTER TABLE usuarios DROP CONSTRAINT ck_usuarios_papel;
ALTER TABLE usuarios ADD CONSTRAINT ck_usuarios_papel CHECK (papel IN ('CIDADAO', 'CUIDADOR', 'GESTOR'));
ALTER TABLE usuarios ADD orgao NVARCHAR(20) NULL
    CONSTRAINT ck_usuarios_orgao CHECK (orgao IN ('OBRAS', 'ILUMINACAO', 'MOBILIDADE', 'LIMPEZA'));

-- V10__orgao_so_do_gestor.sql
-- Separado da V9: no SQL Server, uma restrição que cita a coluna criada no mesmo lote falha na compilação do lote.
-- Todo gestor tem órgão, e só o gestor tem.
ALTER TABLE usuarios ADD CONSTRAINT ck_usuarios_orgao_do_gestor
    CHECK ((papel = 'GESTOR' AND orgao IS NOT NULL) OR (papel <> 'GESTOR' AND orgao IS NULL));

-- R__carga_pontos_atendimento.sql
-- SPEC-007: carga dos locais de atendimento de Osasco/SP e arredores.
-- Gerado por ./gradlew importarOsm a partir da Overpass API. Não edite à mão.
-- Dados © colaboradores do OpenStreetMap, licença ODbL (https://www.openstreetmap.org/copyright).
-- Base OSM de 2026-07-28 02:16:18 UTC; 237 locais.
-- Migração repetível: o Flyway reaplica quando o conteúdo muda. A atualização é por osm_id,
-- então os locais que continuam no mapa mantêm o mesmo id.
DELETE FROM pontos_atendimento WHERE osm_id NOT IN (N'node/10651950008', N'node/10655322394', N'node/10669764357', N'node/10671337203', N'node/10671342013', N'node/10671604263', N'node/10673407654', N'node/10673445699', N'node/10678398694', N'node/10678617997', N'node/10679471545', N'node/10688350843', N'node/10691164912', N'node/10902081307', N'node/11150473860', N'node/11362005853', N'node/11365361845', N'node/11931477936', N'node/11988025852', N'node/12316699492', N'node/12316724384', N'node/12324135409', N'node/12368624357', N'node/12383551912', N'node/12408018893', N'node/12431229432', N'node/12559374437', N'node/12650683942', N'node/12766210542', N'node/12780066146', N'node/12837516385', N'node/12849020003', N'node/12857792033', N'node/12865756507', N'node/12873211687', N'node/12873261809', N'node/12876074119', N'node/12876172582', N'node/13016014607', N'node/13287941281', N'node/13307478748', N'node/13307478769', N'node/13307478773', N'node/13643835743', N'node/13651338572', N'node/13651362228', N'node/13651362294', N'node/13651375066', N'node/13651375559', N'node/13651376069', N'node/13651394473', N'node/13651394560', N'node/13651399792', N'node/13653475045', N'node/13653592546', N'node/14049009297', N'node/1769088979', N'node/1783880592', N'node/2632416391', N'node/3627220953', N'node/3876032484', N'node/3915280371', N'node/3995520700', N'node/4012586263', N'node/408932164', N'node/4253305633', N'node/4278463334', N'node/4329305190', N'node/4351349541', N'node/435264985', N'node/435905680', N'node/435905692', N'node/4532233681', N'node/4717558264', N'node/5069704937', N'node/507071859', N'node/5116220903', N'node/5136378222', N'node/5192389674', N'node/5270238405', N'node/5320428934', N'node/5716021021', N'node/5816707298', N'node/6321016454', N'node/7140034986', N'node/7377356850', N'node/8198818915', N'node/8449712205', N'node/8873764994', N'node/9159176980', N'node/9163425052', N'node/9165127612', N'node/9165682038', N'node/9711617190', N'relation/12317146', N'relation/13517704', N'relation/15591251', N'relation/19567684', N'relation/19960669', N'way/1033460496', N'way/1047679594', N'way/1047679603', N'way/1050331344', N'way/1051379780', N'way/1053701451', N'way/1057634834', N'way/1072937952', N'way/1074884375', N'way/1133901601', N'way/1145194770', N'way/1146509947', N'way/1147047474', N'way/1149197451', N'way/1163712733', N'way/1174573772', N'way/1183565099', N'way/1199224064', N'way/1217672075', N'way/1221447693', N'way/1222940441', N'way/1226469553', N'way/1238052390', N'way/1243752179', N'way/1286379175', N'way/1286379866', N'way/1289760471', N'way/1299028159', N'way/1301611351', N'way/1302033347', N'way/1328618776', N'way/1348051378', N'way/1348051388', N'way/1349207454', N'way/1356971101', N'way/1356971104', N'way/1357801212', N'way/1357801974', N'way/1360964940', N'way/1366009510', N'way/1380027410', N'way/1380027419', N'way/1380296075', N'way/1381173362', N'way/1389044514', N'way/1391053419', N'way/1391409399', N'way/1391567885', N'way/1392127851', N'way/1395613617', N'way/1415906664', N'way/1423327387', N'way/1426718534', N'way/1445093629', N'way/1448258978', N'way/1448309779', N'way/1450150487', N'way/1450716723', N'way/1450959081', N'way/1451138444', N'way/1453212957', N'way/1468996382', N'way/146947250', N'way/1478577022', N'way/1479054144', N'way/150247926', N'way/155852282', N'way/158788584', N'way/158967600', N'way/165265046', N'way/200863585', N'way/291547014', N'way/313850657', N'way/320141806', N'way/34875563', N'way/381323687', N'way/381338399', N'way/385056284', N'way/395910877', N'way/405574870', N'way/410892257', N'way/424941734', N'way/424946527', N'way/426008304', N'way/428753286', N'way/428753287', N'way/432099700', N'way/439088478', N'way/462846028', N'way/468420898', N'way/479640405', N'way/481652739', N'way/485425303', N'way/520075842', N'way/520076544', N'way/520921378', N'way/520922063', N'way/520923746', N'way/546125776', N'way/548857758', N'way/595650045', N'way/599507017', N'way/599507589', N'way/599509089', N'way/612100207', N'way/637694440', N'way/641688037', N'way/644486335', N'way/647807625', N'way/654314444', N'way/664738673', N'way/672938979', N'way/675406741', N'way/678966520', N'way/678969571', N'way/681136870', N'way/681292675', N'way/684800165', N'way/698305920', N'way/721628995', N'way/727276295', N'way/75490092', N'way/766490242', N'way/780039865', N'way/785459956', N'way/79392965', N'way/79442277', N'way/79877355', N'way/816792425', N'way/835605847', N'way/846560981', N'way/865953212', N'way/921028275', N'way/937790535', N'way/991316193', N'way/992025208', N'way/992238817', N'way/996638730');

MERGE INTO pontos_atendimento AS destino
USING (VALUES
(N'node/10651950008', N'Droga Paes', N'FARMACIA', N'Rua General Nelson Braga Moreira', N'+55 11 3655 5983', -23.548248, -46.792097, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/10655322394', N'Drogaria São Paulo', N'FARMACIA', N'Rua Professor José de Azevedo Minhoto - Km 18', N'+55 11 4554 9916', -23.529753, -46.794356, N'DESCONHECIDA', NULL, 0, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/10669764357', N'´Bifarma', N'FARMACIA', N'Avenida João de Andrade', NULL, -23.560491, -46.798895, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/10671337203', N'Drogabel', N'FARMACIA', N'Avenida Sarah Veloso', N'+55 11 3609 2399', -23.564230, -46.798927, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/10671342013', N'Drogasil', N'FARMACIA', N'Rua Dolores Lipiano Moioli - Jardim Veloso', N'+55 11 4624 0348', -23.564314, -46.798572, N'SIM', NULL, 0, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/10671604263', N'Drogaria Ponto Final', N'FARMACIA', N'Avenida Benedito Alves Turíbio, 1424', NULL, -23.551492, -46.814108, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/10673407654', N'Hamilton Pharmácia', N'FARMACIA', N'Avenida Benedito Alves Turíbio, 411 - Jardim Veloso', N'+55 11 97793 4319', -23.558276, -46.808447, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/10673445699', N'Unifar Drogaria e Perfumaria', N'FARMACIA', N'Avenida Benedito Alves Turíbio - Jardim Veloso', N'+55 11 3692 6944', -23.559368, -46.804851, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/10678398694', N'DrogaMed', N'FARMACIA', N'Avenida Analice Sakatauskas, 76', N'+55 11 3685 1836', -23.547524, -46.775859, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/10678617997', N'Drogaria Rani', N'FARMACIA', N'Avenida Antônio Carlos Costa', NULL, -23.554590, -46.786179, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/10679471545', N'Drogaria Omega', N'FARMACIA', N'Rua João Carlos Munhoz Vaqueiro, 1480', N'+55 11 3691 5266', -23.572041, -46.801447, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/10688350843', N'Farmácia Freitas', N'FARMACIA', N'Avenida José Barbosa de Siqueira, 668', N'+55 11 3605 6496', -23.551036, -46.806376, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/10691164912', N'DrogaBig', N'FARMACIA', N'Rua Elza Fagundes de Moraes', N'+55 11 3607 3613', -23.544922, -46.807748, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/10902081307', N'Drogaria Central do Butantã', N'FARMACIA', NULL, NULL, -23.571226, -46.708193, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/11150473860', N'Bifarma', N'FARMACIA', N'Estrada Jacaranda', NULL, -23.550246, -46.844400, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/11362005853', N'Droga Raia', N'FARMACIA', NULL, NULL, -23.589841, -46.716067, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/11365361845', N'Drogaria São Paulo', N'FARMACIA', NULL, NULL, -23.594102, -46.721149, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/11931477936', N'Farma Conde', N'FARMACIA', N'Avenida do Rio Pequeno, 1234 - Rio Pequeno', N'+55 11 3719 4222', -23.568596, -46.756403, N'DESCONHECIDA', NULL, 0, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/11988025852', N'Platinum Drogaria', N'FARMACIA', N'Avenida General Teixeira Lott, 762 - Centro', N'+55 11 4184 2101', -23.527380, -46.841324, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/12316699492', N'Hospital Saint Patrick Jaguara', N'HOSPITAL', N'Avenida Cândido Portinari, 616 - Vila Jaguara, São Paulo', N'5069-0002', -23.508279, -46.752376, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/12316724384', N'Hospital Saint Patrick Osasco', N'HOSPITAL', N'Rua Eloy Candido Lopes, 294 - Centro, Osasco', NULL, -23.533833, -46.776582, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/12324135409', N'Drogarias Menor Preço', N'FARMACIA', N'Estrada Turística do Jaraguá', NULL, -23.486547, -46.756874, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/12368624357', N'Campeão Farma', N'FARMACIA', N'Avenida Antônio Carlos Costa - Jardim Bela Vista', N'+55 11 4624 7273', -23.550511, -46.785254, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/12383551912', N'Drogaria Poupaqui', N'FARMACIA', N'Avenida Prestes Maia, 855', N'+55 11 3694 5810', -23.570364, -46.778451, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/12408018893', N'Ultrafarma', N'FARMACIA', NULL, NULL, -23.573961, -46.727467, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/12431229432', N'Drogaria São Paulo', N'FARMACIA', NULL, NULL, -23.583699, -46.716771, N'DESCONHECIDA', NULL, 1, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/12559374437', N'Bifarma', N'FARMACIA', NULL, NULL, -23.537706, -46.776649, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/12650683942', N'Drogaria São Paulo', N'FARMACIA', NULL, NULL, -23.594634, -46.707600, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/12766210542', N'Drogaria São Paulo', N'FARMACIA', NULL, NULL, -23.558810, -46.766597, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/12780066146', N'ManiFarma Farmácia de Manipulação', N'FARMACIA', NULL, NULL, -23.581926, -46.712640, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/12837516385', N'Drogaria São Paulo', N'FARMACIA', N'Rua Ushima Kira', NULL, -23.592609, -46.837003, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/12849020003', N'Drogaria Laras', N'FARMACIA', N'Rua Três Arapongas, 21 - Jaguaré, São Paulo', NULL, -23.546738, -46.742140, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/12857792033', N'Droga Altino', N'FARMACIA', N'Avenida José Lopes Lázaro, 395', N'+55 11 3681 3606', -23.530011, -46.762674, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/12865756507', N'Drogaria Betel', N'FARMACIA', N'Rua Doutor Nilo Machado', NULL, -23.534653, -46.804498, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/12873211687', N'Drogaria João Batista', N'FARMACIA', N'Avenida Vereador José Fernandes Filho, 2B - Vila Helena', NULL, -23.581429, -46.824969, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/12873261809', N'Unidade Básica de Saúde Vila Helena', N'UBS', N'Avenida Vereador José Fernandes Filho, 78B - Vila Helena', N'+55 11 4146 4609', -23.581876, -46.823623, N'DESCONHECIDA', NULL, 0, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/12876074119', N'Drogaria Central Pharma', N'FARMACIA', N'Estrada do Pequiá, 2303', NULL, -23.549264, -46.841862, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/12876172582', N'Drogasil', N'FARMACIA', N'Avenida Inocêncio Seráfico, 3500', N'+55 11 4167 1055', -23.552880, -46.835514, N'SIM', NULL, 0, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/13016014607', N'DrogaLeste', N'FARMACIA', N'Avenida Antônio Faustino dos Santos, 330', NULL, -23.527152, -46.820466, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/13287941281', N'Droga Borges', N'FARMACIA', N'Rua Professor João Antônio Prado, 111', N'+55 11 3684 0188', -23.541236, -46.794684, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/13307478748', N'Drogaria Neco', N'FARMACIA', N'Avenida Presidente Costa e Silva', N'+55 11 3696 4865', -23.502755, -46.790811, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/13307478769', N'Drogaria São Paulo', N'FARMACIA', NULL, NULL, -23.502855, -46.790616, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/13307478773', N'Mello Farma Popular', N'FARMACIA', N'Avenida Presidente Costa e Silva', NULL, -23.502582, -46.790398, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/13643835743', N'33º Distrito Policial', N'DELEGACIA', NULL, NULL, -23.497238, -46.745952, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/13651338572', N'Delegacia de Polícia', N'DELEGACIA', NULL, NULL, -23.545146, -46.771062, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/13651362228', N'5º Distrito Policial', N'DELEGACIA', NULL, NULL, -23.531085, -46.774953, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/13651362294', N'4º Distrito Policial', N'DELEGACIA', NULL, NULL, -23.515625, -46.789807, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/13651375066', N'Delegacia de Defesa da Mulher', N'DELEGACIA', NULL, NULL, -23.536922, -46.775016, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/13651375559', N'9º Distrito Policial', N'DELEGACIA', NULL, NULL, -23.528163, -46.768581, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/13651376069', N'8º Distrito Policial', N'DELEGACIA', NULL, NULL, -23.547405, -46.794929, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/13651394473', N'7º Distrito Policial', N'DELEGACIA', NULL, NULL, -23.498877, -46.770196, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/13651394560', N'3º Distrito Policial', N'DELEGACIA', NULL, NULL, -23.517628, -46.772315, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/13651399792', N'10º Distrito Policial', N'DELEGACIA', NULL, NULL, -23.497588, -46.789852, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/13653475045', N'2º Distrito Policial', N'DELEGACIA', NULL, NULL, -23.563042, -46.833346, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/13653592546', N'Delegacia Seccional de Carapicuíba', N'DELEGACIA', NULL, NULL, -23.517136, -46.844879, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/14049009297', N'UBS Chácara Inglesa', N'UBS', N'Rua Valdir Padovan, 95 - Jardim Felicidade, São Paulo', N'+55 11 3834 5985', -23.493729, -46.725244, N'DESCONHECIDA', NULL, 0, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/1769088979', N'Drogaria São Paulo', N'FARMACIA', N'Avenida Ministro Laudo Ferreira de Camargo, 340;344;348 - São Paulo', N'+55 11 3507-6286', -23.586686, -46.736929, N'SIM', NULL, 0, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/1783880592', N'UBS Vila Progresso', N'UBS', NULL, NULL, -23.476765, -46.703409, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/2632416391', N'Farma Civica', N'FARMACIA', N'Praça Cívica, 102 - São Paulo', NULL, -23.527795, -46.703937, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/3627220953', N'Drogaria Nova Scala', N'FARMACIA', N'Rua Vicente Rodrigues da Silva, 455 - Osasco', NULL, -23.517997, -46.792749, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/3876032484', N'Unidade Básica de Saúde São Remo', N'UBS', N'Rua Baltazar Rabelo, 167 - São Remo', NULL, -23.563585, -46.744162, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/3915280371', N'Droga Assis', N'FARMACIA', N'Avenida Getúlio Vargas - Osasco', NULL, -23.518521, -46.788619, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/3995520700', N'Bifarma', N'FARMACIA', N'Avenida Corifeu de Azevedo Marques, 1227 - São Paulo', N'+55 11 3726 3425', -23.574099, -46.726396, N'DESCONHECIDA', NULL, 0, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/4012586263', N'UBS - Vila Dalva', N'UBS', NULL, NULL, -23.563595, -46.765072, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/408932164', N'Drogasil', N'FARMACIA', NULL, NULL, -23.528109, -46.724837, N'SIM', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/4253305633', N'Drogaria São Paulo', N'FARMACIA', N'Avenida João Batista, 29;35;37 - Centro, Osasco', N'+55 11 3682-1694', -23.528190, -46.776938, N'DESCONHECIDA', NULL, 0, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/4278463334', N'Drogaria São Paulo', N'FARMACIA', N'Rua Dona Primitiva Vianco, 940 - Centro, Osasco', N'+55 11 3682 1668', -23.536675, -46.776094, N'DESCONHECIDA', NULL, 1, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/4329305190', N'Center Fórmula', N'FARMACIA', N'Avenida Vital Brasil, 294 - São Paulo', N'+55 11 3814-6584', -23.571544, -46.707088, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/4351349541', N'Drogasil', N'FARMACIA', N'Avenida Corifeu de Azevedo Marqeus, 3067 - São Paulo', N'+55 11 3735 9700', -23.569849, -46.741317, N'SIM', NULL, 1, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/435264985', N'Droga Raia', N'FARMACIA', NULL, NULL, -23.494819, -46.849253, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/435905680', N'Farma Life', N'FARMACIA', NULL, NULL, -23.493449, -46.849262, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/435905692', N'Droga Rani', N'FARMACIA', NULL, NULL, -23.495621, -46.848996, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/4532233681', N'4º Distrito Policial Osasco Demacro', N'DELEGACIA', N'Rua Romildo Isaias Neto, 231 - Osasco', NULL, -23.513643, -46.791840, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/4717558264', N'Drogaria São Paulo', N'FARMACIA', N'Avenida dos Autonomistas, 3192 - Centro, Osasco', N'+55 11 3682-1471', -23.534424, -46.782495, N'DESCONHECIDA', NULL, 1, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/5069704937', N'Droga Raia', N'FARMACIA', N'Praça Panamericana, 57 - São Paulo', N'+55 11 3034-2084', -23.554869, -46.709113, N'SIM', NULL, 1, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/507071859', N'91o. Distrito Policial', N'DELEGACIA', NULL, NULL, -23.538729, -46.730813, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/5116220903', N'Farmacia Carrefour', N'FARMACIA', N'Rua Nossa Senhora da Lapa, 457 - São Paulo', NULL, -23.523463, -46.704462, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/5136378222', N'Unidade Básica de Saúde Moinho Velho I', N'UBS', N'Praça Domingos Coelho, 5 - Vila Pereira Cerca, São Paulo', N'+55 11 3976 7601', -23.495134, -46.712874, N'DESCONHECIDA', NULL, 0, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/5192389674', N'Drogaria Rio Branco', N'FARMACIA', N'Rua Professor João Antônio Prado', N'+55 11 3683 3464', -23.540674, -46.794741, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/5270238405', N'Drogaria São Paulo', N'FARMACIA', NULL, NULL, -23.540141, -46.718107, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/5320428934', N'Droga Forte', N'FARMACIA', N'Rua Narciso Sturlini', N'+55 11 3699 3157', -23.541829, -46.774041, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/5716021021', N'Droga Raia', N'FARMACIA', NULL, NULL, -23.587340, -46.737041, N'SIM', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/5816707298', N'Pague Menos', N'FARMACIA', N'Avenida Vital Brasil, 602 - São Paulo', NULL, -23.570619, -46.709935, N'DESCONHECIDA', NULL, 1, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/6321016454', N'FarmaClick', N'FARMACIA', N'Avenida Elísio Teixeira Leite, 2037 - São Paulo', N'+551126094077', -23.477130, -46.704062, N'DESCONHECIDA', NULL, 0, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/7140034986', N'Pharmasol', N'FARMACIA', N'Rua Pio XI, 125 - São Paulo', NULL, -23.525821, -46.706900, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/7377356850', N'Drogasil', N'FARMACIA', N'Rua Ushima Kira', NULL, -23.593295, -46.837968, N'SIM', NULL, 1, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/8198818915', N'Drogasil', N'FARMACIA', NULL, NULL, -23.589533, -46.771583, N'SIM', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/8449712205', N'Drogasil', N'FARMACIA', N'Avenida Jaguaré, 287B - São Paulo', NULL, -23.557005, -46.745371, N'SIM', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/8873764994', N'Farma Unidos Popular', N'FARMACIA', NULL, NULL, -23.589568, -46.769923, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/9159176980', N'Drogaria Brasmar', N'FARMACIA', N'Avenida Inocêncio Seráfico', N'+55 11 4187 7411', -23.536451, -46.835943, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/9163425052', N'Hyper Farmácia Popular', N'FARMACIA', N'Avenida Miriam', NULL, -23.521233, -46.836198, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/9165127612', N'Rede Bol Farma Drogaria e Perfumaria', N'FARMACIA', N'Estrada do Jacarandá, 3755 - Jardim Tonato', N'+55 11 4169 8279', -23.562598, -46.847382, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/9165682038', N'Farmácia Super Popular', N'FARMACIA', N'Avenida Inocêncio Seráfico, 3504 - Vila Dirce', NULL, -23.553059, -46.835509, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'node/9711617190', N'Farma Frank I', N'FARMACIA', N'Avenida João de Andrade', NULL, -23.554704, -46.801479, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'relation/12317146', N'AACD - Osasco', N'HOSPITAL', N'Avenida Getúlio Vargas, 1150 - Jardim Piratininga, Osasco', N'+55 11 3604 5155', -23.508497, -46.787952, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'relation/13517704', N'Hospital Doutor Vivaldo Martins Simões', N'HOSPITAL', N'Rua Ari Barroso, 355 - Presidente Altino, Osasco', N'+55 11 3683 3077', -23.524860, -46.771901, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'relation/15591251', N'87º Distrito Policial', N'DELEGACIA', N'Avenida Menotti Laudisio, 286 - Jardim Cidade Pirituba, São Paulo', NULL, -23.481973, -46.725147, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'relation/19567684', N'UBS + AME Lapa', N'UBS', N'Rua Roma, 466 - São Paulo', NULL, -23.522430, -46.701213, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'relation/19960669', N'Pronto Socorro  José Ibrahin', N'HOSPITAL', N'Rua Maria Grandin dos Santos, S/N - Jardim D´Abril, Osasco', N'+55 11 3691 7232', -23.567721, -46.777120, N'DESCONHECIDA', NULL, 1, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1033460496', N'Drogaria São Paulo', N'FARMACIA', N'Avenida Agenor Couto de Magalhães, 25 - Pirituba, São Paulo', NULL, -23.484786, -46.740952, N'DESCONHECIDA', NULL, 1, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1047679594', N'Drogaria São Paulo', N'FARMACIA', N'Rua Carlos Weber, 375 - Vila Leopoldina, São Paulo', NULL, -23.524729, -46.733050, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1047679603', N'Farmácia Sigma', N'FARMACIA', N'Rua Carlos Weber, 248 - Vila Leopoldina, São Paulo', N'+55 11 3641-2069', -23.523743, -46.733625, N'NAO', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1050331344', N'UBS Parque da Lapa', N'UBS', N'Rua Xambica, 20 - Bela Aliança, São Paulo', NULL, -23.531591, -46.720555, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1051379780', N'Drogasil', N'FARMACIA', N'Rua Belmonte, 294 - São Paulo', N'+55 11 3641-0133', -23.521456, -46.722402, N'SIM', NULL, 1, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1053701451', N'Pharmarte', N'FARMACIA', N'Rua Cerro Corá, 1002 - Vila Ipojuca, São Paulo', NULL, -23.538796, -46.706301, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1057634834', N'Drogasil', N'FARMACIA', N'Avenida Cásper Líbero, 120 - Bela Vista', N'+55 11 3682 5692', -23.544507, -46.781955, N'SIM', NULL, 0, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1072937952', N'UBS + AMA Vila Pereira Barreto', N'UBS', N'Rua Dom Manuel D''elboux, 76 - São Paulo', N'+55 11 5239 2940', -23.487912, -46.713500, N'DESCONHECIDA', NULL, 0, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1074884375', N'Hospital Albert Sabin', N'HOSPITAL', N'Rua Brigadeiro Gavião Peixoto, 177 - Lapa, São Paulo', NULL, -23.525752, -46.711653, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1133901601', N'Drogaria São Paulo', N'FARMACIA', NULL, NULL, -23.494054, -46.737143, N'DESCONHECIDA', NULL, 1, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1145194770', N'Farma Conde', N'FARMACIA', N'Avenida Valter Boveri', NULL, -23.563275, -46.797488, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1146509947', N'Unidade Básica de Saúde Maria Pia de Oliveira', N'UBS', N'Rua Saturno, 48 - Jardim Maurinópolis', N'+55 11 3591 6180', -23.561380, -46.798649, N'DESCONHECIDA', NULL, 0, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1147047474', N'Drogaria São Paulo', N'FARMACIA', N'Avenida Antônio Carlos Costa', NULL, -23.554784, -46.786131, N'DESCONHECIDA', NULL, 1, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1149197451', N'Good Farma', N'FARMACIA', NULL, NULL, -23.543792, -46.807577, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1163712733', N'Drogaria e Perfumaria Genesis', N'FARMACIA', N'Rua Paula Rodrigues, 12 - Osasco', N'+55 11 3656-3140', -23.516761, -46.787063, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1174573772', N'Droga Raia', N'FARMACIA', N'Avenida Marcos Penteado de Ulhôa Rodrigues', NULL, -23.466767, -46.846602, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1183565099', N'Hospital Infantil Darcy Vargas', N'HOSPITAL', N'Rua Doutor Serafico de Assis Carvalho, 34', NULL, -23.592743, -46.710942, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1199224064', N'Pronto Socorro do Mutinga', N'HOSPITAL', N'Rua Salvo Veloso, 521 - Jardim Mutinga, Barueri', NULL, -23.500926, -46.814666, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1217672075', N'3º Distrito Policial', N'DELEGACIA', N'Rua Araguari, 24 - Carapicuíba', NULL, -23.532518, -46.822279, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1221447693', N'Hospital Leforte - Unidade Morumbi', N'HOSPITAL', N'Rua dos Três Imãos, 121', NULL, -23.585653, -46.717528, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1222940441', N'Drogasil', N'FARMACIA', N'Avenida Jorge João Saad, 214', NULL, -23.588621, -46.723455, N'SIM', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1226469553', N'Casa de Saúde São João de Deus', N'HOSPITAL', NULL, NULL, -23.475682, -46.754912, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1238052390', N'1º Distrito Policial', N'DELEGACIA', N'Avenida Ruy Barbosa, 1582 - Carapicuíba', NULL, -23.521385, -46.843844, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1243752179', N'Unidade Básica de Saúde Padre Guerino Riciotti', N'UBS', N'Rua Santo Ubaldo, 51 - Vila dos Remédios, Osasco', N'+55 11 3687 8818', -23.521217, -46.754861, N'DESCONHECIDA', NULL, 0, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1286379175', N'Drogasil', N'FARMACIA', N'Rua Jorge Ward, 28', N'+55 11 2924 8006', -23.568723, -46.756483, N'SIM', NULL, 0, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1286379866', N'Pague Menos', N'FARMACIA', N'Avenida do Rio Pequeno, 1193 - Rio Pequeno', N'+55 11 3714 2810', -23.568577, -46.755787, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1289760471', N'UBS Francisco Dias da Silva', N'UBS', N'Rua Marechal Edgar de Oliveira, 800 - Quitaúna, Osasco', NULL, -23.531171, -46.810224, N'DESCONHECIDA', NULL, 0, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1299028159', N'Drogaria Viana', N'FARMACIA', N'Rua dos Manacás, 485 - Jardim da Glória, Cotia', NULL, -23.598199, -46.842124, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1301611351', N'Conde', N'FARMACIA', N'Avenida João Paulo Ablas', NULL, -23.599707, -46.841513, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1302033347', N'2º Distrito Policial', N'DELEGACIA', NULL, NULL, -23.599293, -46.845755, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1328618776', N'Drogaria São Paulo', N'FARMACIA', N'Rua Schilling, 321 - Vila Leopodina, São Paulo', NULL, -23.530103, -46.727706, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1348051378', N'Hospital Municipal e Maternidade Professor  Mario Degni', N'HOSPITAL', N'Rua Lucas de Leyde, 257', N'+55 11 3394 9330', -23.577942, -46.764711, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1348051388', N'Unidade de Pronto Atendimento Rio Pequeno', N'UPA', N'Rua José Vicente da Cruz, 90 - Vila Antônio', NULL, -23.576331, -46.765020, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1349207454', N'Hospital Sancta Maggiore - Unidade Rússia', N'HOSPITAL', NULL, NULL, -23.580435, -46.710929, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1356971101', N'Hospital Cruzeiro do Sul', N'HOSPITAL', N'Avenida dos Autonomistas, 2502 - Centro, Osasco', NULL, -23.537298, -46.776829, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1356971104', N'Hospital São Francisco', N'HOSPITAL', N'Rua Padre Damaso, 100 - Osasco', NULL, -23.536615, -46.776821, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1357801212', N'Drogaria São Paulo', N'FARMACIA', N'Avenida dos Remédios', NULL, -23.518879, -46.753472, N'DESCONHECIDA', NULL, 1, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1357801974', N'Nissei', N'FARMACIA', N'Avenida dos Remédios', NULL, -23.518884, -46.752865, N'DESCONHECIDA', NULL, 0, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1360964940', N'Drogasil', N'FARMACIA', NULL, NULL, -23.586905, -46.719944, N'SIM', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1366009510', N'Weleda', N'FARMACIA', NULL, NULL, -23.589044, -46.715873, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1380027410', N'Drogaria São Paulo', N'FARMACIA', NULL, NULL, -23.579624, -46.711006, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1380027419', N'Drogasil', N'FARMACIA', N'Avenida Professor Francisco Morato, 1310', NULL, -23.581577, -46.712324, N'SIM', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1380296075', N'Farmácia Ananda', N'FARMACIA', N'Avenida Otacílio Tomanik, 1314 - São Paulo', N'+55 11 3769-1000', -23.568333, -46.750150, N'DESCONHECIDA', NULL, 0, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1381173362', N'Famarcia Popular', N'FARMACIA', N'Avenida José Joaquim Seabra - São Paulo', NULL, -23.577936, -46.749506, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1389044514', N'Drogasil', N'FARMACIA', N'Rua Oswaldo Collino, 27', NULL, -23.527444, -46.767366, N'SIM', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1391053419', N'Ultrafarma Popular', N'FARMACIA', N'Avenida Inocêncio Seráfico', NULL, -23.552227, -46.835460, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1391409399', N'Unidade Básica de Saúde Doutor José Marcílio Malta Cardoso', N'UBS', N'Rua Desembargador Homero Pinho, 105 - Jardim Tropical', NULL, -23.570560, -46.760954, N'DESCONHECIDA', NULL, 0, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1391567885', N'UBS Jardim Santo Elias', N'UBS', N'Rua Fábio de Almeida Magalhães, 490 - Jardim Santo Elias, São Paulo', NULL, -23.495926, -46.750048, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1392127851', N'Drogaria São Paulo', N'FARMACIA', N'Avenida Professor Francisco Morato, 2878', N'+55 11 2924 8527', -23.587269, -46.726502, N'DESCONHECIDA', NULL, 0, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1395613617', N'Pronto Socorro Infantil', N'HOSPITAL', N'Avenida General Teixeira Lott', NULL, -23.528170, -46.840510, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1415906664', N'Hospital e Maternidade Amador Aguiar', N'HOSPITAL', NULL, NULL, -23.506854, -46.787945, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1423327387', N'Hospital São Luiz Alphaville', N'HOSPITAL', N'Alameda Araguaia, 2550 - Alphaville Industrial, Barueri', N'+551135152250', -23.502594, -46.837199, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1426718534', N'SUS', N'FARMACIA', N'Rua Catão, 380 - São Paulo', NULL, -23.524288, -46.700658, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1445093629', N'Drogaria São Paulo', N'FARMACIA', N'Rua Cerro Corá, 472 - São Paulo', NULL, -23.540535, -46.701753, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1448258978', N'1º Distrito Policial de Osasco', N'DELEGACIA', N'Rua José Antônio Augusto - Pestana', NULL, -23.541608, -46.801091, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1448309779', N'Unidade Básica de Saúde Eliomar Reis de Oliveira', N'UBS', N'Rua Gilson Nardoni Rodrigues, 147 - Jardim Bonança', NULL, -23.490796, -46.790881, N'DESCONHECIDA', NULL, 0, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1450150487', N'Unidade Básica de Saúde Carolina Maria de Jesus', N'UBS', N'Rua José de Almeida Vargas, 122 - Jaguaribe', N'+55 11 3591 2290', -23.556959, -46.789467, N'DESCONHECIDA', NULL, 0, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1450716723', N'Drogaria São Paulo', N'FARMACIA', N'Avenida José Lourenço, 469 - Osasco', NULL, -23.557389, -46.782150, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1450959081', N'Pronto Socorro Doutor Osmar Mesquita', N'HOSPITAL', N'Rua Maria Carvalho de Lima, 363 - Helena Maria', N'+55 11 2183 6799', -23.498587, -46.793179, N'SIM', NULL, 1, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1451138444', N'Pronto-Socorro Doutor Antônio Flávio França', N'HOSPITAL', N'Rua Águas da Prata - Rochdale', NULL, -23.512073, -46.775654, N'SIM', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1453212957', N'Unidade Básica de Saúde Elzir Maria de Jesus', N'UBS', N'Estrada das Acácias, 202 - Vila Menck', N'+55 11 4207 9127', -23.538557, -46.844418, N'DESCONHECIDA', NULL, 0, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1468996382', N'Pronto Socorro Rômulo Fonseca Guimarães', N'HOSPITAL', N'Rua Abelardo Luz, 524 - Jardim Mutinga', N'+55 11 3164-9600', -23.495810, -46.811945, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/146947250', N'Unidade Básica de Saúde Vila Sônia', N'UBS', N'Rua Abraão Calil Rezek, 91 - São Paulo', N'+55 11 3742-9844', -23.598035, -46.734639, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1478577022', N'UBS', N'UBS', NULL, NULL, -23.584835, -46.799118, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/1479054144', N'Drogaria São Paulo', N'FARMACIA', N'Avenida Vital Brasil, 1250 - Butanta, São Paulo', N'+55 11 3726-7059', -23.568865, -46.714948, N'DESCONHECIDA', NULL, 0, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/150247926', N'UBS Vila Borges', N'UBS', NULL, NULL, -23.587199, -46.770513, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/155852282', N'75º Distrito Policial (Jardim Arpoador)', N'DELEGACIA', N'Rua Mário Ancona', NULL, -23.597292, -46.796041, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/158788584', N'Hospital Universitário', N'HOSPITAL', N'Avenida Professor Lineu Prestes, 2565 - São Paulo', NULL, -23.564734, -46.741149, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/158967600', N'93º Distrito Policial', N'DELEGACIA', NULL, NULL, -23.558783, -46.746682, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/165265046', N'Pronto Socorro Municipal Doutor Caetano Virgílio Neto', N'HOSPITAL', N'Rua Augusto Farina, 1125', NULL, -23.584434, -46.739011, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/200863585', N'Assistência Médica Ambulatorial e Unidade Básica de Saúde Integrada Jardim Paulistano', N'UBS', N'Rua Encruzilhada do Sul, 220 - São Paulo', N'+55 11 3972-2916', -23.461400, -46.709006, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/291547014', N'Droga Raia', N'FARMACIA', N'Avenida Vital Brasil, 752 - São Paulo', N'+55 11 3032-4741', -23.569991, -46.711871, N'DESCONHECIDA', NULL, 1, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/313850657', N'Pronto Socorro Doutor Conrado Cesarino Nuvolini', N'HOSPITAL', N'Avenida João de Andrade, 958 - Jardim Santo Antônio, Osasco', N'+55 11 3609 0283', -23.554718, -46.802012, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/320141806', N'Hospital Central Sorocabana', N'HOSPITAL', N'Rua Catão, 380 - São Paulo', NULL, -23.524331, -46.701319, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/34875563', N'Unidade de Pronto Atendimento Lapa', N'UPA', N'Avenida Queiroz Filho, 313 - Alto da Lapa', NULL, -23.537759, -46.721943, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/381323687', N'FarMais', N'FARMACIA', N'Rua Monteiro de Melo, 276 - São Paulo', NULL, -23.523917, -46.703192, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/381338399', N'Farma&Cia', N'FARMACIA', N'Rua Tito, 1323A - São Paulo', NULL, -23.528138, -46.704614, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/385056284', N'51ª Delegacia de Polícia Rio Pequeno', N'DELEGACIA', N'Rua Barroso Neto, 46 - Vila Indiana, Sao Paulo', N'+55 11 3727-1211', -23.571396, -46.722150, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/395910877', N'Centro de Saúde Escola Samuel Barnsley Pessoa', N'UBS', N'Avenida Vital Brasil, 1490 - Butantã, São Paulo', NULL, -23.568311, -46.716606, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/405574870', N'Drogasil', N'FARMACIA', N'Rua Pirajussara, 480 - São Paulo', N'+55 11 3034-0574', -23.571238, -46.707682, N'SIM', NULL, 0, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/410892257', N'Drogasil', N'FARMACIA', N'Avenida Nossa Senhora de Assunção, 638 - Vila Butantã, São Paulo', NULL, -23.570704, -46.744092, N'SIM', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/424941734', N'Unidade de Pronto Atendimento Municipal Centro', N'UPA', NULL, NULL, -23.531276, -46.776353, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/424946527', N'Hospital Municipal Antônio Giglio', N'HOSPITAL', N'Rua Pedro Fioretti, 48 - Centro, Osasco', N'+55 11 2183 9333', -23.532245, -46.777205, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/426008304', N'Hospital e Maternidade São Luiz Osasco', N'HOSPITAL', N'Avenida Marechal Rondon, 299 - Centro, Osasco', N'+55 11 3652 8000', -23.531846, -46.781523, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/428753286', N'5º Distrito Policial de Osasco', N'DELEGACIA', N'Avenida Marechal Rondon, 300 - Centro, Osasco', NULL, -23.531043, -46.781548, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/428753287', N'Droga Raia', N'FARMACIA', N'Rua Antônio Agú, 970 - Centro, Osasco', N'+55 11 3699 0808', -23.535541, -46.780563, N'DESCONHECIDA', NULL, 1, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/432099700', N'Droga Raia', N'FARMACIA', N'Avenida Professor Francisco Morato, 2858 - Vila Sônia, São Paulo', NULL, -23.586978, -46.725995, N'DESCONHECIDA', NULL, 1, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/439088478', N'Drogaria São Paulo', N'FARMACIA', N'Rua dos Três Imãos, 651 - São Paulo', N'+55 11 3722-5029', -23.589942, -46.715390, N'DESCONHECIDA', NULL, 1, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/462846028', N'Unidade Básica de Saúde Cruz das Almas', N'UBS', N'Rua Padre Feliciano Domingues, 90 - São Paulo', N'+55 11 3975-3531', -23.483787, -46.703160, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/468420898', N'Policlínica Dona Leonil Crê Bortolosso/ Zona Norte', N'HOSPITAL', N'Avenida Getúlio Vargas, 889 - Jardim Piratininga, Osasco', NULL, -23.513912, -46.788994, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/479640405', N'Drograia São Paulo', N'FARMACIA', N'Rua Pio XI, 1377 - Alto da Lapa, São Paulo', N'+55 11 3021-6397', -23.535549, -46.714135, N'DESCONHECIDA', NULL, 1, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/481652739', N'Droga Raia', N'FARMACIA', N'Rua Ushima Kira, 485 - Granja Viana, Cotia', N'+55 11 4617 3502', -23.592106, -46.835492, N'DESCONHECIDA', NULL, 0, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/485425303', N'34º Distrito Delegacia de Polícia Civil', N'DELEGACIA', N'Avenida Professor Francisco Morato, 2971', NULL, -23.588352, -46.726720, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/520075842', N'Drogaria São Paulo', N'FARMACIA', NULL, NULL, -23.553262, -46.710032, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/520076544', N'Drogasil', N'FARMACIA', NULL, NULL, -23.554754, -46.709834, N'SIM', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/520921378', N'Drogaria São Paulo', N'FARMACIA', N'Rua Passo da Pátria, 1101 - Bela Aliança, São Paulo', NULL, -23.526874, -46.728580, N'DESCONHECIDA', NULL, 0, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/520922063', N'Droga Raia', N'FARMACIA', N'Rua Schilling, 390 - Vila Leopodina, São Paulo', NULL, -23.530186, -46.728545, N'SIM', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/520923746', N'Farmácias Nissei', N'FARMACIA', N'Rua Schilling, 606 - Vila Leopoldina, São Paulo', NULL, -23.531325, -46.730185, N'SIM', NULL, 1, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/546125776', N'Hospital Geral de Carapicuíba Doutor Francisco de Moura Coutinho Filho', N'HOSPITAL', N'Rua da Pedreira, 95 - Parque José Alexandre, Carapicuíba', N'+55 11 4185 7600', -23.534124, -46.830304, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/548857758', N'Unidade Básica de Saúde Doutor Eurico Souto Cabral', N'UBS', N'Avenida Consolação, 505 - Vila Gustavo Correia, Carapicuíba', NULL, -23.516154, -46.834137, N'DESCONHECIDA', NULL, 0, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/595650045', N'Unidade Básica de Saúde Jardim Boa Vista', N'UBS', N'Rua Cândido Fontoura, 620 - Jardim Boa Vista, São Paulo', N'+55 11 3782 8380', -23.584041, -46.791250, N'DESCONHECIDA', NULL, 0, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/599507017', N'Unidade de Pronto Atendimento Cohab II', N'UPA', N'Avenida do Bosque, 410 - Cohab II, Carapicuíba', N'+55 11 4185 3187', -23.530183, -46.827719, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/599507589', N'Unidade Básica de Saúde COHAB II', N'UBS', N'Avenida do Bosque, 410 - Cohab II, Carapicuíba', N'+55 11 4164 3113', -23.529966, -46.827724, N'DESCONHECIDA', NULL, 0, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/599509089', N'Policlínica Carapicuíba', N'HOSPITAL', NULL, NULL, -23.571232, -46.824047, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/612100207', N'Unidade Básica de Saúde Irmã Águeda Maria Jaime', N'UBS', N'Rua Alagoana, 100 - Conceição, Osasco', N'+55 11 3591 4411', -23.578144, -46.800826, N'DESCONHECIDA', NULL, 0, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/637694440', N'6º Distrito Policial da Polícia Civil', N'DELEGACIA', N'Rua Clotilde Calesi, 133 - Vila Osasco, Osasco', NULL, -23.543658, -46.777263, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/641688037', N'Hospital Nossa Senhora de Fátima', N'HOSPITAL', N'Praça Duque de Caxias, 54 - Centro', N'+55 11 2575 1200', -23.536649, -46.781491, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/644486335', N'Unidade Básica de Saúde Anunciata de Lúcia', N'UBS', N'Rua São Jorge', NULL, -23.534558, -46.803865, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/647807625', N'Drogaria Brandão', N'FARMACIA', N'Avenida Santo Antônio, 1666 - Vila Osasco, Osasco', NULL, -23.541798, -46.778364, N'DESCONHECIDA', NULL, 0, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/654314444', N'Drogasil', N'FARMACIA', N'Avenida Hildebrando de Lima, 431', NULL, -23.530803, -46.793845, N'SIM', NULL, 1, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/664738673', N'Unidade Básica de Saúde Francisca Lima de Lira', N'UBS', N'Avenida Juscelino Kubitschek de Oliveira, 100', N'+55 11 3656 3227', -23.493912, -46.798485, N'DESCONHECIDA', NULL, 0, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/672938979', N'Pronto Socorro André Sacco', N'HOSPITAL', N'Rua General Pedro Pinho, 1270 - Vila Pestana', N'+55 11 3683 -9369', -23.541913, -46.801041, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/675406741', N'Drogaria Bem Estar', N'FARMACIA', N'Avenida Ayrton Senna - Jardim Santa Maria, Osasco', NULL, -23.583260, -46.810968, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/678966520', N'Pronto Socorro José Agostinho dos Santos', N'HOSPITAL', N'Rua José Martinho', NULL, -23.485929, -46.804468, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/678969571', N'Unidade Básica de Saúde Amaro José de Souza', N'UBS', N'Rua Petrolina, 178 - Jardim Mutinga', N'+55 11 4199 3170', -23.504364, -46.811919, N'DESCONHECIDA', NULL, 0, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/681136870', N'Unidade Básica de Saúde Jardim Jaqueline', N'UBS', NULL, NULL, -23.594816, -46.751816, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/681292675', N'Unidade Básica de Saúde Armando Gonçalves de Freitas', N'UBS', N'Rua Padre Cicero Romão Batista, Armando Gonçalves de Freitas - Parque Imperial', N'+55 11 4199 3163', -23.488723, -46.813624, N'DESCONHECIDA', NULL, 0, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/684800165', N'Drogasil', N'FARMACIA', N'Rua Rosalio José da Conceição, 29 - Jardim Íris, São Paulo', NULL, -23.497025, -46.726138, N'SIM', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/698305920', N'Ubs Jardim Guanabara', N'UBS', N'Avenida Ministro Petrônio Portela, 663 - São Paulo', NULL, -23.493320, -46.701020, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/721628995', N'UBS Butantã', N'UBS', N'Rua Cabral de Menezes, 51 - Vila Gomes, São Paulo', N'+55 11 3726-1838', -23.577049, -46.733995, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/727276295', N'Drogasil', N'FARMACIA', N'Avenida Eliseu de Almeida', NULL, -23.589230, -46.734609, N'SIM', NULL, 1, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/75490092', N'Hospital Israelita Albert Einstein', N'HOSPITAL', NULL, NULL, -23.599832, -46.715351, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/766490242', N'Droga Raia', N'FARMACIA', N'Rua Tito - São Paulo', NULL, -23.528170, -46.704276, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/780039865', N'Farmais', N'FARMACIA', N'Rua Cecilia Calovini, 23 - São Paulo', NULL, -23.481595, -46.717221, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/785459956', N'Hospital Sao Luis Morumbi', N'HOSPITAL', NULL, NULL, -23.590905, -46.703757, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/79392965', N'Casa de Saúde Nossa Senhora de Fátima', N'HOSPITAL', N'Avenida Jornalista Paulo Zingg, 1078', NULL, -23.487787, -46.760951, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/79442277', N'Hospital Municipal Doutor José Soares Hungria', N'HOSPITAL', N'Avenida Menotti Laudisio, 100', N'+55 11 3394-8600', -23.484643, -46.727529, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/79877355', N'Centro de Atenção Integrada à Saúde Mental Philippe Pinel', N'HOSPITAL', N'Avenida Raimundo Pereira de Magalhães, 5214 - Pirituba', NULL, -23.486790, -46.724024, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/816792425', N'Hospital Luz Butantã', N'HOSPITAL', N'Avenida Professor Francisco Morato, 719 - São Paulo', N'+55 11 3094 2300', -23.578248, -46.708134, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/835605847', N'Unidade Básica de Saúde Santa Maria Goretti', N'UBS', N'Avenida Analice Sakatauskas, 145 - Vila Aliança', N'+55 11 3654 078', -23.548216, -46.776266, N'DESCONHECIDA', NULL, 0, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/846560981', N'Hospital Saint Patrick Portinari', N'HOSPITAL', N'Praça General Guimarães, 77 - Vila Jaguara, São Paulo', N'+55 11 3465-0700', -23.508419, -46.751151, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/865953212', N'Droga Raia', N'FARMACIA', N'Avenida Corifeu de Azevedo Marques, 996 - São Paulo', N'+55 11 3721-2053', -23.572505, -46.723741, N'DESCONHECIDA', NULL, 0, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/921028275', N'Unidade Básica de Saúde Vila Nova Jaguaré', N'UBS', N'Rua Salatiel de Campos, 222', N'+55 11 3768 1527', -23.543007, -46.744557, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/937790535', N'Unidade Básica de Saúde Nancy Abranches', N'UBS', N'Rua Ladislau Roman, 410 - Caxingui, São Paulo', N'+55 11 3721 6406', -23.579096, -46.714953, N'DESCONHECIDA', NULL, 0, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/991316193', N'Drogaria São Paulo', N'FARMACIA', N'Avenida Inocêncio Seráfico, 1725', NULL, -23.536558, -46.835552, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/992025208', N'Pronto Socorro Vila Dirce', N'HOSPITAL', N'Estrada Ernestina Vieira, 70 - Vila Dirce', N'+55 11 4188 8309', -23.553064, -46.836319, N'DESCONHECIDA', NULL, NULL, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/992238817', N'Unidade Básica de Saúde Doutor Adauto Ribeiro', N'UBS', N'Estrada da Gabiroba, 519 - Santo Estevão', N'+55 11 4187 0112', -23.544364, -46.828995, N'DESCONHECIDA', NULL, 0, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),
(N'way/996638730', N'Droga Raia', N'FARMACIA', N'Rua Cerro Corá, 1917 - São Paulo', NULL, -23.535535, -46.713018, N'DESCONHECIDA', NULL, 1, N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18')
) AS origem (osm_id, nome, tipo, endereco, telefone, latitude, longitude, acessibilidade, banheiro_adaptado, funciona_24h, fonte, atualizado_em)
ON destino.osm_id = origem.osm_id
WHEN MATCHED THEN UPDATE SET nome = origem.nome, tipo = origem.tipo, endereco = origem.endereco, telefone = origem.telefone, latitude = origem.latitude, longitude = origem.longitude, acessibilidade = origem.acessibilidade, banheiro_adaptado = origem.banheiro_adaptado, funciona_24h = origem.funciona_24h, fonte = origem.fonte, atualizado_em = origem.atualizado_em
WHEN NOT MATCHED THEN INSERT (osm_id, nome, tipo, endereco, telefone, latitude, longitude, acessibilidade, banheiro_adaptado, funciona_24h, fonte, atualizado_em) VALUES (origem.osm_id, origem.nome, origem.tipo, origem.endereco, origem.telefone, origem.latitude, origem.longitude, origem.acessibilidade, origem.banheiro_adaptado, origem.funciona_24h, origem.fonte, origem.atualizado_em);
