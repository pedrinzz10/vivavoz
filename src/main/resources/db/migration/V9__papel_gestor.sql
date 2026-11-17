-- Papel GESTOR: servidor da prefeitura que atende a zeladoria na API .NET. O órgão vai no token (claim "orgao").
ALTER TABLE usuarios DROP CONSTRAINT ck_usuarios_papel;
ALTER TABLE usuarios ADD CONSTRAINT ck_usuarios_papel CHECK (papel IN ('CIDADAO', 'CUIDADOR', 'GESTOR'));
ALTER TABLE usuarios ADD orgao NVARCHAR(20) NULL
    CONSTRAINT ck_usuarios_orgao CHECK (orgao IN ('OBRAS', 'ILUMINACAO', 'MOBILIDADE', 'LIMPEZA'));
