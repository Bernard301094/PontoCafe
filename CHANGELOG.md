# Changelog

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