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
3. O motor facial local gera um embedding.
4. A API executa identificação 1:N entre os colaboradores ativos cadastrados.
5. A API só retorna um nome quando a melhor correspondência ultrapassa o limiar mínimo e não é ambígua em relação à segunda melhor correspondência.
6. A aplicação mostra o nome identificado e pergunta **“É você?”**.
7. Ao confirmar:
   - sem pausa aberta: registra o início;
   - com pausa aberta: registra o retorno/finalização.
8. No início, o comprovante rápido mostra **hora registrada, horário máximo de retorno e 15:00 disponíveis**.
9. No retorno, mostra **hora de retorno, tempo utilizado e se ficou dentro do limite**.
10. O comprovante fica visível por aproximadamente 5 segundos e a aplicação volta automaticamente para a câmera.

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

1. O Administrador cadastra os dados do colaborador.
2. A câmera abre para o cadastro facial.
3. A prova de vida por piscada é executada.
4. O motor local gera o embedding.
5. A API cifra e salva somente o template/embedding, não a foto bruta.
6. O colaborador passa a participar da identificação 1:N.

## Primeiro acesso administrativo
1. Configure no backend `FIRST_ADMIN_SETUP_KEY` com uma chave longa e aleatória.
2. Abra **Administrador** no Android.
3. Enquanto ainda não existir nenhuma conta, a tela de configuração inicial será exibida.
4. Informe nome, e-mail, senha e a chave de instalação.
5. Depois que o primeiro Administrador é criado, esse fluxo inicial deixa de aceitar novos cadastros.
6. Faça login com a conta criada.

## Arquitetura
- Android: Kotlin + Jetpack Compose
- Câmera: CameraX
- Detecção facial e sinais de piscada: ML Kit
- Inferência do embedding: LiteRT em Google Play services
- Reconhecimento: identificação 1:N no backend
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
- Better Auth gerencia credenciais e sessões de Administrador/Supervisor.
- As sessões dos dois perfis usam armazenamentos separados e cifrados no Android Keystore.
- O token do dispositivo também é armazenado de forma protegida.
- O registro público de usuários permanece desabilitado.
- Códigos temporários são persistidos apenas como hashes HMAC.
- Dados biométricos são armazenados como embeddings cifrados, não como fotos brutas.
- A API usa um token facial curto e de uso único antes de registrar início ou retorno.
- A identificação 1:N rejeita correspondências abaixo do limiar ou muito próximas da segunda melhor opção.
- Acesso administrativo e alterações sensíveis são auditáveis.

## Motor facial
O runtime LiteRT e o `LiteRtFaceEmbeddingEngine` estão implementados. O APK espera `app/src/main/assets/facenet.tflite`, com entrada RGB 160x160 e saída de 128 dimensões, conforme documentado em `app/src/main/assets/README.md`.

O binário do modelo ainda não é versionado no repositório porque os pesos precisam ter licença e origem adequadas ao uso empresarial. Antes da produção também é obrigatório calibrar `FACE_MATCH_THRESHOLD` e `FACE_IDENTIFICATION_MARGIN` com amostras do ambiente real.

## Status
Estão implementados na branch de desenvolvimento: câmera-primeiro, identificação 1:N no backend, confirmação de identidade, início/retorno automático, comprovante rápido, Supervisor somente leitura, gestão de contas, gestão de colaboradores, cadastro facial, autorização fora do horário e configuração das regras de café.

Ainda faltam: empacotar um modelo facial aprovado, validar o build completo, configurar a URL real da API e gerar a APK final instalável.
