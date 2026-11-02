
-- First step of the Supabase Auth migration. NO automatic email/CPF matching:
-- links must be created explicitly by an administrator, offline.
-- This migration remains in the backend Flyway history, not Supabase CLI history.
alter table public.usuarios
    add column if not exists supabase_auth_user_id uuid;

create unique index if not exists uk_usuarios_supabase_auth_user_id
    on public.usuarios (supabase_auth_user_id)
    where supabase_auth_user_id is not null;
