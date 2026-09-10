# Ponto Café 1.0 — checklist de liberação

Este documento define o gate de produção da versão `1.0.0`. A presença do número de versão no código **não significa que a Release já foi aprovada**. A 1.0 só é considerada estável depois que todos os itens obrigatórios abaixo tiverem evidência executada.

## 1. Dependências e banco

- [ ] PR de Integridade Operacional 0.15 revisado e incorporado na sequência correta.
- [ ] `database/007_ponto_operation_idempotency.sql` aplicado antes do Worker compatível.
- [ ] `database/008_release_readiness_indexes.sql` aplicado depois da 007 e antes do Worker 1.0.
- [ ] Confirmar que `operacoes_ponto_idempotentes` aceita `REGISTRO_RAPIDO`, `INICIAR` e `FINALIZAR`.
- [ ] Confirmar índices de retenção exactly-once e `APP_HEALTH` criados pela 008.
- [ ] Nenhuma migração pendente na base usada pelo ambiente de produção.
- [ ] Backup imediatamente anterior ao rollout concluído e identificável.

## 2. Gate automatizado

Executar no commit candidato:

```bash
npm install --workspace backend --no-audit --no-fund
npm --workspace backend run validate
npm run release:check
```

Resultado obrigatório:

- [ ] testes backend aprovados;
- [ ] TypeScript sem erro;
- [ ] contrato `release:check` aprovado;
- [ ] workflow de CI aprovado;
- [ ] `:app:testReleaseUnitTest` aprovado;
- [ ] `:app:assembleRelease` aprovado;
- [ ] APK não contém nenhum modelo facial nem a permissão de câmera.

## 3. Identidade da Release Android

A candidata 1.0 deve manter:

- `versionName = 1.1.0`;
- `versionCode = 110`;
- `compileSdk = 36`;
- `targetSdk = 36`;
- Java 17;
- R8/minify ativado;
- `shrinkResources` ativado;
- APK assinado pelo certificado oficial já utilizado pelo Ponto Café.

Verificações manuais obrigatórias:

- [ ] SHA-256 do APK registrado no relatório de liberação;
- [ ] SHA-256 do certificado de assinatura confere com o certificado oficial;
- [ ] instalação como atualização da versão anterior funciona sem limpar os dados locais;
- [ ] downgrade acidental não é aceito como procedimento operacional.

## 4. Código de acesso

Contrato que não muda sem decisão explícita:

- 6 caracteres, alfabeto Crockford Base32 (sem I, L, O e U);
- preso a um colaborador desde a emissão;
- uma saída e um retorno, nesta ordem;
- a validade governa só a saída — o retorno nunca expira;
- um código vivo por pessoa de cada vez;
- tolerância de 1 minuto antes de o limite começar a contar, gravada por pausa.

Testes físicos obrigatórios no APK Release assinado:

- [ ] código emitido para A é recusado quando B o digita;
- [ ] o mesmo código abre a pausa e depois a fecha;
- [ ] uma terceira apresentação do mesmo código é recusada;
- [ ] código expirado é recusado na saída;
- [ ] pessoa que já saiu consegue voltar mesmo depois de o prazo de saída passar;
- [ ] emitir código para quem está em pausa é recusado;
- [ ] 8 tentativas erradas bloqueiam temporariamente aquele colaborador;
- [ ] o comprovante de saída mostra registro, início da contagem e prazo de retorno;
- [ ] retorno aos 15 min 30 s **não** é excesso; aos 16 min 30 s é.

## 5. Ponto e integridade exactly-once

Executar com dados controlados:

- [ ] INÍCIO normal cria exatamente uma pausa;
- [ ] RETORNO normal finaliza exatamente a pausa aberta;
- [ ] retry do mesmo UUID de INÍCIO devolve o mesmo resultado;
- [ ] retry do mesmo UUID de RETORNO devolve o mesmo resultado;
- [ ] duas requisições concorrentes com o mesmo UUID não criam duas mutações;
- [ ] UUID reutilizado para outro dispositivo/colaborador/operação retorna conflito;
- [ ] `COMMIT -> perda da resposta HTTP -> reconciliação` retorna o resultado original;
- [ ] transição online -> offline usa o mesmo UUID e não inverte INÍCIO/RETORNO;
- [ ] reiniciar/encerrar o app durante uma operação incerta não perde a identidade da operação;
- [ ] snapshot local é persistido antes de liberar o UUID.

## 6. Regras operacionais

Validar sem modificar a regra de negócio existente:

- [ ] pausa da manhã;
- [ ] pausa da tarde;
- [ ] retorno dentro do limite;
- [ ] retorno acima do limite;
- [ ] mesma pausa já utilizada;
- [ ] duas pausas do dia consumidas (2/2);
- [ ] fora do horário sem autorização;
- [ ] autorização válida de Supervisor;
- [ ] autorização expirada/usada;
- [ ] operação offline permitida dentro da janela configurada;
- [ ] sincronização posterior dos eventos offline.

## 7. Administração e Supervisor

- [ ] login Admin;
- [ ] login Supervisor;
- [ ] sessões salvas/protegidas;
- [ ] bloqueio/desbloqueio da área protegida;
- [ ] PIN individual de dispositivo;
- [ ] rotação de token revoga o token anterior;
- [ ] desativação de dispositivo impede novas batidas;
- [ ] gestão de colaboradores;
- [ ] emissão e cancelamento de código;
- [ ] relatórios PDF e CSV;
- [ ] auditoria de ações administrativas.

## 8. Saúde do sistema e frota

Na tela **Saúde do sistema**:

- [ ] banco aparece operacional e com latência plausível;
- [ ] contadores de operação correspondem ao banco;
- [ ] métricas das últimas 24 h são exibidas;
- [ ] operações protegidas representam operações idempotentes, não são rotuladas como "duplicidades";
- [ ] dispositivo com versão antiga aparece como desatualizado;
- [ ] dispositivo sem telemetria recente é sinalizado sem bloquear o Ponto;
- [ ] crash/travamento recente produz alerta de saúde;
- [ ] telemetria não contém PIN, senha, token, foto nem embedding;
- [ ] `PONTO_OPERATION_RETENTION_DAYS` está conscientemente configurado (padrão 30 dias);
- [ ] `DEVICE_HEALTH_RETENTION_DAYS` está conscientemente configurado (padrão 30 dias);
- [ ] cron de manutenção remove apenas diários técnicos antigos e não apaga pausas/auditoria de negócio.

## 9. Política de atualização

- [ ] `APP_LATEST_ANDROID_VERSION` configurada para a Release liberada;
- [ ] `APP_MIN_ANDROID_VERSION` definida conscientemente de acordo com compatibilidade do Worker;
- [ ] `backend/wrangler.jsonc` usa a mesma política da Release, sem defaults antigos sobrescrevendo o backend;
- [ ] cliente mostra atualização disponível quando a versão instalada é menor;
- [ ] cliente sinaliza atualização obrigatória quando fica abaixo da mínima;
- [ ] procedimento de distribuição do APK oficial identifica versão, SHA-256 e certificado.

A 1.0 não depende de instalação silenciosa. Em aparelhos Android comuns, a instalação continua respeitando as proteções do sistema operacional.

## 10. Backup e recuperação

Seguir `docs/DISASTER_RECOVERY.md`.

- [ ] backup válido criado;
- [ ] restauração executada em banco separado, nunca sobre produção;
- [ ] tabelas críticas verificadas após restore;
- [ ] contagens básicas comparadas com a origem;
- [ ] tempo real de recuperação registrado;
- [ ] responsável pelo procedimento conhece a localização segura do backup e das credenciais.

## 11. Privacidade e segurança

Seguir `docs/PRIVACIDADE_DADOS.md`.

- [ ] nenhuma credencial versionada no Git;
- [ ] logs não expõem senha, token ou chave de banco;
- [ ] telemetria de saúde não expõe código de acesso;
- [ ] retenção de códigos testada, e um código com saída sem retorno sobrevive à limpeza;
- [ ] nenhuma rota de dispositivo devolve código em claro;
- [ ] funções Admin/Supervisor respeitam autorização do backend;
- [ ] dispositivo revogado perde acesso;
- [ ] exportações e relatórios não concedem privilégios novos.

## 12. Rollout e rollback

Antes do rollout:

- [ ] congelar novas features;
- [ ] registrar commit, APK SHA-256 e certificado;
- [ ] aplicar migrações 007 e 008 antes do Worker 1.0;
- [ ] testar um dispositivo piloto antes de atualizar os demais.

Se um problema crítico for encontrado:

1. interromper a distribuição do APK;
2. preservar logs/request IDs e evidências;
3. não reverter migração destrutivamente durante operação ativa;
4. corrigir Worker/cliente conforme compatibilidade documentada;
5. restaurar banco somente quando houver evidência de corrupção/perda e usando o procedimento de disaster recovery;
6. emitir uma nova versão assinada em vez de reutilizar um APK defeituoso.

## Critério final

`1.0.0` pode ser chamada de **Release estável** somente quando todos os itens obrigatórios acima tiverem evidência real. Até lá, a branch/tag deve ser tratada como **Release Candidate**.
