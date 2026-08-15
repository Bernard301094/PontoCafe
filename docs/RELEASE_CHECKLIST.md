# Ponto Café — Checklist de Release

Use este checklist antes de fazer merge em `main` ou publicar uma APK estável.

## 1. Fonte
- [ ] Branch atualizada com `origin` sem merge acidental.
- [ ] `git status` limpo ou alterações locais revisadas.
- [ ] Versão Android (`versionName`/`versionCode`) revisada.
- [ ] `APP_LATEST_ANDROID_VERSION` igual à versão que será publicada.
- [ ] Nenhum segredo, `.jks`, senha, token ou `local.properties` versionado.

## 2. Regra operacional
- [ ] Manhã: janela configurada corretamente.
- [ ] Tarde: janela configurada corretamente.
- [ ] **Tempo padrão confirmado: 15:00 = 900 segundos.**
- [ ] Exceção fora de horário funciona com código temporário.
- [ ] Código temporário só pode ser consumido uma vez.

## 3. Backend
Executar em `backend`:

```bash
npm install
npm run validate
npx wrangler deploy --dry-run
```

Confirmar:
- [ ] Testes backend PASS.
- [ ] TypeScript PASS.
- [ ] Dry-run do Worker PASS.
- [ ] Nenhuma variável secreta aparece no log.

## 4. Android
Executar na raiz:

```bash
gradle --no-daemon :app:testDebugUnitTest
gradle --no-daemon :app:assembleDebugAndroidTest
gradle --no-daemon :app:assembleRelease
```

Confirmar:
- [ ] Unit tests PASS.
- [ ] Testes Compose compilam.
- [ ] Release APK compila.
- [ ] APK usa a URL de produção correta.

## 5. Smoke test no Galaxy A55
- [ ] Ativação de dispositivo com código de 10 caracteres.
- [ ] PIN individual do dispositivo.
- [ ] Login Admin.
- [ ] Login Supervisor.
- [ ] Minimizar/reabrir Admin: exige biometria/PIN e restaura a tela.
- [ ] Criar Supervisor.
- [ ] Criar dispositivo e gerar token.
- [ ] Editar nome/setor/turno de colaborador.
- [ ] Cadastro de rosto em cinco amostras.
- [ ] Reconhecimento de saída.
- [ ] Reconhecimento de retorno.
- [ ] Confirmação de limite 15:00.
- [ ] Operação offline válida.
- [ ] Sincronização posterior do evento offline.
- [ ] Centro de sincronização mostra pendências e erros.
- [ ] Histórico individual abre corretamente.
- [ ] Importação CSV revisada com arquivo pequeno de teste em ambiente seguro.
- [ ] Calibração biométrica funciona em luz normal e condição difícil.
- [ ] Diagnóstico do sistema retorna Request ID e latência.
- [ ] Modo terminal não impede acesso administrativo autorizado.

## 6. Publicação
Ordem obrigatória quando houver mudança de API e APK:
1. backend validado;
2. deploy do Worker;
3. verificar `/health` e `/app-status`;
4. compilar/assinar APK;
5. instalar no aparelho de teste;
6. executar smoke test;
7. só então fazer merge em `main` e criar tag.

## 7. Pós-release
- [ ] Verificar erros com Request ID.
- [ ] Verificar diagnóstico local de crash/travamento.
- [ ] Confirmar Cloudflare Cron de retenção biométrica.
- [ ] Confirmar que não há fila offline inesperada.
- [ ] Registrar alterações no `CHANGELOG.md`.
