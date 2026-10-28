# StockOS Staging — projeto Supabase identificado (28/10/2026)

Este é o **único projeto autorizado para testes do StockOS**.

| Atributo | Valor |
| --- | --- |
| Organização | Space Underground |
| Projeto | StockOS Staging |
| Project ref | `earocdtfxtvcsfsfxbvt` |
| Região existente | `us-east-1` (não São Paulo) |
| PostgreSQL | 17.11 |
| URL pública do projeto | `https://earocdtfxtvcsfsfxbvt.supabase.co` |
| Issuer de tokens | `https://earocdtfxtvcsfsfxbvt.supabase.co/auth/v1` |
| JWT signing alg | **Ainda não verificado no projeto real** |
| Conta de usuário de testes | Nenhuma, na inspeção inicial |
| Tabelas SaaS | Nenhuma, na inspeção inicial |
| Chave publicável | Existe `sb_publishable_...`, obter diretamente no painel ou conectar por configuração privada |
| Banco real conectado ao Java | **Ainda não** |
| Migrations Flyway executadas | **Nenhuma** |

As verificações foram somente leitura pelo conector Supabase. `get_advisors(security)`
retornou zero alertas iniciais. Isso não equivale a auditoria completa ou
confirmação das opções do Data API marcadas na criação.

## Variáveis previstas para Java (valores públicos, SEM segredos)

```env
STOCKOS_SECURITY_MODE=supabase-jwt
STOCKOS_SERVER_ADDRESS=127.0.0.1
STOCKOS_SUPABASE_EXPECTED_PROJECT_REF=earocdtfxtvcsfsfxbvt
STOCKOS_SUPABASE_URL=https://earocdtfxtvcsfsfxbvt.supabase.co
STOCKOS_SUPABASE_AUTH_ISSUER=https://earocdtfxtvcsfsfxbvt.supabase.co/auth/v1
STOCKOS_SUPABASE_PUBLISHABLE_KEY=sb_publishable_COLE_A_CHAVE_PUBLICA_NO_AMBIENTE
SPRING_PROFILES_ACTIVE=staging
FLYWAY_BASELINE_ON_MIGRATE=false
```

**Não** colocar `DB_PASSWORD`, `sb_secret`, JWT de sessão ou `service_role`
no Git, mensagens ou códigos públicos. Obter a URL JDBC e certificado diretamente
em *Connect > Database connection* do novo projeto, e configurar em ambiente
privado (SSL verificado). Os defaults do backend não são credenciais de staging.

## Sequência segura para conectar de verdade

1. No projeto novo, verificar *Authentication > Signing Keys*: escolher uma
   chave de assinatura **assimétrica** ativa e garantir que seu JWKS público
   esteja disponível. O backend não aceita HS256 legado.
2. Verificar *Settings > Data API* (se realmente desabilitada) e *Authentication >
   Providers > Email*: confirmação por e-mail, URLs de redirecionamento e políticas.
3. Preparar os scripts Flyway com **separação do schema legado em `public`**.
   Atualmente o jar executa migrações `V20260417_00`… que criam tabelas
   legadas públicas, além das migrations `stockos_private`. **Não rodar
   automaticamente todas em staging até resolver essa separação**.
4. Depois de validar a estratégia Flyway, aplicar migrations no banco novo
   usando papel de migração, sem misturar histórico do Supabase CLI.
5. Criar contas fictícias e seed seguro por administrador autorizado; validar
   login e aceitação de convites com token e e-mail confirmados.
6. Configurar usuário PostgreSQL de runtime com menor privilégio e revisar
   tenant/RLS antes de expor endpoints.
7. Executar testes de isolamento multiempresa, caixa, estoque, devoluções
   e backup/restauração. `ProductionSafetyGate` continua bloqueando `prod`.

## Regras de proteção

- NÃO acessar `zvzfkfvxbuofgqrrogxh` (Space Underground) ou
  `bhezekckmyrmlxovwfwr` (projeto anterior) para qualquer ação StockOS.
- Nunca usar o banco StockOS como substituto do banco Space.
- Não fazer merge da PR #3 nem deploy de produção sem revisão.

Documentação oficial:
https://supabase.com/docs/guides/getting-started/quickstarts/spring-boot
https://supabase.com/docs/guides/auth/signing-keys
https://supabase.com/docs/guides/api/securing-your-api
