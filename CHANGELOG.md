# Changelog

## 0.7.0 — Reliability + Operations

### Regra operacional
- Mantém o tempo padrão do café em **15 minutos (900 segundos)**.
- Editor de regras passa a trabalhar com segundos sem alterar o padrão atual.
- Testes automáticos impedem regressão silenciosa do valor padrão.

### Confiabilidade
- Request ID `PC-*` nas respostas do backend para rastrear erros sem expor segredos.
- `Server-Timing` e latência do banco no diagnóstico administrativo.
- Monitor local de crashes e travamentos prolongados do thread principal no Android.
- Testes backend, testes unitários Android e smoke test Compose.
- CI configurado para PRs e `main`, além de execução manual.
- Rascunhos de cadastro permanecem em memória ao minimizar/bloquear a área protegida e são restaurados após biometria/PIN, sem persistir senhas em disco ou Bundle.

### Pessoas
- Importação CSV com pré-visualização e validação.
- Edição em lote de setor, turno e desativação.
- Desativação em lote é bloqueada quando há pausa aberta.
- Histórico individual de pausas, média, excessos e registros fora do horário.
- Cadastro de colaborador preserva nome, setor e turno durante bloqueio/desbloqueio tanto no Admin quanto no Supervisor.

### Biometria
- Calibração por amostra com score correto, concorrente mais próximo e margem.
- Resumo de modelos e thresholds atuais.
- Validação leve de luminosidade, contraste e desfoque antes do FaceNet.
- Histórico de ações biométricas por colaborador.
- Política de retenção padrão de 90 dias para colaboradores já desativados.
- Manutenção automática diária via Cloudflare Cron Trigger.
- Cadastro facial agora bloqueia reutilização do mesmo rosto em outra pessoa comparando o embedding consolidado e, quando disponíveis, as cinco amostras individuais.
- Verificação de duplicidade inclui templates retidos de colaboradores desativados e é serializada no banco para impedir duas altas simultâneas do mesmo rosto.
- Limiar de proteção de cadastro foi endurecido para `0.78`, separado do limiar normal de reconhecimento.

### Offline
- Centro de sincronização com fila local e motivos de falha.
- Snapshot offline continua cifrado com Android Keystore.
- Cache em memória reduz descriptografia e parsing repetitivos.

### Dispositivo
- Modo terminal configurável por aparelho.
- Opção de manter tela ligada.
- Integração com Screen Pinning/Lock Task permitido a apps comuns.
- Tentativa opcional de abrir após reinicialização, sujeita às políticas do Android/OEM.
- Criação de dispositivo/token passou a usar transação explícita, retry de colisão e Request ID para diagnóstico.
- Formulário preserva nome/PIN quando a criação falha e só limpa os campos depois da confirmação do servidor.
- PINs e senhas sensíveis ganharam controle de mostrar/ocultar com ícone de olho.

### Administração
- Tela de diagnóstico com servidor, banco, sessões, versão, offline e retenção.
- Gestão reorganizada em Dispositivos, Sincronização, Biometria, Diagnóstico, Modo terminal, Auditoria e Autorizações.
- Cadastro de Supervisor/Administrador preserva nome, e-mail, perfil e senha enquanto o processo do app permanece vivo; o rascunho é apagado ao concluir ou cancelar o cadastro.
