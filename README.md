# Ponto Café

Aplicativo Android em pt-BR para controle de pausas de café com liberação por **código de acesso**, operação offline, perfis de acesso e rastreabilidade administrativa.

## Regras atuais
- Manhã: **08:00–10:00**
- Tarde: **15:00–17:00**
- Limite padrão por período: **15 minutos = 900 segundos**
- Tolerância antes de o limite começar a contar: **1 minuto** (`COFFEE_GRACE_SECONDS`)
- O saldo não acumula entre períodos
- Uma pausa por período, por dia
- A pausa não é finalizada automaticamente ao atingir o limite; o excesso é registrado

O horário deixou de ser um portão e passou a ser a etiqueta MANHÃ/TARDE dos relatórios: **um código válido libera o café a qualquer hora**, e a pausa fora da janela fica marcada como `fora_horario`.

O backend e o Android trabalham internamente em segundos. A interface administrativa permite precisão em minutos + segundos, mas o padrão operacional atual permanece **15:00**.

## Fluxo principal do Ponto

O Supervisor gera um código de **6 caracteres** para quem vai tomar café. A mesma pessoa usa esse código para **sair** e para **voltar**.

1. O Supervisor abre **Códigos de café**, encontra a pessoa e toca em **Gerar código**. O código aparece em letras grandes para ser ditado.
2. No quiosque, a pessoa procura e toca no **próprio nome**.
3. Digita os 6 caracteres num teclado que só oferece o alfabeto do código.
4. O servidor decide se aquilo é uma **saída** ou um **retorno** — o aparelho nunca escolhe.
5. Na saída, o comprovante mostra a hora registrada, **quando o tempo começa a contar** (1 minuto depois) e o horário-limite de retorno.
6. No retorno, mostra a hora, o tempo total fora, o tempo já descontado da tolerância e eventual excesso.
7. O comprovante fica visível por 12 segundos e volta sozinho para a lista de nomes.

### O que o código garante

- **Preso a uma pessoa.** Um código correto apresentado com o nome errado é recusado.
- **Uma saída e um retorno.** Depois disso está esgotado, e o esquema do banco impede qualquer outra combinação.
- **Expira só para sair.** A validade (15 min por padrão) governa a saída. Uma vez usado para sair, o código continua válido para o retorno sem prazo — negar o retorno deixaria a pausa aberta para sempre.
- **Um por pessoa de cada vez.** Emitir um novo cancela o pendente; emitir enquanto a pessoa está fora é recusado.
- **Tentativas erradas são contadas.** 8 recusas em 5 minutos bloqueiam temporariamente aquele colaborador no quiosque, e cada tentativa fica em auditoria.

## O minuto de tolerância

Quem registra a saída ainda está em frente ao quiosque, não em frente ao café. O percurso até lá não sai do tempo de pausa: o limite de 15 minutos só começa a contar 1 minuto depois do registro, e na prática a pessoa fica **16 minutos** fora.

A tolerância fica gravada **por pausa** (`pausas_cafe.carencia_segundos`), e não apenas na configuração, para que um relatório antigo seja lido com a tolerância que realmente valeu naquele dia. Os relatórios mostram as duas leituras: `duracao_segundos` (tempo total fora) e `tempo_contado_segundos` (o que se compara ao limite).

## Operação offline
- Regras do café permanecem disponíveis localmente dentro da janela de segurança configurada.
- Eventos offline são cifrados com AES-GCM protegido pelo Android Keystore.
- Sem rede o aparelho **não valida o código** — só o servidor conhece os códigos vivos. Ele aceita o registro, guarda o código digitado com a hora real do quiosque e deixa a validação para a sincronização.
- A consequência aceita: um código errado digitado offline só é recusado mais tarde, e aparece como falha nominal na central de sincronismo em vez de virar uma pausa que ninguém autorizou.
- O **Centro de Sincronização** mostra pendências, tentativas e motivos de falha.

## Perfis

### Supervisor
Pode:
- acompanhar pausas ativas e excesso em tempo real;
- emitir e cancelar códigos de café;
- consultar histórico e relatórios;
- cadastrar colaboradores;
- registrar saída/retorno manualmente, com motivo auditado.

O Supervisor não pode administrar contas de acesso, dispositivos, políticas de retenção ou regras globais do café.

### Administrador
Pode, além das operações de gestão:
- cadastrar contas Administrador ou Supervisor;
- listar, desativar, reativar e excluir contas;
- alterar perfil e redefinir senha;
- cadastrar e editar colaboradores;
- importar colaboradores por CSV;
- alterar setor, turno ou status em lote;
- consultar histórico individual;
- administrar dispositivos, PIN e códigos de ativação;
- configurar horários e duração em segundos;
- acompanhar auditoria, diagnóstico e sincronização;
- configurar o modo terminal deste aparelho.

Proteções impedem excluir/desativar a própria conta e impedem remover/desativar o último Administrador ativo.

## Pessoas e histórico
A área **Pessoas** reúne colaboradores e contas de acesso sem misturar seus papéis.

Para colaboradores, o Admin dispõe de:
- busca por nome/setor/turno;
- cadastro individual;
- importação CSV (`Nome;Setor;Turno`);
- seleção e edição em lote;
- histórico individual de pausas;
- média de duração;
- excessos;
- ocorrências fora do horário;
- histórico dos códigos emitidos (quem liberou, quando, e se chegou a ser usado).

Desativação em lote é bloqueada quando qualquer pessoa selecionada possui pausa aberta.

## Governança de dados
- O reconhecimento facial foi **removido**: a migração `012_access_codes.sql` apaga `templates_faciais` e `verificacoes_faciais`.
- O APK não declara `android.permission.CAMERA`, não empacota modelo facial e não depende de CameraX, ML Kit ou TFLite.
- O código de acesso é gravado em claro e lido apenas por rotas autenticadas de Admin/Supervisor; nenhuma rota de dispositivo o devolve. Ver a justificativa em `docs/PRIVACIDADE_DADOS.md`.
- Emissão, cancelamento e tentativas recusadas são auditáveis.
- Retenção padrão: **90 dias** para códigos esgotados. Um código com saída e sem retorno nunca é removido.
- Um Cloudflare Cron Trigger executa a manutenção diariamente.

## Dispositivos e modo terminal
A ativação do dispositivo usa um código curto de 10 caracteres exibido uma única vez. Depois da troca, o aparelho recebe uma credencial longa e o código curto deixa de ser válido.

O **Modo terminal** é configurável por aparelho:
- manter a tela ligada;
- usar Screen Pinning/Lock Task permitido a aplicativos comuns;
- tentar abrir após reinicialização;
- manter as áreas Admin/Supervisor protegidas.

A aplicação não contorna PIN, biometria do sistema, Samsung Knox ou políticas de segurança do Android. Funcionalidades de Device Owner exigiriam provisionamento corporativo específico e não são assumidas pelo projeto.

## Sessão e bloqueio local
- Sessões Admin/Supervisor permanecem cifradas no Android Keystore.
- Ao minimizar uma área restrita, a interface é bloqueada.
- Ao voltar, o Android solicita biometria do aparelho ou credencial do sistema.
- A navegação administrativa é persistida sem guardar senha, PIN ou credencial.

## Diagnóstico e observabilidade
Cada requisição recebe um `X-Request-Id` no formato `PC-*`. Erros administrativos podem mostrar esse identificador para localizar o evento no Worker sem expor segredo.

A área **Diagnóstico** mostra:
- saúde e latência do banco;
- versão Android mínima/mais recente;
- número de colaboradores, dispositivos, pausas e sessões ativas;
- códigos pendentes, em uso e tentativas recusadas nas últimas 24 h;
- configuração de offline, validade de código e tolerância;
- crashes locais registrados;
- travamentos do thread principal acima de 5 segundos.

A telemetria local não registra PIN, senha, token ou código de acesso.

## Arquitetura
- Android: Kotlin + Jetpack Compose + Material 3/Adaptive
- Voz do quiosque: sherpa-onnx (VITS/Piper pt-BR) empacotado no APK
- Backend: Hono + Better Auth em Cloudflare Workers
- Banco: PostgreSQL em Neon
- Conectividade: Cloudflare Hyperdrive → Neon
- Feedback visual: Compose + Lottie pontual

O Android nunca acessa PostgreSQL diretamente e não contém credenciais da base.

## Testes e release
- testes Node do domínio e dos contratos do backend;
- `npm run validate` = testes + TypeScript;
- `npm run release:check` = contrato de release 1.0;
- unit tests Android para a regra de **15:00**, o alfabeto do código e importação CSV;
- smoke test Compose;
- workflow de validação em PR/push para `main` e execução manual;
- checklist em `docs/RELEASE_CHECKLIST.md`;
- changelog em `CHANGELOG.md`.

O workflow depende da disponibilidade do GitHub Actions da conta. A existência do workflow não substitui a validação local.

### Backend
```bash
cd backend
npm install
npm run validate
npx wrangler deploy --dry-run
```

### Android
```bash
gradle --no-daemon :app:testDebugUnitTest
gradle --no-daemon :app:assembleDebugAndroidTest
gradle --no-daemon :app:assembleRelease
```

## Migração para o código de acesso

Antes de subir esta versão:

1. Aplicar `database/012_access_codes.sql`. **A migração é destrutiva**: apaga `templates_faciais`, `verificacoes_faciais` e `autorizacoes`. Faça backup antes.
2. Publicar o Worker (`npm run deploy:cloudflare`).
3. Distribuir o APK novo. Versões antigas do app deixam de funcionar: as rotas biométricas não existem mais.
4. Orientar os Supervisores: **Códigos de café** na área restrita substitui as antigas Autorizações e o cadastro de rostos.

Consulte também `CHANGELOG.md`, `docs/PRIVACIDADE_DADOS.md` e `docs/RELEASE_CHECKLIST.md`.
