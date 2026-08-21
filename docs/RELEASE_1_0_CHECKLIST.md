# Ponto Café 1.0 — checklist de liberação

Este documento define o gate de produção da versão `1.0.0`. A presença do número de versão no código **não significa que a Release já foi aprovada**. A 1.0 só é considerada estável depois que todos os itens obrigatórios abaixo tiverem evidência executada.

## 1. Dependências e banco

- [ ] PR de Integridade Operacional 0.15 revisado e incorporado na sequência correta.
- [ ] `database/007_ponto_operation_idempotency.sql` aplicado antes do Worker compatível.
- [ ] Confirmar que `operacoes_ponto_idempotentes` aceita `REGISTRO_RAPIDO`, `INICIAR` e `FINALIZAR`.
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
- [ ] FaceNet dentro do APK corresponde ao blob Git fixado pelo projeto.

## 3. Identidade da Release Android

A candidata 1.0 deve manter:

- `versionName = 1.0.0`;
- `versionCode = 100`;
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

## 4. Compatibilidade biométrica

Não alterar na 1.0 sem uma calibração dedicada:

- FaceNet `facenet-128d-160-v1`;
- 160×160;
- embedding 128D;
- CPU/XNNPACK, 2 threads;
- crop canônico existente;
- prewhitening existente;
- normalização L2;
- threshold de reconhecimento `0.72`;
- margem de identificação `0.06`.

Testes físicos obrigatórios no APK Release assinado:

- [ ] colaborador já cadastrado é reconhecido sem recadastro;
- [ ] pessoa desconhecida não é aceita;
- [ ] liveness por piscar/movimento continua obrigatório;
- [ ] múltiplos rostos não geram batida indevida;
- [ ] baixa confiança não é convertida em aceite;
- [ ] calibração/diagnóstico continua exibindo score e margem sem persistir foto de teste.

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
- [ ] cadastro/atualização biométrica;
- [ ] avatar continua separado da biometria;
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
- [ ] telemetria não contém PIN, senha, token, foto nem embedding.

## 9. Política de atualização

- [ ] `APP_LATEST_ANDROID_VERSION` configurada para a Release liberada;
- [ ] `APP_MIN_ANDROID_VERSION` definida conscientemente de acordo com compatibilidade do Worker;
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

Seguir `docs/PRIVACIDADE_BIOMETRICA.md`.

- [ ] nenhuma credencial versionada no Git;
- [ ] logs não expõem senha, token ou chave de banco;
- [ ] telemetria de saúde não expõe biometria;
- [ ] exclusão/retensão biométrica testada;
- [ ] funções Admin/Supervisor respeitam autorização do backend;
- [ ] dispositivo revogado perde acesso;
- [ ] exportações e relatórios não concedem privilégios novos.

## 12. Rollout e rollback

Antes do rollout:

- [ ] congelar novas features;
- [ ] registrar commit, APK SHA-256 e certificado;
- [ ] aplicar migrações antes do Worker quando exigido;
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
