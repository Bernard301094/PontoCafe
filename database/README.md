# Banco de dados

O arquivo `001_initial_schema.sql` representa o primeiro protótipo e **não deve ser aplicado em uma instalação nova**.

Para a arquitetura atual:

1. Execute `npm run auth:migrate` dentro de `backend/` para criar/atualizar o schema do Better Auth.
2. Aplique `002_better_auth_business_schema.sql` para criar as tabelas específicas do Ponto Café.
3. Aplique `003_device_unlock_pin.sql` para habilitar PIN individual e proteção contra tentativas de desbloqueio em cada dispositivo.
4. Aplique `004_device_registration_idempotency.sql` para evitar cadastros/tokens duplicados em retentativas de rede. O token de replay fica cifrado por poucos minutos e é removido pela manutenção agendada.
5. Aplique `005_multi_face_templates.sql` junto da versão de backend que suporta múltiplos templates. Essa migração remove a unicidade de `colaborador_id` em `templates_faciais` e permite manter templates biométricos complementares já suportados pelo backend. **Não aplique a 005 isoladamente mantendo o backend antigo em produção**, pois a rota antiga de cadastro ainda depende da restrição única anterior.
6. Aplique `006_collaborator_avatar_metadata.sql` para os metadados de avatar visual dos colaboradores. Avatar permanece separado da biometria.
7. **Antes de implantar o Worker 0.15.0 ou 1.0.0**, aplique `007_ponto_operation_idempotency.sql`. Ela cria o diário transacional exactly-once para `REGISTRO_RAPIDO`, `INICIAR` e `FINALIZAR`, permitindo replay seguro e reconciliação online → offline quando uma resposta de rede é perdida.
8. Para a candidata **1.0.0**, aplique `008_release_readiness_indexes.sql` depois da 007. Ela adiciona somente índices idempotentes para a retenção do diário exactly-once e a leitura/retenção da telemetria `APP_HEALTH`; não altera dados de negócio.
9. Depois das migrações 007/008, implante o Worker compatível antes de instalar o APK Android 1.0.
10. **Antes de implantar o Worker com código de acesso**, aplique `012_access_codes.sql`. Ela cria `codigos_acesso`, adiciona `pausas_cafe.codigo_acesso_id` e `carencia_segundos`, e **apaga** `templates_faciais`, `verificacoes_faciais` e `autorizacoes`. A migração é destrutiva por desenho: o reconhecimento facial deixou de existir e o dado biométrico não fica dormente no banco. **Faça backup antes.** Não aplique a 012 mantendo o Worker antigo em produção — as rotas biométricas passariam a falhar em tabelas inexistentes.
11. Numa instalação nova, as migrações 005 e 006 podem ser puladas: a 012 desfaz o que elas criam.
12. **Antes de implantar o Worker com código por período e QR**, aplique `014_codigo_por_periodo_e_qr.sql`. Ela adiciona `codigos_acesso.periodo` e `dia_operacional` (com o índice único que passa a permitir um código vivo por pessoa **por período e por dia**, em vez de um só por pessoa) e `dispositivos.qr_habilitado`, que nasce **falso** — nenhum aparelho aceita leitura por câmara até que um Administrador ou Supervisor a libere. A migração é aditiva e não apaga nada. Aplicá-la mantendo o Worker antigo é seguro: as colunas novas ficam nulas/falsas e o código anterior não as lê. O caminho inverso não: um Worker novo sobre o banco sem a 014 falha ao emitir códigos.
13. Execute `npm run auth:bootstrap` uma única vez para criar o primeiro administrador em uma instalação nova.

Depois disso, novos supervisores devem ser criados pelo recurso Admin do Better Auth com o papel `user`. Dentro do Ponto Café, esse papel é interpretado como `SUPERVISOR`.

O aplicativo Android nunca recebe a `DATABASE_URL`, nenhum PIN de desbloqueio é salvo em texto puro no PostgreSQL e o token de ativação permanece persistido normalmente apenas como hash; a única cópia reversível usada para replay idempotente de cadastro de dispositivo é cifrada com AES-256-GCM e possui TTL curto.

Na operação do Ponto, `operacoes_ponto_idempotentes` armazena somente a identidade UUID da operação, os vínculos de dispositivo/colaborador, o tipo de mutação, o `pause_id` e a resposta JSON já confirmada pelo servidor. Códigos de acesso, PINs, senhas e tokens de sessão **não** são armazenados nessa tabela.

A manutenção diária da 1.0 remove entradas técnicas antigas conforme configuração: por padrão 30 dias para `operacoes_ponto_idempotentes`, 30 dias para auditorias `APP_HEALTH` e 90 dias para códigos de acesso já esgotados — um código com saída registrada e sem retorno nunca é removido. Registros de pausa e demais auditorias de negócio não são removidos por essa limpeza técnica.
