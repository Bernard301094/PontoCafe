# Ponto Café

Aplicativo Android em pt-BR para controle de pausas de café com identificação facial, operação offline, perfis de acesso e rastreabilidade administrativa.

## Regras atuais
- Manhã: **08:00–10:00**
- Tarde: **15:00–17:00**
- Limite padrão por período: **15 minutos = 900 segundos**
- O saldo não acumula entre períodos
- Fora do horário exige autorização temporária gerada por **Administrador ou Supervisor**
- A pausa não é finalizada automaticamente ao atingir 15 minutos; o excesso é registrado

O backend e o Android trabalham internamente em segundos. A interface administrativa permite precisão em minutos + segundos, mas o padrão operacional atual permanece **15:00**.

## Fluxo principal do Ponto
A câmera é a tela inicial do terminal. O colaborador não procura nem seleciona o próprio nome.

1. O colaborador olha para a câmera.
2. A aplicação executa detecção facial e desafio de prova de vida.
3. A qualidade da imagem é verificada para evitar amostras extremamente escuras, superexpostas, sem contraste ou muito desfocadas.
4. O motor FaceNet/LiteRT local gera um embedding.
5. O Android compara esse embedding com o catálogo biométrico cifrado no próprio dispositivo (identificação 1:N local).
6. A aplicação só aceita um candidato quando a melhor correspondência ultrapassa o limiar e mantém margem segura sobre a segunda melhor.
7. O Android envia apenas o candidato encontrado + embedding atual para a API fazer confirmação 1:1 autoritativa.
8. A aplicação mostra o nome confirmado e pergunta **“É você?”**.
9. Ao confirmar:
   - sem pausa aberta: registra o início;
   - com pausa aberta: registra o retorno/finalização.
10. No início, o comprovante mostra hora registrada, retorno máximo e o limite vigente — atualmente **15:00**.
11. No retorno, mostra hora de retorno, tempo utilizado e eventual excesso.
12. O comprovante usa feedback háptico, fica visível por aproximadamente 5 segundos e volta automaticamente para a câmera.

Se a identificação não for suficientemente segura, nenhum nome é mostrado e a câmera solicita nova tentativa.

## Operação offline
- Regras e catálogo facial permanecem disponíveis localmente dentro da janela de segurança configurada.
- Eventos offline são cifrados com AES-GCM protegido pelo Android Keystore.
- Fotos não são armazenadas.
- O backend revalida a biometria durante a sincronização; o score informado pelo cliente não é tratado como autoritativo.
- O **Centro de Sincronização** mostra pendências, tentativas e motivos de falha.
- O snapshot offline utiliza cache em memória para evitar descriptografia/parsing repetitivos durante a operação normal.

## Perfis

### Supervisor
Pode:
- acompanhar pausas ativas e excesso em tempo real;
- consultar histórico e relatórios;
- cadastrar colaboradores;
- cadastrar, atualizar ou excluir a biometria facial conforme o fluxo permitido;
- gerar autorização temporária para pausa fora do horário;
- executar testes de calibração biométrica autorizados.

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
- executar política de retenção biométrica;
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
- estado e histórico da biometria.

Desativação em lote é bloqueada quando qualquer pessoa selecionada possui pausa aberta.

## Biometria e calibração
O cadastro do rosto usa o mesmo `FaceEmbeddingEngine` do Ponto e salva apenas embeddings cifrados.

A área **Biometria** permite medir a qualidade do sistema com amostras reais:
- score da pessoa correta;
- outro template mais próximo;
- margem entre os dois;
- Top-1 accuracy acumulada;
- False Reject Rate (FRR);
- False Accept Rate (FAR) sobre comparações impostoras realizadas durante a calibração.

Essas taxas refletem apenas as amostras de calibração executadas no ambiente e não substituem certificação biométrica formal. Os thresholds devem ser ajustados apenas depois de quantidade e diversidade suficientes de amostras reais.

## Governança biométrica
- Fotos brutas não são persistidas.
- Templates faciais são cifrados no PostgreSQL.
- Catálogo local é cifrado com Android Keystore.
- Cadastro, recadastro, teste e exclusão são auditáveis.
- A política padrão de retenção da 0.7 é **90 dias após a desativação do colaborador**.
- Um Cloudflare Cron Trigger executa a manutenção diariamente.
- Admin também pode executar a política manualmente para conferência.

## Dispositivos e modo terminal
A ativação do dispositivo usa um código curto de 10 caracteres exibido uma única vez. Depois da troca, o aparelho recebe uma credencial longa e o código curto deixa de ser válido.

O **Modo terminal** é configurável por aparelho:
- manter a tela ligada;
- usar Screen Pinning/Lock Task permitido a aplicativos comuns;
- tentar abrir após reinicialização;
- manter as áreas Admin/Supervisor protegidas.

A aplicação não contorna PIN, biometria, Samsung Knox ou políticas de segurança do Android. Funcionalidades de Device Owner exigiriam provisionamento corporativo específico e não são assumidas pelo projeto.

## Sessão e bloqueio local
- Sessões Admin/Supervisor permanecem cifradas no Android Keystore.
- Ao minimizar uma área restrita, a interface é bloqueada.
- Ao voltar, Android solicita biometria ou credencial do aparelho.
- A navegação administrativa é persistida sem guardar senha, PIN ou biometria.
- A 0.7 também restaura Historial, Biometria, Sincronização, Diagnóstico, Dispositivos e Modo terminal após recriação do processo.

## Diagnóstico e observabilidade
Cada requisição recebe um `X-Request-Id` no formato `PC-*`. Erros administrativos podem mostrar esse identificador para localizar o evento no Worker sem expor segredo.

A área **Diagnóstico** mostra:
- saúde e latência do banco;
- versão Android mínima/mais recente;
- número de colaboradores, dispositivos, pausas e sessões ativas;
- configuração segura de offline/biometria;
- crashes locais registrados;
- travamentos do thread principal acima de 5 segundos.

A telemetria local não registra PIN, senha, token, foto ou embedding.

## Arquitetura
- Android: Kotlin + Jetpack Compose + Material 3/Adaptive
- Câmera: CameraX
- Detecção facial: ML Kit
- Embedding: FaceNet + LiteRT local
- Identificação 1:N: local no Android
- Confirmação 1:1: backend
- Backend: Hono + Better Auth em Cloudflare Workers
- Banco: PostgreSQL em Neon
- Conectividade: Cloudflare Hyperdrive → Neon
- Relatórios: Vico + exportações existentes
- Feedback visual: Compose + Lottie pontual

O Android nunca acessa PostgreSQL diretamente e não contém credenciais da base.

## Testes e release
A 0.7 adiciona:
- testes Node do domínio do backend;
- `npm run validate` = testes + TypeScript;
- unit tests Android para a regra de **15:00** e importação CSV;
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

## Versão em desenvolvimento
A etapa atual é **Ponto Café 0.7.0 — Reliability + Operations** (`versionCode 7`).

Antes de merge em `main` e criação de tag, devem passar:
1. testes e typecheck do backend;
2. dry-run/deploy do Worker;
3. testes e build Android;
4. smoke test físico no Galaxy A55;
5. verificação de criação de Supervisor/dispositivo, bloqueio biométrico, ponto online/offline e sincronização.

Consulte também `CHANGELOG.md` e `docs/RELEASE_CHECKLIST.md`.
