# Banco de dados

O arquivo `001_initial_schema.sql` representa o primeiro protótipo e **não deve ser aplicado em uma instalação nova**.

Para a arquitetura atual:

1. Execute `npm run auth:migrate` dentro de `backend/` para criar/atualizar o schema do Better Auth.
2. Aplique `002_better_auth_business_schema.sql` para criar as tabelas específicas do Ponto Café.
3. Aplique `003_device_unlock_pin.sql` para habilitar PIN individual e proteção contra tentativas de desbloqueio em cada dispositivo.
4. Aplique `004_device_registration_idempotency.sql` para evitar cadastros/tokens duplicados em retentativas de rede. O token de replay fica cifrado por poucos minutos e é removido pela manutenção agendada.
5. Execute `npm run auth:bootstrap` uma única vez para criar o primeiro administrador.

Depois disso, novos supervisores devem ser criados pelo recurso Admin do Better Auth com o papel `user`. Dentro do Ponto Café, esse papel é interpretado como `SUPERVISOR`.

O aplicativo Android nunca recebe a `DATABASE_URL`, nenhum PIN de desbloqueio é salvo em texto puro no PostgreSQL e o token de ativação permanece persistido normalmente apenas como hash; a única cópia reversível usada para replay idempotente é cifrada com AES-256-GCM e possui TTL curto.
