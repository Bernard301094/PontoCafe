# Ponto Café

Aplicativo Android para controle de pausas de café com validação facial, autorização de supervisor e relatórios.

## Regras atuais
- Manhã: 08:00–10:00
- Tarde: 15:00–17:00
- Limite por período: 15 minutos
- O saldo não acumula entre períodos
- Fora do horário exige autorização temporária de supervisor

## Arquitetura
- Android: Kotlin + Jetpack Compose
- Câmera: CameraX
- Detecção facial: ML Kit
- Reconhecimento 1:1: motor de embedding separado da detecção facial
- Banco atual: Neon PostgreSQL
- Persistência: PostgreSQL padrão, sem dependência de APIs proprietárias do provedor
- Backend/API: Hono + Better Auth + PostgreSQL
- Idioma da interface: pt-BR

## Primeiro acesso administrativo
1. Configure no backend `FIRST_ADMIN_SETUP_KEY` com uma chave longa e aleatória.
2. Abra **Área administrativa** no Android.
3. Enquanto ainda não existir nenhuma conta, a tela de configuração inicial será exibida.
4. Informe nome, e-mail, senha e a chave de instalação.
5. Depois que o primeiro ADMIN é criado, esse endpoint inicial deixa de aceitar novos cadastros.
6. Faça login com a conta criada.

## Painel do Administrador
O ADMIN pode:
- cadastrar contas ADMIN ou SUPERVISOR;
- listar contas e seus status;
- desativar/reativar contas;
- alterar o perfil entre ADMIN e SUPERVISOR;
- redefinir senha e encerrar as sessões da conta;
- gerar o token de ativação de um dispositivo/quiosque.

Proteções adicionais impedem desativar a própria conta e impedem remover/desativar o último administrador ativo.

## Portabilidade
Neon é apenas o provedor PostgreSQL atual. O Android não acessa a base diretamente e não contém credenciais do banco. A API utiliza uma `DATABASE_URL`, permitindo migrar futuramente para outro PostgreSQL sem reescrever o aplicativo.

Veja `docs/ARQUITETURA_PORTAVEL.md`.

## Segurança
- O app não contém credenciais PostgreSQL.
- A hora oficial e as regras de pausa são validadas no servidor/banco.
- Better Auth gerencia credenciais e sessões de ADMIN/SUPERVISOR.
- A sessão administrativa e o token do dispositivo são armazenados cifrados com Android Keystore.
- O registro público de usuários permanece desabilitado.
- Códigos temporários são persistidos apenas como hashes.
- Dados biométricos são armazenados como templates/embeddings cifrados, não como fotos brutas.
- PDFs são gerados sob demanda e não ocupam espaço no banco por padrão.
- Acesso administrativo e alterações sensíveis são auditáveis.

## Status
Backend e fluxo administrativo do MVP em desenvolvimento. O motor de reconhecimento facial 1:1 ainda precisa ser definido e validado antes de liberar o fluxo biométrico completo.
