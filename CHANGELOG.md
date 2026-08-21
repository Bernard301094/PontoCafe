# Changelog

## 1.0.0 — Release Candidate · observabilidade, hardening e gate de produção

### Saúde do sistema e frota
- Expande o diagnóstico administrativo com métricas das últimas 24 horas para pausas e operações protegidas pela camada exactly-once.
- Adiciona visão de frota baseada na telemetria técnica já enviada pelos dispositivos: versão do app, modelo do aparelho, Android, atividade, crashes e travamentos prolongados.
- Sinaliza dispositivos desatualizados, sem telemetria recente ou com crash/travamento nas últimas 24 horas sem bloquear o registro do Ponto.
- A tela **Saúde do sistema** passa a separar operação atual, integridade do Ponto, frota, banco, política de produção e saúde local.
- Contadores idempotentes são apresentados como **operações protegidas**, sem inferir ou rotular incorretamente cada operação como duplicidade evitada.

### Release e compatibilidade
- Promove a candidata Android para `1.0.0` (`versionCode 100`) mantendo compile/target SDK 36, Java 17, R8/minify e resource shrinking.
- Mantém FaceNet fixado no mesmo commit/blob, CPU/XNNPACK e a mesma compatibilidade dos templates já cadastrados.
- Mantém threshold de reconhecimento `0.72`, margem `0.06` e não adiciona delegate GPU.
- Política padrão de atualização passa a anunciar `1.0.0` como versão mais recente e `0.15.0` como versão mínima compatível, podendo ser sobrescrita pelas variáveis do ambiente de produção.

### CI e governança
- Substitui o antigo workflow canary por um gate real que executa testes e typecheck do backend, contrato de Release, testes Android e `assembleRelease`.
- O CI baixa e valida o FaceNet pelo Git blob exato antes da compilação.
- Adiciona `npm run release:check`, que falha se versão, R8, FaceNet, thresholds, CPU TFLite, migração exactly-once, CI ou documentação crítica se afastarem do contrato 1.0.
- Adiciona contrato automatizado `release-1.0-readiness-contract.test.ts`.

### Operação, recuperação e privacidade
- Adiciona checklist formal de liberação 1.0; o número `1.0.0` no código não transforma sozinho a branch em Release estável.
- Documenta backup e restore PostgreSQL com verificação de integridade em banco separado.
- Documenta governança biométrica, minimização de dados, retenção, telemetria e resposta a incidentes.
- A 1.0 depende da Integridade Operacional 0.15/migração 007 e não deve ser publicada antes dos testes físicos, restore real e validação da recuperação `COMMIT -> resposta perdida -> reconciliação`.

## 0.15.0 — Integridade operacional do Ponto

### Idempotência e exactly-once
- Cada mutação crítica do Ponto recebe um UUID persistido no Android antes da rede: `REGISTRO_RAPIDO`, `INICIAR` e `FINALIZAR`.
- O Worker serializa retries concorrentes com `pg_advisory_xact_lock` e persiste o resultado idempotente na mesma transação PostgreSQL que inicia ou finaliza a pausa.
- Se a resposta HTTP se perder depois do COMMIT, o mesmo UUID pode recuperar exatamente o resultado original; uma saída já confirmada não pode ser reinterpretada como retorno, nem um retorno confirmado virar nova saída.
- O endpoint `ponto/operacoes/reconciliar` espera qualquer transação concorrente com o mesmo UUID terminar antes de decidir se houve COMMIT ou rollback.
- O caminho rápido consulta primeiro uma operação incerta e, quando existe resultado autoritativo, reconstrói o comprovante sem criar nova mutação.
- Nos casos não elegíveis ao fast-path, uma operação incerta bloqueia nova leitura de estado e segue pelo fallback offline existente; a fila reutiliza o mesmo UUID e o Worker reconcilia o COMMIT original antes de qualquer nova ação.
- A sincronização offline também reconhece operações previamente confirmadas por `REGISTRO_RAPIDO`, `INICIAR` ou `FINALIZAR` e retorna `RECONCILIADO` sem executar uma segunda mutação.
- O journal Android é cifrado com AES-GCM protegido pelo Android Keystore e não persiste foto, embedding bruto, PIN, senha ou token de sessão.
- O UUID crítico é escrito de forma síncrona antes da rede. Depois de uma resposta mutante, ele só é liberado após o snapshot local correspondente ser confirmado em armazenamento persistente.
- Eventos offline de início/retorno também são persistidos de forma durável antes de liberar o journal curto de operação.

### Banco e implantação
- Adiciona `database/007_ponto_operation_idempotency.sql`, com a tabela `operacoes_ponto_idempotentes` e suporte aos tipos `REGISTRO_RAPIDO`, `INICIAR` e `FINALIZAR`.
- A migração `007` deve ser aplicada antes de implantar o Worker 0.15; o Android nunca acessa essa tabela diretamente.
- O Worker 0.15 deve estar ativo antes da instalação do APK 0.15 para que a reconciliação exactly-once esteja disponível em todos os caminhos protegidos.

### Compatibilidade
- Não altera FaceNet, modelo, crop, prewhitening, normalização, thresholds, margem, CPU/XNNPACK, prova de vida, CameraX, horários, limite do café, regra 2/2 ou autorizações do Supervisor.
- Android `0.15.0` (`versionCode 36`) mantém R8/minify e resource shrinking de produção.

## 0.14.0 — Contas protegidas + Relatórios com UX responsiva

### Acesso restrito
- Substitui o hero alto do seletor por uma faixa compacta de segurança, priorizando a conta que o usuário realmente quer abrir.
- Renomeia a área principal para **Entrar com uma conta salva** e separa claramente **Sessões salvas** de **Entrar com outra conta**.
- Cada sessão salva passa a ter CTA explícito **Abrir conta**; a remoção deixa de ficar exposta e vai para o menu **Mais opções** com confirmação.
- Em telas médias e expandidas, sessões salvas e novo login usam duas colunas; em celular permanecem em fluxo vertical confortável.
- Mantém múltiplas contas, sessões legadas, ativação por conta e armazenamento cifrado pelo Android Keystore sem persistir senhas.

### Relatórios do Supervisor
- Reorganiza a tela em **Período do relatório**, resumo, indicadores, tendência, registros por data e excessos que pedem atenção.
- Torna **Emitir relatório** uma ação principal próxima ao resumo do período, em vez de esconder a exportação no fim da tela.
- A emissão abre um `ModalBottomSheet` com **Gerar e compartilhar PDF** e **Exportar e compartilhar CSV**, sempre usando o período atualmente selecionado.
- Mantém os atalhos Hoje, 7 dias, 30 dias e seleção por calendário, além de comparação com período anterior, tendência e abertura do histórico diário.
- Em telas expandidas, resumo e emissão ficam lado a lado e as quatro métricas usam uma única linha; no celular permanecem em grade 2×2.
- Preserva `FileProvider`, geração PDF local, CSV do backend, compartilhamento Android e todas as regras/dados já existentes.
- Não altera FaceNet, embeddings, thresholds, liveness, CameraX, backend, PINs, autenticação ou regras de pausa.

## 0.13.0 — Área protegida + Ponto com UX responsiva

### Área protegida
- Reduz a altura e o excesso de espaço da tela de desbloqueio, mantendo o conteúdo centralizado e limitado a uma largura confortável em celulares e telas maiores.
- Reorganiza a hierarquia para mostrar primeiro **Área protegida**, perfil ativo e estado da sessão, com texto mais curto e direto.
- Destaca **Desbloquear agora** como ação principal e deixa **Voltar ao Ponto Café** como ação secundária.
- Mantém desbloqueio automático por `BiometricPrompt` e o fallback seguro para credencial do dispositivo; nenhuma proteção foi reduzida.

### Ponto Café
- Reorganiza a tela de bater ponto em torno da câmera, com scrims para legibilidade, guia facial responsivo e painel de instruções mais compacto.
- O topo passa a comunicar claramente **Bater ponto por reconhecimento facial** e o estado Online/Offline sem competir com a câmera.
- Melhora os estados em tempo real: aproximação, múltiplos rostos, desafio de prova de vida, identidade pronta, confirmação do ponto e rosto não reconhecido.
- A permissão de câmera ganha uma tela própria mais objetiva, explicando por que a câmera é necessária.
- Redesenha os comprovantes de saída/retorno e os bloqueios para diferenciar visualmente sucesso, limite excedido, folga já utilizada, 2/2 consumidas e fora do horário.
- Mantém os tempos atuais de feedback: 3 s para registro válido, 5 s para folga já utilizada e 2 s para bloqueios genéricos.
- Preserva a precedência operacional existente: 2/2 continua acima de folga repetida e de fora do horário.
- Não altera FaceNet, embeddings, thresholds, liveness, CameraX, backend, PINs ou regras de pausa.

## 0.11.0 — Gestão com UX responsiva

### Gestão
- Reorganiza o menu em uma **Central de gestão** com resumo operacional e hierarquia visual mais clara.
- Agrupa as ferramentas em **Operação** e **Confiabilidade e controle**, reduzindo a sensação de grade de atalhos sem prioridade.
- Adapta os atalhos aos breakpoints compartilhados do app: 1 coluna em telas compactas, 2 em médias e 3 em expandidas.
- Mantém Dispositivos, Sincronização, Autorizações, Modo terminal, Biometria, Diagnóstico e Auditoria, com cards compactos e affordance explícita de navegação.
- Reorganiza as regras de café em uma área própria; em telas expandidas os períodos podem ser exibidos em duas colunas.
- O editor mostra estado ativo/desativado e destaca **Alterações não salvas**, oferecendo **Descartar** e **Salvar** sem mudar as validações de negócio.
- Preserva os presets de 10, 12 e 15 minutos, duração personalizada, seleção de horário e validação pelo domínio `PontoCafeRules`.
- Move o **Teste operacional** para uma seção avançada recolhível para não competir com tarefas administrativas frequentes.
- `AdminManagementScreenV2` permanece apenas como entrypoint compatível e encaminha para a nova experiência V3.
- Não altera backend, autenticação, FaceNet, liveness, permissões ou regras operacionais.

## 0.10.9 — Pessoas com UX responsiva

### Pessoas
- Reduz o cabeçalho e remove a grade de métricas duplicadas, mantendo um resumo compacto de colaboradores, pendências e acessos.
- Separa **Colaboradores** de **Acessos** como contextos diferentes em vez de tratá-los como filtros equivalentes.
- Mantém busca e filtros fora da lista rolável para que permaneçam acessíveis durante a navegação.
- Adiciona filtros por biometria, setor e turno, com contador de filtros ativos.
- Substitui cards expansíveis por um painel de ações em `ModalBottomSheet` no celular, preservando a posição da lista.
- Em telas grandes, usa experiência master-detail: lista à esquerda e detalhes/ações da pessoa à direita.
- Compacta os cards de colaboradores e remove o selo redundante de avatar.
- Move ações destrutivas para **Mais opções**, reduzindo competição visual com cadastro/atualização de biometria.
- Troca os quatro botões permanentes do topo por CTA contextual flutuante e menu secundário de ferramentas.
- Melhora seleção em lote com `Selecionar todos`, contador contextual e barra de ação persistente.
- Compartilha os principais componentes de lista, filtros e ações entre Administrador e Supervisor para reduzir divergência de UX.
- Preserva regras de negócio, biometria, FaceNet, liveness, permissões e backend; a alteração é restrita à experiência da tela Pessoas.

## 0.10.8 — Restauração do reconhecimento facial compatível

### Biometria
- Remove a aceleração GPU experimental do FaceNet para preservar compatibilidade com os templates faciais já cadastrados.
- O embedding canônico volta a usar CPU/XNNPACK com 2 threads, o mesmo recorte, prewhitening e normalização utilizados na geração das biometrias existentes.
- Restaura um único detector ML Kit FAST com landmarks e classificação no mesmo frame, evitando divergência entre presença, liveness e captura.
- Mantém o reconhecimento adaptativo de forma progressiva: primeiro tenta o embedding canônico e só calcula recortes alternativos do mesmo frame se o canônico realmente falhar.
- O cadastro facial continua usando somente o embedding canônico; nenhuma biometria existente precisa ser recadastrada.
- Mantém CameraX 640×480 e `STRATEGY_KEEP_ONLY_LATEST`.

## 0.10.7 — Reconhecimento adaptativo + bloqueio diário + avatar por câmera

### Ponto Café
- Quando as pausas de **manhã e tarde já foram utilizadas no mesmo dia**, esse estado `2/2` passa a ter prioridade absoluta sobre `FORA_HORARIO`.
- A proteção é aplicada no caminho rápido, na confirmação biométrica autoritativa, no fallback offline e na sobreposição final do Ponto.
- O aviso informa claramente que não há mais pausa disponível no dia e permanece visível por 5 segundos.
- A proteção já existente contra repetição de apenas um período permanece ativa.
- Novos testes de contrato impedem que o estado `2/2` volte a ser mascarado por avisos genéricos de horário.

### Avatares
- Administrador e Supervisor podem definir/trocar o avatar usando **Câmera** ou **Galeria**.
- Fotos tiradas pela câmera passam pelo mesmo recorte central, redimensionamento e compressão WebP já usados para imagens da galeria.
- Avatar continua totalmente separado da biometria facial e nunca é usado no reconhecimento.

### Biometria
- Reconhecimento no Ponto passa a usar fallback adaptativo com recortes alternativos do mesmo frame quando o embedding canônico não é suficiente.
- Nenhuma foto adicional é solicitada para reconhecer a mesma pessoa com pequenas variações de enquadramento/aparência.
- O embedding canônico e todas as biometrias existentes permanecem compatíveis.
- FaceNet, normalização, prova de vida, limiar e margem de identificação não foram reduzidos.

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