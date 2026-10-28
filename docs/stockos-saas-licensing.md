# StockOS — Licenciamento manual para o piloto

O StockOS está sendo desenvolvido como SaaS, mas **não há cobrança automática,
gateway de pagamento ou renovação integrada neste PR**.

## Modelo inicial

- `planos`: código, nome, preço mensal de referência, limite de filiais/usuários,
  módulo de comandas habilitado.
- `licencas`: vínculo 1:1 com empresa, plano, estado (`TRIAL`, `ATIVA`,
  `SUSPENSA`, `CANCELADA`), início e fim da vigência e preço contratado
  registrado no momento da contratação.
- A licença é válida **somente** quando o estado é `TRIAL` ou `ATIVA`,
  o plano está ativo, o módulo de comandas está habilitado e a vigência inclui
  o instante atual. Não existe renovação automática implícita.
- A API `GET /api/saas/empresas/{empresaId}/licenca` devolve o estado da
  licença para **membros autenticados** mesmo após o vencimento.
- As rotas operacionais exigem vínculo do JWT à empresa **e** licença ativa;
  a consulta de empresas e filiais permanece disponível ao membro expirado.

## Segurança e operação

As tabelas de planos e licenças ficam no schema `stockos_private`, com
RLS ativo e sem políticas para acesso direto pelo cliente Supabase.

**Não existe API pública para emitir/renovar licenças.** O provisionamento deve
ser feito exclusivamente por um operador confiável, em banco *dedicado ao StockOS*
e em ambiente de estágio, com procedimento auditado.

Antes de vender o serviço será necessário implementar o painel administrativo
da Space Underground, trilha de auditoria para emissão/renovação/cancelamento,
gestão real de pagamentos, limites de planos, recuperação de dados, exportação
de dados no cancelamento, contrato e política de privacidade.

**Limitação do piloto:** a checagem atual protege as APIs SaaS que passam por
`SaasTenantAccess.requireMembership`. APIs legadas continuam indisponíveis
no modo JWT; nenhuma API deve burlá-lo quando for ativada.
