# Ponto Café

Aplicativo Android em pt-BR para controle de pausas de café com identificação facial, perfis de acesso e histórico.

## Regras atuais
- Manhã: 08:00–10:00
- Tarde: 15:00–17:00
- Limite por período: 15 minutos
- O saldo não acumula entre períodos
- Fora do horário exige autorização temporária gerada por um ADMINISTRADOR
- A pausa não é finalizada automaticamente ao atingir 15 minutos; o excesso é registrado

## Fluxo principal do Ponto
A câmera é a tela inicial do quiosque. O colaborador não procura nem seleciona o próprio nome.

1. O colaborador olha para a câmera.
2. A aplicação executa detecção facial e prova de vida por piscada.
3. Um embedding facial é enviado à API para identificação 1:N entre colaboradores ativos.
4. A API só retorna um nome quando a melhor correspondência ultrapassa o limiar mínimo e não é ambígua em relação à segunda melhor correspondência.
5. A aplicação mostra o nome identificado e pergunta **“É você?”**.
6. Ao confirmar:
   - sem pausa aberta: registra o início;
   - com pausa aberta: registra o retorno/finalização.
7. No início, o comprovante rápido mostra **hora registrada, horário máximo de retorno e 15:00 disponíveis**.
8. No retorno, mostra **hora de retorno, tempo utilizado e se ficou dentro do limite**.
9. O comprovante fica visível por aproximadamente 5 segundos e a aplicação volta automaticamente para a câmera.

Se a identificação não for suficientemente segura, nenhum nome é mostrado e a câmera solicita uma nova tentativa.

## Perfis

### Supervisor
Perfil estritamente de leitura. Pode:
- ver quem está em pausa agora;
- ver horário de saída e tempo decorrido;
- identificar quem ultrapassou 15 minutos;
- consultar o histórico de pausas.

O Supervisor **não pode** cadastrar, excluir, editar, configurar ou gerar autorizações.

### Administrador
Perfil de gestão do sistema. Pode:
- cadastrar contas ADMIN ou SUPERVISOR;
- listar, desativar e reativar contas;
- alterar perfil;
- redefinir senha e encerrar sessões;
- gerar token de ativação do dispositivo/quiosque;
- gerar código temporário para pausa fora do horário;
- gerenciar colaboradores e seus dados biométricos pelas rotas administrativas.

Proteções adicionais impedem desativar a própria conta e impedem remover/desativar o último administrador ativo.

## Primeiro acesso administrativo
1. Configure no backend `FIRST_ADMIN_SETUP_KEY` com uma chave longa e aleatória.
2. Abra **Admin** no Android.
3. Enquanto ainda não existir nenhuma conta, a tela de configuração inicial será exibida.
4. Informe nome, e-mail, senha e a chave de instalação.
5. Depois que o primeiro ADMIN é criado, esse fluxo inicial deixa de aceitar novos cadastros.
6. Faça login com a conta criada.

## Arquitetura
- Android: Kotlin + Jetpack Compose
- Câmera: CameraX
- Detecção facial e sinais de piscada: ML Kit
- Reconhecimento: embedding facial separado da detecção, com identificação 1:N no backend
- Banco atual: Neon PostgreSQL
- Persistência: PostgreSQL padrão, sem dependência de APIs proprietárias do provedor
- Backend/API: Hono + Better Auth + PostgreSQL
- Idioma da interface: pt-BR

## Portabilidade
Neon é apenas o provedor PostgreSQL atual. O Android não acessa a base diretamente e não contém credenciais do banco. A API utiliza uma `DATABASE_URL`, permitindo migrar futuramente para outro PostgreSQL sem reescrever o aplicativo.

Veja `docs/ARQUITETURA_PORTAVEL.md`.

## Segurança
- O app não contém credenciais PostgreSQL.
- A hora oficial e as regras de pausa são validadas no servidor/banco.
- Better Auth gerencia credenciais e sessões de ADMIN/SUPERVISOR.
- A sessão de acesso e o token do dispositivo são armazenados cifrados com Android Keystore.
- O registro público de usuários permanece desabilitado.
- Códigos temporários são persistidos apenas como hashes HMAC.
- Dados biométricos são armazenados como embeddings cifrados, não como fotos brutas.
- A API usa um token facial curto e de uso único antes de registrar início ou retorno.
- A identificação 1:N rejeita correspondências abaixo do limiar ou muito próximas da segunda melhor opção.
- Acesso administrativo e alterações sensíveis são auditáveis.

## Motor facial
A infraestrutura do fluxo 1:N já está implementada, mas o artefato final do modelo de embeddings ainda precisa ser empacotado e calibrado antes do APK de produção. O código não deve incorporar pesos de terceiros sem licença e proveniência adequadamente verificadas.

## Status
Fluxo câmera-primeiro, recibo rápido, Supervisor somente leitura e painel administrativo estão implementados na branch de desenvolvimento. Ainda faltam validação de build, empacotamento/calibração do modelo facial e configuração da URL real da API antes da geração do APK final.
