# StockOS — Segurança multi-tenant: fundação (não pronta para produção)

## Objetivo

Cada estabelecimento é um tenant independente. O dono entra com identidade
verificada do Supabase Auth e gerencia seus operadores; funcionários usam RU
**local à loja** + PIN **apenas em terminais pareados**, futuramente. O RU é
identificador público, nunca senha. Proprietário não empresta sessão a caixa.

## Implementado nesta PR (somente fundação)

- Tabela `estabelecimentos` com UUID e estado `ATIVO` ou `SUSPENSO`.
- Tabela `usuarios_estabelecimentos`, vínculo explícito entre loja e usuário
  operacional existente, papel (`PROPRIETARIO`, `GERENTE`, `OPERADOR`) e RU local
  único por loja. Um RU local pode repetir em outra loja.
- `TenantMembershipGuard`: exige vínculo, operador e loja ativos. Rejeita ausência
  de vínculo, troca de loja, suspensão, revogação e papel não autorizado.
- Testes de integração de autorização e constraints do PostgreSQL.
- **Nenhum** proprietário ou estabelecimento é criado automaticamente;
  **nenhum** dado legado é migrado, reclassificado ou eliminado.

## Restrições de segurança e etapas obrigatórias

1. **Ainda não há isolamento de dados operacionais**. `produtos`,
   `fornecedores`, `caixas`, `vendas`, `notas_fiscais`, logs e outras tabelas
   continuam globais. A coluna antiga `vendas.filial_id` não garante isolamento.
2. Esta guarda não foi acoplada aos endpoints legados. **Não se deve ativar
   multi-tenancy publicamente** apenas por existir uma tabela de vínculos.
3. Próxima etapa: mapear todas as tabelas e suas relações; introduzir
   `estabelecimento_id` obrigatório em operações novas e constraints/FKs
   compostas que impeçam referenciar objetos de outra loja. Planejar a
   associação dos dados legados somente com inventário e confirmação do dono.
4. As queries JDBC/JPA de leitura e escrita devem filtrar por **tenant derivado
   do contexto autenticado** e rejeitar IDs externos antes de retornar dados.
   Guardas antes da query não substituem filtro SQL. Cobrir transações,
   paginação, relatórios, exportação, auditoria e processos assíncronos.
5. Ao integrar a PR de Supabase Auth, `verifiedUsuarioRu` deve vir do token
   validado e vínculo server-side; nunca confiar em RU, tenant ID ou papel
   arbitrário enviado no cabeçalho, body, query string ou localStorage.
6. Para terminal compartilhado: dispositivo pareado/revogável, PIN individual
   protegido por hash Argon2id (ou equivalente), limitação de tentativas,
   sessão de operador curta, reautenticação para estornos e ações críticas.
7. Remover as senhas legadas em texto puro e qualquer modo de login legado
   antes de deploy público. MFA obrigatório para dono/administrador.
8. Testar dois tenants com IDs que colidem e tentativas cruzadas em TODOS os
   endpoints, inclusive erro sem revelar a existência dos recursos.
9. Se usar Supabase Data API, revisar RLS. O backend JDBC deve impor suas
   próprias políticas: RLS de um client Supabase não protege automaticamente
   a conta JDBC do Spring.
10. Separar credenciais do migrador Flyway das credenciais de execução Java:
    a conexão da aplicação **não pode utilizar o superusuário PostgreSQL**
    usado no Docker local. O papel de aplicação deve ter privilégios mínimos,
    sem BYPASSRLS e sem permissões para DDL ou outros bancos. Uma mesma conta
    superuser para todos os tenants anula boa parte da defesa em profundidade.
11. Revisão de logs/PII, backups por tenant, proteção de cookies/tokens,
    limites de acesso e resposta a incidentes antes do piloto.

## Matriz mínima de ataques de regressão

| Área | Tentativa proibida | Resultado requerido |
| --- | --- | --- |
| Autenticação | JWT sem assinatura, com audiência errada ou expirado | 401 |
| Identidade | Alterar RU ou papel no cabeçalho/JSON | Identidade vinculada server-side prevalece |
| Associação | Funcionário da loja A acessa loja B | 403/404 genérico, sem dados |
| Estado | Usuário revogado ou loja suspensa usa sessão antiga | Acesso negado |
| Estoque | ID de produto de B em operação de A | Nenhuma leitura, baixa ou modificação |
| Caixa | Terminal de A finaliza venda de B | Negado e registrado |
| Relatórios | Paginação/totalização/exportação cruzada | Valores exclusivamente do tenant autenticado |
| Relacionamentos | Nota fiscal de A referencia fornecedor/produto de B | Constraint ou operação rejeita |
| Background | Job agendado processa linhas de outra loja | Escopo de tenant obrigatório e auditável |
| Banco | Credencial da API tenta DDL ou acesso administrativo | Permissão negada |

Não considerar a plataforma pronta enquanto esta matriz não estiver executada
contra os endpoints e transações reais. Testes de associação isolados não
comprovam a proteção das operações de negócio.

**Status:** trabalho de infraestrutura numa PR draft. Não conectar à Cloud
nem executar migrações manualmente em ambiente de cliente.
