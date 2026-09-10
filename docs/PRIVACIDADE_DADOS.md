# Ponto Café — privacidade e governança de dados

Este documento registra os controles técnicos esperados para o Ponto Café. Ele complementa, mas não substitui, a análise jurídica e as políticas internas aplicáveis.

## O que mudou, e por quê importa aqui

Até a versão anterior o Ponto identificava a pessoa pelo rosto: capturava a face na câmera do quiosque, derivava um *embedding* e o comparava contra um catálogo de templates cifrados. Todo este documento existia para governar esse dado.

**Esse dado deixou de existir.** A liberação da pausa passou a ser um código de 6 caracteres emitido pelo Supervisor, e a migração `012_access_codes.sql` apaga `templates_faciais` e `verificacoes_faciais`. O APK não declara mais `android.permission.CAMERA`, não empacota modelo facial e não depende de CameraX, ML Kit ou TFLite.

A consequência para privacidade é direta e vale registrar: o sistema deixou de tratar dado biométrico — a categoria mais sensível que ele manipulava — e passou a tratar um passe operacional de curta duração. O que se ganha em risco residual, perde-se em prova de identidade: um código pode ser passado adiante, um rosto não. Essa troca foi deliberada.

## Princípios técnicos

1. **Finalidade definida:** o código de acesso existe para autorizar uma pausa de café e registrar a saída e o retorno correspondentes.
2. **Minimização:** armazenar e transmitir somente o necessário para essa finalidade.
3. **Menor privilégio:** emitir e cancelar códigos exige sessão autenticada de Admin ou Supervisor; o quiosque nunca lê um código, apenas o compara.
4. **Retenção controlada:** códigos esgotados são removidos após a política configurada (padrão 90 dias). Um código com saída registrada e sem retorno **nunca** é removido — é a única coisa que ainda permite fechar aquela pausa.
5. **Auditabilidade:** emissão, cancelamento e tentativas inválidas deixam trilha de auditoria nominal.

## O código de acesso

- Alfabeto Crockford Base32 (`0123456789ABCDEFGHJKMNPQRSTVWXYZ`), 6 caracteres — cerca de 1,07 mil milhões de combinações.
- Preso a um colaborador desde a emissão. Um código correto apresentado com o nome errado é recusado.
- Vale **uma** saída e **um** retorno. Depois disso está esgotado.
- A janela de validade (`ACCESS_CODE_TTL_SECONDS`, padrão **2 min**) governa apenas a **saída**. Curta de propósito: um passe que sobrevive meia hora vira um passe transferível. Uma vez usado para sair, o código continua válido para o retorno sem prazo: negar o retorno deixaria a pausa aberta para sempre e obrigaria a um fecho manual.
- Emissão bloqueada enquanto a pessoa está em pausa — dois códigos vivos deixariam em aberto qual deles fecha a jornada.

### Sobre guardar o código em texto puro

`codigos_acesso.codigo` é gravado em claro, e isso é uma decisão, não um descuido. O código não é credencial de conta: é um passe operacional de curta duração, preso a uma pessoa, que o Supervisor precisa poder reler para ditar de novo a quem esqueceu. Guardá-lo como hash tornaria a tela de códigos ativos inútil sem ganho proporcional — quem tiver acesso de leitura ao banco já tem acesso a toda a jornada de todos.

Só rotas autenticadas de Admin/Supervisor leem essa coluna. Nenhuma rota de dispositivo devolve o código; o quiosque envia o que a pessoa digitou e o servidor compara em tempo constante (`secureCodeEquals`).

### Força bruta

O espaço de 32^6 torna adivinhação cega irrelevante. O que o limite de tentativas (`ACCESS_CODE_MAX_ATTEMPTS`, padrão 8 numa janela de 5 min por colaborador) trava é o cenário realista: alguém testando, em minutos, os poucos códigos que viu de relance no papel do Supervisor. Cada tentativa recusada é registada em auditoria com dispositivo e motivo.

## A tolerância de 1 minuto

`pausas_cafe.carencia_segundos` guarda, por linha, quantos segundos correm antes de o limite começar a contar. Com o padrão de 15 min de café e 60 s de tolerância, a pessoa fica de facto 16 minutos fora, e o relatório mostra as duas leituras separadas: `duracao_segundos` (tempo total fora) e `tempo_contado_segundos` (o que se compara ao limite).

O valor fica na linha, e não só na configuração, para que um relatório de seis meses atrás seja lido com a tolerância que realmente valeu naquele dia.

## Journal de integridade

O `PontoOperationJournal` existe para recuperar uma mutação de Ponto com resultado de rede incerto. Ele persiste:

- um UUID de operação;
- o identificador do colaborador;
- um SHA-256 do código digitado — nunca o código em claro;
- a marca de tempo e o estado de incerteza.

Quem ler este diário encontra a prova de que a mesma tentativa se repetiu, e não o passe para sair.

## Fila offline

Sem rede, o quiosque não tem como validar um código — só o servidor conhece os códigos vivos. Ele aceita o registo, guarda o código digitado junto com a hora real do quiosque, e deixa a validação para a sincronização.

A consequência aceite é que um código errado digitado offline só é recusado mais tarde. O evento fica com estado `ERRO` e visível na central de sincronismo, em vez de silenciosamente virar uma pausa que ninguém autorizou.

## Telemetria

A telemetria técnica (`APP_HEALTH`, `DEVICE_HEARTBEAT`) continua limitada a versão do app, modelo do aparelho, versão do Android e contadores de crash/stall. Nunca inclui código de acesso, PIN, senha ou token.

## Retenção configurável

| Variável | Padrão | O que remove |
| --- | --- | --- |
| `ACCESS_CODE_RETENTION_DAYS` | 90 | Códigos cancelados, já usados no retorno, ou expirados sem uso. Também as tentativas inválidas registadas em auditoria. |
| `PONTO_OPERATION_RETENTION_DAYS` | 30 | Diário idempotente de operações do Ponto. |
| `DEVICE_HEALTH_RETENTION_DAYS` | 30 | Telemetria técnica `APP_HEALTH` / `DEVICE_HEARTBEAT`. |

A limpeza corre no cron diário do Worker (`scheduled`), nunca no caminho de uma requisição de Ponto.
