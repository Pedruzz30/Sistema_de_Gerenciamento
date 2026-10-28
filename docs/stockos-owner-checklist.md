# StockOS — o que está pronto e o que depende do responsável

**Estado:** desenvolvimento isolado na PR #3. Não implantar, nem fazer merge,
até que a segurança e o fluxo comercial sejam auditados.

## Trabalho que pode ser preparado sem credenciais pessoais
- Código Java, migrations Flyway, interfaces, suíte de testes, workflows de CI.
- Testes PostgreSQL local descartável, HTTP JWT simulado, browser Chromium,
  assinatura RSA com JWKS de teste e backup/restauração do CI.
- Modelagem e documentação técnica, revisão de configurações, plano de rollout.
- Políticas de isolamento por tenant para futuro runtime PostgreSQL restrito
  **ainda precisam implementação e testes**.

## Decisões/autorização necessárias (quando estiver disponível)

### 1. Novo Supabase SOMENTE para StockOS
- **Concluído:** projeto `StockOS Staging` criado na organização Space Underground,
  com banco independente na região São Paulo (`sa-east-1`), ref `hucfndwhaebvithtkovn`.
- **Concluído:** 13 migrations privadas aplicadas, 23 tabelas com RLS; nenhuma tabela legada. Ainda faltam papel JDBC de runtime e isolamento RLS por tenant.
- Verificar plano/custos e política de recuperação antes da implantação.
- Configurar Auth: confirmação de e-mail, URLs de redirect, domínio futuro e
  provedor SMTP transacional. Usar chave de assinatura JWT assimétrica.
- Obter URL do projeto, issuer e **publishable key**; as chaves `sb_secret`
  e `service_role` não podem ir para o navegador, chat ou GitHub.
- Confirmar custo/plano de backups, retenção e política de restauração.
- Após isso, executar staging real de Auth/JWKS/convites antes de produção.

### 2. Hospedagem e domínio
- Escolher hospedeiro da API Java, nome de domínio e responsável pelas contas.
- Autorizar a criação do serviço e configurar variáveis privadas via painel.
- Configurar TLS, limites de rede, logs, backups, healthcheck e DNS.
- Não habilitar `spring.profiles.active=prod` ou remover `ProductionSafetyGate`
  antes do checklist de segurança estar aprovado.

### 3. Comercial, financeiro e jurídico
- Definir preço e limites dos planos e o responsável pelo recebimento.
- Escolher gateway, cadastrar conta recebedora e ativar webhook validado.
- Definir política fiscal, LGPD, termos, retenção e exclusão de dados.
- Autorizar uma operação piloto com dados fictícios/consentidos e criar
  usuários reais separados para proprietário, gerente e atendente.

### 4. Aceite antes do piloto
- Validar E2E real de autenticação, convite, troca de papel, empresa e filial.
- Validar isolamento multiempresa com conexão SQL sem BYPASSRLS.
- Testar falha de rede, reenvio, concorrência de estoque, estorno, recuperação
  de backup e reconciliação de caixa.
- Fazer revisão de segurança, UX no celular e capacidade de suporte.

**Não envie senhas, banco completo ou chaves secretas aqui.**
Usar o gerenciador de segredos do hospedeiro e, no GitHub,
Actions secrets específicos do ambiente quando realmente necessários.

## Configuração já prevista
```text
STOCKOS_SECURITY_MODE=supabase-jwt
STOCKOS_SUPABASE_AUTH_ISSUER=https://SEU_PROJETO.supabase.co/auth/v1
STOCKOS_SUPABASE_URL=https://SEU_PROJETO.supabase.co
STOCKOS_SUPABASE_PUBLISHABLE_KEY=sb_publishable_...
DB_URL=jdbc:postgresql://...
DB_USERNAME=...   (usuário runtime restrito, a configurar)
DB_PASSWORD=...   (segredo apenas no ambiente, nunca no Git)
```

Os valores acima são placeholders e não são credenciais operacionais.
