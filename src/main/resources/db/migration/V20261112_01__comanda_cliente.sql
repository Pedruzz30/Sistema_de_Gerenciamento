-- Nome de quem está com a comanda (opcional): aparece junto do número nas telas, no celular
-- e no papel da cozinha, para achar a conta pelo nome e não trocar uma mesa pela outra.
alter table comandas add column cliente varchar(60);
