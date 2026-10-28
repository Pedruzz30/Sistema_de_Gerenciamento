# Gestão inicial de funcionários
O proprietário autenticado pode alterar papéis e ativar/desativar membros já existentes.
Mudanças geram auditoria no banco, respeitam o limite max_usuarios na reativação
e proíbem remover o último proprietário.
Endpoints: GET /api/saas/empresas/{id}/funcionarios;
PUT /api/saas/empresas/{id}/funcionarios/{authUserId};
GET /api/saas/empresas/{id}/funcionarios/auditoria.

Não existem criação de contas nem convites. O sistema nunca aceita UUID arbitrário
do navegador como prova de propriedade de identidade. Implementar convite com
identidade de email confirmada em Supabase Auth antes do primeiro cliente.
Não implantar em produção.