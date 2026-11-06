-- Login seguro: sessões no servidor, PIN de operador e senha provisória.
--
-- As senhas em texto puro são convertidas para hash BCrypt pela aplicação na
-- inicialização (AutenticacaoService.migrarSenhasAntigas): SQL puro não tem BCrypt
-- sem a extensão pgcrypto. O hash tem 60 caracteres e cabe em varchar(120).

alter table usuarios
    add column pin_hash varchar(72);

-- Verdadeiro: a pessoa precisa criar uma senha nova antes de usar o sistema.
alter table usuarios
    add column senha_provisoria boolean not null default false;

create table sessoes_usuario (
    id bigserial primary key,
    token_hash varchar(64) not null,
    usuario_ru bigint not null references usuarios(ru),
    via_pin boolean not null default false,
    criada_em timestamp not null,
    expira_em timestamp not null,
    constraint uk_sessoes_usuario_token unique (token_hash)
);

create index idx_sessoes_usuario_expira on sessoes_usuario(expira_em);
