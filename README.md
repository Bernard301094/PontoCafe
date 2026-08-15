# Ponto Café

Aplicativo Android em pt-BR para controle de pausas de café com identificação facial, perfis de acesso e histórico.

## Regras atuais
- Manhã: 08:00–10:00
- Tarde: 15:00–17:00
- Limite por período: 15 minutos
- O saldo não acumula entre períodos
- Fora do horário exige autorização temporária gerada por um Administrador
- A pausa não é finalizada automaticamente ao atingir 15 minutos; o excesso é registrado

## Fluxo principal do Ponto
A câmera é a tela inicial do quiosque. O colaborador não procura nem seleciona o próprio nome.

1. O colaborador olha para a câmera.
2. A aplicação executa detecção facial e prova de vida por piscada.
3. O motor FaceNet/LiteRT local gera um embedding.
4. O Android compara esse embedding com o catálogo biométrico cifrado no próprio dispositivo (identificação 1:N local).
5. A aplicação só aceita um candidato quando a melhor correspondência ultrapassa o limiar e tem margem segura sobre a segunda melhor.
6. O Android envia apenas o candidato encontrado + embedding atual para a API fazer uma confirmação 1:1 autoritativa.
7. A aplicação mostra o nome confirmado e pergunta **“É você?”**.
8. Ao confirmar:
   - sem pausa aberta: registra o início;
   - com pausa aberta: registra o retorno/finalização.
9. No início, o comprovante rápido mostra **hora registrada, horário máximo de retorno e 15:00 disponíveis**.
10. No retorno, mostra **hora de retorno, tempo utilizado e se ficou dentro do limite**.
11. O comprovante fica visível por aproximadamente 5 segundos e a aplicação volta automaticamente para a câmera.

Se a identificação não for suficientemente segura, nenhum nome é mostrado e a câmera solicita uma nova tentativa.

## Catálogo facial local
- A API disponibiliza apenas ao quiosque autenticado o catálogo compatível com o modelo facial instalado.
- O catálogo é transferido por HTTPS e salvo cifrado com AES-GCM protegido pelo Android Keystore.
- A versão do catálogo é calculada a partir dos colaboradores ativos e seus templates.
- O Android sincroniza no início, periodicamente e imediatamente ao voltar das áreas de Administrador ou Supervisor.
- Alterar/desativar um colaborador, remover sua biometria ou atualizar seu rosto invalida a versão anterior do catálogo.
- A busca 1:N não consome uma consulta ao banco para cada pessoa que olha para a câmera.

## Perfis

### Supervisor
Pode:
- ver quem está em pausa agora;
- ver horário de saída e tempo decorrido;
- identificar quem ultrapassou 15 minutos;
- consultar o histórico de pausas;
- cadastrar colaboradores;
- cadastrar ou atualizar o rosto de colaboradores;
- excluir somente o rosto de um colaborador;
- retirar um colaborador da lista ativa, preservando o histórico de pausas e auditoria.

O Supervisor **não pode** administrar contas, alterar regras de café, gerar tokens de dispositivo nem gerar autorizações fora do horário.

### Administrador
Perfil de gestão do sistema. Pode:
- cadastrar contas Administrador ou Supervisor;
- listar, desativar, reativar e excluir contas;
- alterar perfil;
- redefinir senha e encerrar sessões;
- cadastrar colaboradores;
- cadastrar ou atualizar o rosto do colaborador usando a câmera;
- configurar horários, duração e ativação dos períodos de café;
- gerar token de ativação do dispositivo/quiosque;
- gerar código temporário para pausa fora do horário.

Proteções adicionais impedem excluir/desativar a própria conta e impedem remover/desativar o último Administrador ativo.

## Cadastro facial
O cadastro do rosto usa exatamente o mesmo `FaceEmbeddingEngine` usado no Ponto:

1. Um Administrador ou Supervisor cadastra os dados do colaborador.
2. A câmera abre para o cadastro facial.
3. A prova de vida por piscada é executada.
4. O motor local gera o embedding.
5. A API cifra e salva somente o template/embedding, não a foto bruta.
6. Ao voltar ao Ponto, o catálogo local é sincronizado.

A remoção do rosto exclui apenas o template biométrico. A remoção de um colaborador é lógica (`ativo=false`) para preservar o histórico. Se houver uma pausa aberta, o sistema bloqueia ambas as remoções até o retorno ser registrado.

## Primeiro acesso administrativo
1. Configure no backend `FIRST_ADMIN_SETUP_KEY` com uma chave longa e aleatória.
2. Abra **Administrador** no Android.
3. Enquanto ainda não existir nenhuma conta, a tela de configuração inicial será exibida.
4. Informe nome, e-mail, senha e a chave de instalação.
5. Depois que o primeiro Administrador é criado, esse fluxo inicial deixa de aceitar novos cadastros.
6. Faça login com a conta criada.

## Arquitetura sem custo mensal obrigatório
- APK Android: Kotlin + Jetpack Compose
- Câmera: CameraX
- Detecção facial e piscada: ML Kit
- Embedding facial: FaceNet + LiteRT local
- Identificação 1:N: local no Android
- Confirmação 1:1: backend
- Backend/API: Hono + Better Auth em Cloudflare Workers
- Banco: Neon PostgreSQL Free
- Conectividade recomendada: Cloudflare Hyperdrive → Neon
- Persistência: PostgreSQL padrão
- Idioma da interface: pt-BR

A arquitetura foi escolhida para operar dentro das camadas gratuitas enquanto o volume permanecer dentro dos limites dos provedores.

## Cloudflare Workers
O backend contém:
- `backend/src/cloudflare.ts`: entrypoint do Worker;
- `backend/wrangler.jsonc`: configuração do Worker;
- scripts `dev:worker`, `deploy`, `deploy:dry` e `cf:types`.

Secrets nunca são versionados. Em Cloudflare devem ser cadastrados como secrets/bindings:
- `DATABASE_URL` (fallback direto) ou binding `HYPERDRIVE`;
- `BETTER_AUTH_SECRET`;
- `CODE_PEPPER`;
- `BIOMETRIC_MASTER_KEY`;
- `FIRST_ADMIN_SETUP_KEY`;
- `BETTER_AUTH_URL` após definir o endereço definitivo do Worker.

O entrypoint aceita `HYPERDRIVE.connectionString` quando o binding estiver configurado; caso contrário usa `DATABASE_URL`.

## Portabilidade
Neon é apenas o provedor PostgreSQL atual. O Android não acessa a base diretamente e não contém credenciais do banco. A API continua usando PostgreSQL padrão, permitindo migrar futuramente para outro provedor sem reescrever o aplicativo.

Veja `docs/ARQUITETURA_PORTAVEL.md`.

## Segurança
- O app não contém credenciais PostgreSQL.
- A hora oficial e as regras de pausa são validadas no servidor/banco.
- Better Auth gerencia credenciais e sessões de Administrador/Supervisor.
- As sessões dos dois perfis usam armazenamentos separados e cifrados no Android Keystore.
- O token do dispositivo também é armazenado de forma protegida.
- O catálogo biométrico local é cifrado com chave do Android Keystore.
- O registro público de usuários permanece desabilitado.
- Códigos temporários são persistidos apenas como hashes HMAC.
- Dados biométricos persistidos no PostgreSQL são embeddings cifrados, não fotos brutas.
- A confirmação 1:1 em servidor gera um token facial curto e de uso único antes de registrar início ou retorno.
- A identificação local rejeita correspondências abaixo do limiar ou muito próximas da segunda melhor opção.
- Inclusão, alteração e exclusão de colaboradores/biometrias são auditadas com o perfil e usuário responsável.

## Motor facial gratuito
O `LiteRtFaceEmbeddingEngine` usa entrada RGB 160x160 e saída de 128 dimensões.

O build Android executa automaticamente `prepareFaceModel` antes de `preBuild`:
1. baixa `facenet.tflite` de um commit fixo do projeto público de referência;
2. valida o Git blob SHA esperado;
3. falha o build se o conteúdo não corresponder;
4. empacota o modelo no APK sem versionar o binário de ~23 MB no repositório.

A proveniência está registrada em `app/src/main/assets/NOTICE_FACE_MODEL.txt`.

Antes de uso corporativo definitivo, o limiar e a margem facial ainda devem ser calibrados com amostras reais do dispositivo e do ambiente onde o quiosque será utilizado.

## Status
Implementado na branch de desenvolvimento:
- câmera como tela principal;
- FaceNet/LiteRT no Android;
- identificação 1:N local;
- catálogo biométrico cifrado e sincronizado;
- confirmação facial 1:1 no servidor;
- início/retorno automático;
- comprovante rápido;
- acompanhamento de pausas pelo Supervisor;
- gestão de colaboradores e biometria por Administrador e Supervisor;
- gestão de contas pelo Administrador;
- autorização fora do horário;
- configuração das regras de café;
- backend preparado para Cloudflare Workers.

Pendências de entrega: criar/configurar o Worker real na conta Cloudflare, cadastrar os secrets/binding Hyperdrive, validar build completo e gerar a APK instalável apontando para a URL final da API.
