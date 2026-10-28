-- Keeps the existing employee model while linking it to a verified Supabase Auth subject.
-- Provisioning must be done by a trusted admin process, never by a public API.
alter table usuarios add column if not exists auth_user_id uuid;

create unique index if not exists uk_usuarios_auth_user_id
    on usuarios (auth_user_id)
    where auth_user_id is not null;
