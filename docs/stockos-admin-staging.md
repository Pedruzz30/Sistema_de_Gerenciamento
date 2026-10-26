# Console administrativo da Space Underground (piloto)

## URL de desenvolvimento
- Interface: `/saas-admin.html`
- APIs: `/api/admin/stockos/{planos,empresas,auditoria}`
- Licenciamento: `PUT /api/admin/stockos/empresas/{empresaId}/licenca`

## Segurança
- **Desativado por padrão.** Para testes locais em modo JWT, configure:
  - `STOCKOS_ADMIN_ENABLED=true`
  - `STOCKOS_ADMIN_AUTH_USER_IDS=<uuid-auth-confirmado>`
- Usuários autorizados são identificados exclusivamente pelo JWT validado. Emails,
  `user_metadata` e papéis `PROPRIETARIO` de estabelecimentos **não**
  concedem privilégios administrativos.
- Testar somente com um **Supabase separado** para StockOS e base PostgreSQL
  descartável. A URL e a chave *publicável* de Auth são configurações de frontend;
  chaves secretas nunca entram no navegador.
- Todos os cadastros e alterações são transacionais e geram registro em
  `stockos_private.admin_auditoria` na mesma transação.
- O proprietário deve ser provisionado no Supabase Auth antes de cadastrar a
  empresa. Não há convite automático nem verificação do UUID informado;
  conferir identidade antes de aprovar o cadastro.
- Preço e período são definidos manualmente por operador autorizado. Não há
  checkout, webhook, nota fiscal, cobrança recorrente ou renovação automática.
- Limites de plano ainda não são impostos nas rotas operacionais.
- **Não liberar produção nem remover o bloqueio do perfil `prod`.**

## Antes do piloto
Adicionar testes de integração PostgreSQL das migrações, invariantes transacionais,
limites de plano, integridade do provisionamento, auditoria, isolamento entre
estabelecimentos e autenticação real. Criar processo de backups e restauração.
