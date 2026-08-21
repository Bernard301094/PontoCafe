# Auditoria UI/UX e responsividade — PontoCafe

> Inventário inicial registrado antes de qualquer alteração visual nesta branch.
> Branch de trabalho: `agent/ui-ux-responsive-redesign`.

## Escopo e salvaguardas

Esta auditoria cobre a única `Activity`, todas as superfícies Compose, destinos de
navegação, estados expostos pelos ViewModels, componentes reutilizáveis, diálogos,
folhas, menus, formulários, estados assíncronos e a câmera. A implementação visual
não deve alterar reconhecimento facial, liveness, regras 2/2, INÍCIO/RETORNO,
autorizações, segurança do quiosque, persistência offline, idempotência, contratos
de backend, notificações, vibração ou cadastro de dispositivos.

Não fazem parte deste trabalho: APKs, artefatos de release, deploys, migrações ou
mudanças de infraestrutura.

## Arquitetura de interface descoberta

- `MainActivity` é a única Activity e o ponto raiz de composição.
- Não há Fragments nem XML de layout; a interface é Jetpack Compose.
- Áreas raiz: Ponto/Quiosque, Administrador, Supervisor e seletor/bloqueio da área
  restrita.
- A navegação interna é dirigida por estado, não por Navigation Compose com rotas
  em string.
- Admin e Supervisor usam `NavigationSuiteScaffold`, que alterna automaticamente
  a apresentação da navegação conforme o espaço disponível.
- A câmera é mantida montada por `PontoFlowHost`; recibos e bloqueios são overlays,
  evitando reinicializar CameraX entre operações.

### Perfis e limites de acesso

| Perfil/contexto | Objetivo | Superfícies permitidas |
|---|---|---|
| Ponto/Quiosque | Reconhecer a pessoa e registrar INÍCIO/RETORNO | configuração inicial do aparelho, câmera, estado pronto/processando, recibo, bloqueio, acesso restrito e modo quiosque |
| Administrador | Controle, configuração, contas, dispositivos e diagnóstico | dashboard, pessoas, gestão/regras, contas, autorizações, auditoria, dispositivos, quiosque, histórico e confiabilidade |
| Supervisor | Decisões operacionais e exceções em tempo real | ao vivo, pessoas, relatórios, histórico, cadastro/biometria e autorização |
| Área restrita | Selecionar/restaurar uma conta e proteger a sessão | seleção de conta, login, bloqueio por PIN/biometria e retorno ao Ponto |

### Destinos e caminhos

**Raiz (`AreaRestrita`)**

- `null` → `DeviceSetupScreen` quando o aparelho ainda não foi configurado;
  caso contrário → `PontoFlowHost`/`FaceKioskScreen`.
- `LOGIN` → `RestrictedLoginModeScreen`.
- `ADMIN` → `AdminArea`.
- `SUPERVISOR` → `SupervisorAreaShell`.
- Uma sessão restrita restaurada passa por `RestrictedAreaLockScreen` após a app
  sair do primeiro plano.

**Administrador (`AdminDestination`)**

- Entrada: `LOADING`, `LOGIN`, `FIRST_SETUP`.
- Primários: `HOME`, `COLLABORATORS`, `SETTINGS`, apresentados como Visão geral,
  Pessoas e Gestão.
- Secundários: `NEW_ACCOUNT`, `USER_DETAIL`, `NEW_COLLABORATOR`,
  `BIOMETRIC_ENROLLMENT`, `AUTHORIZATION`, `AUDIT`.
- Confiabilidade (`ReliabilityDestination`): `COLLABORATOR_HISTORY`,
  `BIOMETRIC_DIAGNOSTICS`, `SYNC_CENTER`, `SYSTEM_DIAGNOSTICS`.
- Dispositivos e modo quiosque são abertos a partir da área de gestão/dashboard.

**Supervisor (`SupervisorDestination`)**

- Entrada: `LOGIN`.
- Primários: `AO_VIVO`, `COLABORADORES`, `RELATORIOS`.
- Secundários: `HISTORICO`, `NOVO_COLABORADOR`, `BIOMETRIA`, `AUTORIZACAO`.

## Estados de UI e fontes de verdade

| Fonte | Estado exposto | Responsabilidade visual |
|---|---|---|
| `PontoCafeViewModel` | `PontoCafeUiState` | configuração do dispositivo, catálogo biométrico, sincronização, reconhecimento, liveness, registro, erros e recibo/bloqueio |
| `AdminViewModel` | `AdminUiState` | autenticação, destino, colaboradores, contas, regras, auditoria, feedback e operações administrativas |
| `AdminReliabilityViewModel` | `AdminReliabilityUiState` | histórico individual, diagnóstico biométrico, sincronização e saúde do sistema |
| `AdminDeviceViewModel` | `AdminDeviceUiState` | inventário e estado dos dispositivos, provisionamento e PIN do quiosque |
| `SupervisorViewModel` | `SupervisorUiState` | sessão, operação ao vivo, histórico, pessoas, relatórios, autorização e biometria |

Os estados locais adicionais encontrados cobrem seleção/filtros, calendários,
diálogos, folhas, edição de formulários, permissão de câmera, foco e feedback. Eles
devem continuar locais quando são efêmeros; dados operacionais permanecem nos
ViewModels/repositórios existentes.

## Inventário completo de superfícies

### Raiz, autenticação e quiosque

| Superfície/arquivo | Perfil | Estado inicial | Observações de auditoria |
|---|---|---:|---|
| `MainActivity.kt` | Todos | Ativa | host raiz, restauração/bloqueio e barras do sistema |
| `DeviceSetupScreen.kt` | Ponto | Ativa | formulário de configuração inicial do dispositivo |
| `PontoFlowHost.kt` | Ponto | Ativa | orquestra câmera contínua, registro e overlays de sucesso/bloqueio |
| `FaceKioskScreen.kt` | Ponto | Ativa | relógio, câmera, guia facial, liveness, sync/offline, acesso restrito e PIN |
| `camera/FaceCamera.kt` | Ponto/biometria | Ativa | preview CameraX e análise facial; lógica crítica não visual |
| `KioskModeScreen.kt` | Admin/Ponto | Ativa | ativação, limitações, PIN e saída do modo quiosque |
| `RestrictedLoginModeScreen.kt` | Área restrita | Ativa | seleção de conta, autenticação e menu de conta |
| `RestrictedAreaLockScreen.kt` | Área restrita | Ativa | desbloqueio da sessão por PIN/biometria e fallback |
| `AdminLoginScreen.kt` | Admin | Ativa | login administrativo legado ainda alcançável pelo estado Admin |
| `FirstAdminSetupScreen.kt` | Admin | Ativa | criação protegida do primeiro administrador |
| `SupervisorLoginScreenV2.kt` | Supervisor | Ativa | autenticação do supervisor |
| `PointReceiptScreen.kt` | Ponto | Herdada, sem rota ativa | substituída pelos overlays de `PontoFlowHost`; mantida por compatibilidade |

### Administrador

| Superfície/arquivo | Perfil | Estado inicial | Observações de auditoria |
|---|---|---:|---|
| `AdminArea.kt` | Admin | Ativa | shell, navegação adaptativa, perfil e roteamento |
| `AdminPanelScreen.kt` | Admin | Ativa (wrapper) | encaminha para o dashboard V2 |
| `AdminHomeScreenV2.kt` | Admin | Ativa | centro de controle, KPIs, atenção, pausas e histórico |
| `AdminPeopleScreenV4.kt` | Admin | Ativa | colaboradores/contas, busca, filtros, seleção em lote e master-detail |
| `AdminManagementScreenV2.kt` | Admin | Ativa (wrapper) | feedback e encaminhamento para Gestão V3 |
| `AdminManagementScreenV3.kt` | Admin | Ativa | configurações operacionais e editor de regras de café |
| `AdminNewCollaboratorScreen.kt` | Admin | Ativa | formulário de colaborador e continuidade para biometria |
| `AdminBiometricEnrollmentScreen.kt` | Admin | Ativa | confirmação, permissão, câmera, captura, qualidade e sucesso |
| `AdminNewAccountScreen.kt` | Admin | Ativa | host do formulário de nova conta |
| `AdminAccountForm.kt` | Admin | Ativa | dados, perfil, senha, validação e consequências de acesso |
| `AdminUserDetailScreen.kt` | Admin | Ativa | perfil, estado, troca de senha e desativação/remoção |
| `AdminAuthorizationScreen.kt` | Admin | Ativa | fluxo em etapas para autorização excepcional |
| `AdminAuditScreen.kt` | Admin | Ativa | busca, data, tipo, lista e detalhe de eventos |
| `AdminDevicesScreenV2.kt` | Admin | Ativa | frota, cadastro, token, PIN, saúde e ações críticas |
| `CollaboratorHistoryScreen.kt` | Admin | Ativa | perfil, biometria, filtros e histórico individual |
| `BiometricDiagnosticsScreen.kt` | Admin | Ativa | catálogo, métricas, busca, validação da câmera e ações de sync |
| `SyncCenterScreen.kt` | Admin | Ativa | conectividade, cache, fila offline e falhas de sincronização |
| `SystemDiagnosticsScreen.kt` | Admin | Ativa | resumo, integridade, telemetria, versões e frota |
| `AdminRulesScreen.kt` | Admin | Herdada, sem rota ativa | substituída pelo editor em Gestão V3 |

### Supervisor

| Superfície/arquivo | Perfil | Estado inicial | Observações de auditoria |
|---|---|---:|---|
| `SupervisorNavigationShell.kt` | Supervisor | Ativa | shell, navegação adaptativa e roteamento |
| `SupervisorOperationScreen.kt` | Supervisor | Ativa | painel ao vivo e prioridades operacionais |
| `SupervisorLiveAlerts.kt` | Supervisor | Ativa | alertas transitórios/operacionais em tempo real |
| `SupervisorPeopleScreenV3.kt` | Supervisor | Ativa | busca, filtro, detalhes, ações e layout master-detail |
| `SupervisorNewCollaboratorPersistentScreen.kt` | Supervisor | Ativa | formulário persistente de novo colaborador |
| `SupervisorBiometricEnrollmentScreenV2.kt` | Supervisor | Ativa | confirmação, permissão, câmera, progresso e sucesso |
| `SupervisorAuthorizationScreen.kt` | Supervisor | Ativa | solicitação, validação, confirmação e código liberado |
| `SupervisorHistoryScreenV2.kt` | Supervisor | Ativa | data, lista compacta e detalhe progressivo |
| `SupervisorReportsScreenV2.kt` | Supervisor | Ativa | período, métricas, tendências, listas e exportação |
| `SupervisorCollaboratorManagement.kt` | Supervisor | Herdada, sem rota ativa | telas antigas substituídas por People V3, cadastro persistente e biometria V2 |

### Componentes, estados e padrões compartilhados

| Arquivo/padrão | Conteúdo descoberto | Estado inicial |
|---|---|---:|
| `PontoCafeTheme.kt` | esquemas claro/escuro, tipografia, formas, espaçamento e fundo | Ativo |
| `ResponsiveLayout.kt` | classe de largura compacta/média/expandida e larguras máximas | Ativo, incompleto para altura/orientação |
| `MaterialDesignSystem.kt` | hero, KPI, ação, seção, vazio, banner e botões | Ativo |
| `CommonComponents.kt` | headers, títulos, métricas, pills, avatar, alertas, resumo e mensagens | Ativo/compatibilidade |
| `ProfessionalUxComponents.kt` | app bar de área, teste operacional, comparação e gráfico | Ativo |
| `OperationalUxComponents.kt` | voltar ao topo, chave/valor e feedback | Ativo |
| `PeopleExperienceComponents.kt` | resumo, busca, filtros, pessoa, folha de ações, painel e filtro | Ativo |
| `OperationalPauseFeed.kt` | resumo, cartão compacto, progresso e folha de detalhe | Ativo |
| `AccountProfileComponents.kt` | resumo de conta e folha de perfil/logout | Ativo |
| `CollaboratorAvatarSourceDialog.kt` | câmera/galeria e permissão para avatar | Ativo |
| `SecurePasswordField.kt` | senha e controle de visibilidade | Ativo |
| `LoadingSkeleton.kt`, `PontoCafeSkeleton.kt` | skeletons de listas/métricas | Ativo |
| `BiometricRegistrationSuccessFeedback.kt` | confirmação visual/temporal de cadastro facial | Ativo |
| `PontoCafeAnimations.kt`, `PontoCafeMotion.kt` | sucesso e transições com movimento reduzido | Ativo |
| `UiCompileCompatibility.kt` | APIs auxiliares de compatibilidade Material/Compose | Ativo |

## Inventário de diálogos, folhas e menus

- Seletores de data: dashboard Admin, auditoria, histórico Supervisor e relatórios.
- Detalhes em folha: perfil da conta, evento de auditoria, pausa operacional,
  ações/filtros de pessoas e exportação de relatório.
- Confirmações Admin: editar regra/horário, token/dispositivo, ação crítica de
  dispositivo, excluir/desativar colaborador ou conta, importação CSV, edição em
  lote, edição individual e fonte do avatar.
- Confirmações Supervisor: sair/descartar, avatar, detalhe de pausa, autorização e
  erros/retorno do fluxo.
- Quiosque: diálogo de acesso restrito com conta, PIN, carregamento e erro.
- Menus: ferramentas/importação em Pessoas Admin e seletor/overflow de conta na
  área restrita.
- Folhas de ação de Pessoas devem virar painel lateral em largura expandida quando
  o contexto não for modal; confirmações continuam modais com largura limitada.

## Inventário de estados transversais

| Tipo | Ocorrências principais | Diretriz de redesign |
|---|---|---|
| Carregamento | shells Admin, Pessoas, histórico, relatórios, diagnósticos, câmera, sync e botões de salvar/exportar | skeleton/inline; bloqueio total somente quando não há conteúdo seguro para mostrar |
| Vazio | pessoas, acessos, dispositivos, pausas, histórico, auditoria, relatório, catálogo facial e fila offline | título específico, explicação curta e próxima ação útil |
| Erro | autenticação, validação, avatar, rede/backend, câmera/permissão, sync, exportação e operação de ponto | severidade semântica, texto pt-BR e recuperação concreta |
| Offline | Ponto, painel ao vivo, centro de sync e saúde | indicador persistente sem tratar operação local válida como falha total |
| Sucesso | ponto INÍCIO/RETORNO, autorização, cadastro/biometria, regra, dispositivo e exportação | confirmação inequívoca e retorno automático quando já previsto |
| Bloqueio | fora do horário, período usado, regra 2/2, autorização exigida, liveness/rosto desconhecido | causa compreensível e única próxima ação segura |
| Permissão | câmera de quiosque, cadastro facial e avatar; notificação no fluxo de sistema | explicar finalidade, distinguir negação temporária/permanente e abrir Ajustes quando necessário |

## Problemas encontrados antes da implementação

### Sistema visual

- Há uma boa base Material 3, mas três famílias de componentes (`Common`,
  `MaterialDesignSystem` e `Professional/Operational`) resolvem padrões semelhantes
  com densidade, raio e semântica diferentes.
- Tokens semânticos existem, porém alguns estados ainda escolhem cores diretamente
  e alguns cartões dependem da cor sem uma descrição de estado equivalente.
- Headers, botões e superfícies não têm uma política única de altura/densidade.
- A configuração edge-to-edge depende de comportamento implícito do target SDK e
  cada tela aplica insets de forma ligeiramente diferente.

### Responsividade

- A classe local considera apenas largura (`<600`, `<840`, `>=840 dp`); não modela
  altura compacta, orientação, font scale nem telas grandes/extra grandes.
- Vários layouts usam largura máxima corretamente, mas formulários e overlays
  podem perder ações em paisagem curta ou com fonte ampliada.
- Algumas folhas inferiores continuam ocupando o paradigma de telefone em tablet,
  mesmo quando painel lateral/diálogo seria mais previsível.
- O quiosque dimensiona guia, scrims e mensagens sobretudo pela largura; relógio,
  câmera e instrução podem competir em paisagem compacta.
- Overlays de recibo/bloqueio têm ícones e paddings fixos e não rolam.

### Usabilidade e acessibilidade

- Poucos componentes customizados expõem heading, `stateDescription`, live region
  ou uma descrição agrupada para TalkBack.
- O gráfico em Canvas não tem resumo textual acessível.
- Alguns ícones do quiosque usam área de toque de 40 dp; o alvo recomendado é pelo
  menos 48 dp.
- Formulários usam `imePadding`, mas o fluxo Next/Done e o descarte do teclado não
  são uniforme; o PIN do quiosque não conclui pelo IME.
- A permissão de câmera no quiosque não distingue claramente negação permanente e
  não oferece abrir Ajustes depois dessa condição.
- Há mensagens corretas, mas alguns erros vindos do repositório podem chegar com
  linguagem técnica e precisam de apresentação contextual sem alterar o diagnóstico.

### Densidade e desempenho

- Dashboard e gestão já possuem colunas adaptativas, mas ainda há excesso de
  superfícies aninhadas e repetição de métricas em alguns tamanhos.
- As listas principais são lazy; diálogos e conteúdos curtos usam `Column`, porém
  alguns corpos modais extensos precisam de limite e rolagem.
- A câmera não deve receber animações ou efeitos adicionais; o redesign deve limitar
  recomposições à camada de overlay e preservar o pipeline de análise.

## Estratégia de redesign

1. Expandir os tokens adaptativos para largura, altura, orientação, densidade de
   conteúdo e font scale, mantendo compatibilidade com chamadas existentes.
2. Consolidar padrões novos em componentes `Pc*`: scaffold de conteúdo, cabeçalho,
   feedback, estado vazio, botões com loading, diálogos responsivos, filtros e
   semântica. APIs antigas permanecem como wrappers quando removê-las elevar risco.
3. Normalizar edge-to-edge/insets e limitar conteúdo em telas largas sem apenas
   esticar os cartões de telefone.
4. Redesenhar por trabalho real: Admin como centro de controle; Supervisor como
   triagem em tempo real; Ponto como fluxo distante, simples e resistente a toque.
5. Converter áreas largas apropriadas em grade/master-detail; adaptar paisagem
   curta; tornar corpos modais roláveis e ações sempre alcançáveis.
6. Uniformizar busca, chips/filtros, formulários, IME, estados e microcopy pt-BR.
7. Adicionar semântica aos componentes compartilhados e aos feedbacks críticos,
   sem tornar ícones decorativos redundantes para leitores de tela.
8. Reexecutar busca global de UI e testes de contrato/compilação, sem tarefas de
   assemble/package.

## Matriz de cobertura inicial (baseline)

Esta tabela conserva o estado encontrado antes da implementação. “Revisada” significa
que o código, os estados e o papel no fluxo foram inspecionados; a matriz final, com o
resultado por superfície, está no item 28 do informe de fechamento.

| Área | Perfil | Revisada | Alterada | Decisão inicial |
|---|---|---:|---:|---|
| Raiz, sistema e sessão | Todos | Sim | Não | normalizar edge-to-edge, restauração e insets |
| Configuração do dispositivo | Ponto | Sim | Não | formulário adaptativo e feedback claro |
| Câmera/guia facial | Ponto | Sim | Não | altura compacta, permissão, alvos e semântica; preservar pipeline |
| Registro Ponto | Ponto | Sim | Não | preservar regras/tempos; tornar overlays adaptativos |
| Sucesso INÍCIO/RETORNO | Ponto | Sim | Não | confirmação legível à distância e retorno automático |
| Falha/bloqueio Ponto | Ponto | Sim | Não | causa + próxima ação, sem erro técnico |
| Acesso/saída do quiosque | Ponto/Admin/Supervisor | Sim | Não | proteção intacta, PIN e IME mais claros |
| Login/seletor de conta | Área restrita | Sim | Não | formulário, erro e conta ativa consistentes |
| Bloqueio/restauração | Área restrita | Sim | Não | acessibilidade e adaptação sem reduzir segurança |
| Shell e navegação Admin | Admin | Sim | Não | navegação suite + headers/insets consistentes |
| Home/controle Admin | Admin | Sim | Não | prioridade, KPIs, atenção e duas colunas úteis |
| Pessoas Admin | Admin | Sim | Não | busca/filtro/master-detail/lista de alta escala |
| Contas Admin | Admin | Sim | Não | diferenciar função, estado e consequências |
| Novo colaborador Admin | Admin | Sim | Não | agrupar campos, IME, validação e continuidade |
| Biometria Admin | Admin | Sim | Não | instrução/progresso/permissão/sync inequívocos |
| Autorizações Admin | Admin | Sim | Não | etapas, resumo e código com hierarquia |
| Gestão/regras de café | Admin | Sim | Não | agrupar, validar e explicar sem mudar regras |
| Dispositivos | Admin | Sim | Não | saúde semântica e ações progressivas |
| Auditoria | Admin | Sim | Não | filtros padronizados e detalhe progressivo |
| Histórico individual | Admin | Sim | Não | resumo + lista compacta + estado vazio |
| Diagnóstico biométrico | Admin | Sim | Não | resumo → atenção → detalhe, catálogo/sync precisos |
| Centro de sincronização | Admin | Sim | Não | offline/fila/falha/recuperação claramente separados |
| Saúde do sistema | Admin | Sim | Não | saúde → aviso → crítico e frota adaptativa |
| Shell/navegação Supervisor | Supervisor | Sim | Não | navegação suite e densidade operacional |
| Operação ao vivo | Supervisor | Sim | Não | excedidos/próximos primeiro e atualização clara |
| Alertas Supervisor | Supervisor | Sim | Não | severidade e anúncio acessível |
| Pessoas Supervisor | Supervisor | Sim | Não | busca/filtro/master-detail e ações permitidas |
| Novo colaborador Supervisor | Supervisor | Sim | Não | formulário persistente e continuidade |
| Biometria Supervisor | Supervisor | Sim | Não | paridade visual/semântica com Admin |
| Autorização Supervisor | Supervisor | Sim | Não | separação sensível e retorno seguro ao Ponto |
| Histórico Supervisor | Supervisor | Sim | Não | lista escaneável e diálogo responsivo |
| Relatórios/exportação | Supervisor | Sim | Não | filtros, métricas e detalhe adaptativos |
| Perfil/logout | Admin/Supervisor | Sim | Não | folha compacta no telefone e largura limitada |
| Busca/filtros/ordenação | Admin/Supervisor | Sim | Não | campo e paradigma de filtros comuns |
| Formulários/teclado | Todos | Sim | Não | Next/Done, tipos, erros e ações alcançáveis |
| Diálogos/folhas/menus | Todos | Sim | Não | largura/altura, rolagem e ação primária uniformes |
| Empty/loading/error/offline | Todos | Sim | Não | componentes semânticos e recuperação contextual |
| Tema/tokens/componentes | Todos | Sim | Não | consolidar sistema compartilhado |
| Animação/performance | Todos | Sim | Não | movimento sutil; câmera e listas sem custo decorativo |
| `AdminRulesScreen` herdada | Admin | Sim | Não | sem rota; não duplicar editor ativo |
| `PointReceiptScreen` herdada | Ponto | Sim | Não | sem rota; preservar como compatibilidade |
| módulo Supervisor herdado | Supervisor | Sim | Não | sem rota; não reabrir telas substituídas |

## Informe final de implementação

### 1. Lista completa de telas descobertas

- **Raiz, autenticação e quiosque:** `MainActivity`, `DeviceSetupScreen`,
  `PontoFlowHost`, `FaceKioskScreen`, `FaceCamera`, `KioskModeScreen`,
  `RestrictedLoginModeScreen`, `RestrictedAreaLockScreen`, `AdminLoginScreen`,
  `FirstAdminSetupScreen`, `SupervisorLoginScreenV2` e `PointReceiptScreen`
  (legada, sem rota ativa).
- **Administrador:** `AdminArea`, `AdminPanelScreen`, `AdminHomeScreenV2`,
  `AdminPeopleScreenV4`, `AdminManagementScreenV2`,
  `AdminManagementScreenV3`, `AdminNewCollaboratorScreen`,
  `AdminBiometricEnrollmentScreen`, `AdminNewAccountScreen`,
  `AdminAccountForm`, `AdminUserDetailScreen`, `AdminAuthorizationScreen`,
  `AdminAuditScreen`, `AdminDevicesScreenV2`, `CollaboratorHistoryScreen`,
  `BiometricDiagnosticsScreen`, `SyncCenterScreen`, `SystemDiagnosticsScreen` e
  `AdminRulesScreen` (legada, sem rota ativa).
- **Supervisor:** `SupervisorNavigationShell`, `SupervisorOperationScreen`,
  `SupervisorLiveAlerts`, `SupervisorPeopleScreenV3`,
  `SupervisorNewCollaboratorPersistentScreen`,
  `SupervisorBiometricEnrollmentScreenV2`, `SupervisorAuthorizationScreen`,
  `SupervisorHistoryScreenV2`, `SupervisorReportsScreenV2` e o módulo legado
  `SupervisorCollaboratorManagement` (sem rota ativa).
- **Superfícies modais e compartilhadas:** perfil/logout, ações e filtros de
  pessoas, detalhe de pausa, exportação, calendário, origem de avatar, acesso
  restrito/PIN, edição de regras, ações críticas de dispositivo/conta/pessoa e os
  estados de loading, vazio, erro, offline, sucesso, bloqueio e permissão.

### 2. Lista completa de perfis descobertos

Foram encontrados quatro contextos de uso: Ponto/Quiosque, Administrador,
Supervisor e Área restrita. Admin privilegia controle e configuração; Supervisor,
triagem operacional; Ponto, reconhecimento e registro a distância; Área restrita,
seleção, autenticação e proteção da sessão. O redesign preserva esses limites e não
uniformiza indevidamente a densidade entre perfis.

### 3. Arquitetura de navegação encontrada

A aplicação tem uma única Activity e navegação Compose dirigida por estado. A raiz
decide entre configuração do aparelho, Ponto, seleção de conta, Admin e Supervisor.
Admin usa `AdminDestination` e `ReliabilityDestination`; Supervisor usa
`SupervisorDestination`. Os shells ativos já usam `NavigationSuiteScaffold`, que
apresenta barra ou rail conforme o espaço. A câmera permanece montada dentro de
`PontoFlowHost`, e sucesso/bloqueio são overlays para não reiniciar CameraX.

### 4. Principais problemas de UX identificados

Havia hierarquia inconsistente entre dashboards, ações primárias pouco estáveis em
formulários/modalidades, estados visuais dependentes de cor, filtros divergentes,
mensagens assíncronas sem anúncio acessível, ações destrutivas pouco diferenciadas,
permissões de câmera sem recuperação clara e truncamentos artificiais em listas de
colaboradores/frota.

### 5. Problemas de layout responsivo identificados

O modelo original considerava quase só a largura. Paisagem de pouca altura, fonte
ampliada, tablets e telas muito largas não tinham política completa; overlays e
diálogos extensos podiam esconder ações; métricas e controles permaneciam lado a
lado quando já não cabiam; câmera, relógio e instruções competiam no quiosque.

### 6. Problemas do sistema de design identificados

Tokens Material 3 existiam, mas componentes de três famílias resolviam os mesmos
padrões com espaçamento, densidade, semântica e loading diferentes. Faltavam
contratos compartilhados para modal rolável, ação perigosa, permissão, heading,
estado acessível e alvos mínimos de toque.

### 7. Arquivos alterados

Foram alterados 48 arquivos existentes e adicionados quatro arquivos, totalizando
52 arquivos no worktree desta branch:

- **Raiz/câmera:** `MainActivity.kt`, `camera/FaceCamera.kt`.
- **Sistema e componentes:** `PontoCafeTheme.kt`, `ResponsiveLayout.kt`,
  `MaterialDesignSystem.kt`, `CommonComponents.kt`,
  `ProfessionalUxComponents.kt`, `OperationalUxComponents.kt`,
  `PeopleExperienceComponents.kt`, `OperationalPauseFeed.kt`,
  `AccountProfileComponents.kt`, `CollaboratorAvatarSourceDialog.kt`,
  `SecurePasswordField.kt`, `LoadingSkeleton.kt`, `PontoCafeSkeleton.kt`,
  `PontoCafeAnimations.kt`, `BiometricRegistrationSuccessFeedback.kt`, além dos
  novos `AdaptiveUiComponents.kt` e `CameraPermissionComponents.kt`.
- **Ponto, acesso e autenticação:** `DeviceSetupScreen.kt`, `FaceKioskScreen.kt`,
  `PontoFlowHost.kt`, `KioskModeScreen.kt`, `RestrictedLoginModeScreen.kt`,
  `RestrictedAreaLockScreen.kt`, `AdminLoginScreen.kt`,
  `FirstAdminSetupScreen.kt` e `SupervisorLoginScreenV2.kt`.
- **Administrador:** `AdminHomeScreenV2.kt`, `AdminPeopleScreenV4.kt`,
  `AdminManagementScreenV3.kt`, `AdminNewCollaboratorScreen.kt`,
  `AdminBiometricEnrollmentScreen.kt`, `AdminAccountForm.kt`,
  `AdminUserDetailScreen.kt`, `AdminAuthorizationScreen.kt`,
  `AdminAuditScreen.kt`, `AdminDevicesScreenV2.kt`,
  `CollaboratorHistoryScreen.kt`, `BiometricDiagnosticsScreen.kt`,
  `SyncCenterScreen.kt` e `SystemDiagnosticsScreen.kt`.
- **Supervisor:** `SupervisorOperationScreen.kt`, `SupervisorLiveAlerts.kt`,
  `SupervisorPeopleScreenV3.kt`,
  `SupervisorNewCollaboratorPersistentScreen.kt`,
  `SupervisorBiometricEnrollmentScreenV2.kt`,
  `SupervisorAuthorizationScreen.kt`, `SupervisorHistoryScreenV2.kt` e
  `SupervisorReportsScreenV2.kt`.
- **Verificação e documentação:** novo `ResponsiveLayoutTest.kt` e este
  `UI_UX_RESPONSIVE_AUDIT.md`.

### 8. Componentes reutilizáveis criados ou melhorados

Foram criados `PcBottomSheetContent`, `PcDialogBody`, `PcFormActions` e um
controlador/apresentação compartilhados de permissão de câmera. O sistema existente
ganhou botões com loading, botão destrutivo, semântica de heading/estado/live region,
alvos de 48 dp, busca/filtros/ordenação compartilhados, folhas adaptativas, resumo
textual de gráfico, feedback operacional e dimensões/tokens consistentes.

### 9. Melhorias de Administrador

O dashboard agora prioriza atenção, KPIs, histórico e atalhos em composição que muda
entre uma e duas colunas. Pessoas ganhou busca/ordenação persistentes, master-detail
seguro para fonte grande e modais roláveis. Gestão, auditoria, autorizações,
histórico e confiabilidade receberam hierarquia, ações e estados consistentes.

### 10. Melhorias de Supervisor

Operação ao vivo mantém prioridades no topo, informa atualização e adapta métricas.
Pessoas tem paridade de busca/filtros/master-detail; autorização deixou de limitar
visualmente a lista aos primeiros 60 colaboradores; histórico e relatórios ganharam
leitura progressiva, métricas adaptativas e modais consistentes. Alertas respeitam o
tempo recomendado pelo serviço de acessibilidade.

### 11. Melhorias de Ponto/Quiosque

A tela permanece simples e legível à distância, mas agora adapta guia, scrims,
relógio e instruções à altura compacta. A permissão de câmera distingue solicitação,
justificativa e negação permanente, com abertura de Ajustes. PIN, alvos de toque,
feedback, loading e IME foram uniformizados sem alterar registro ou segurança.

### 12. Melhorias de gestão de pessoas

Admin e Supervisor compartilham padrão de busca, chips, ordenação, filtros, cartões,
painel de detalhe e ações. Estados de consulta sobrevivem à recomposição; ações em
lote e individuais têm hierarquia; formulários de cadastro conduzem foco e IME; a
lista continua lazy e não perde conteúdo em telas estreitas ou com fonte ampliada.

### 13. Melhorias de cadastro biométrico

Os fluxos Admin e Supervisor agora têm os mesmos estados visuais para confirmação,
permissão, instrução, captura, progresso, qualidade, erro e sucesso. Em paisagem de
pouca altura o conteúdo rola e as ações permanecem alcançáveis. A matemática de
captura, liveness, embeddings e FaceNet não foi modificada.

### 14. Melhorias da UI de sincronização biométrica

Diagnóstico biométrico e Centro de sincronização distinguem carregamento inicial,
catálogo vazio, indisponibilidade, fila offline, falha e sincronização concluída.
Busca, validação por câmera, retenção, métricas e ações de sync têm loading e
semântica explícitos; não se mostra “tudo sincronizado” antes de existir estado real.

### 15. Melhorias de gestão de dispositivos

Cadastro, token, PIN e ações críticas foram separados por intenção, com loading,
validação, teclado adequado e tratamento visual destrutivo. Saúde, telemetria,
versão e conectividade deixaram de aparecer como um único estado ambíguo. Controles
empilham em largura/altura reduzida ou fonte grande e a frota não é truncada.

### 16. Melhorias de System Health

O diagnóstico apresenta primeiro o resumo, depois integridade, telemetria, versões e
frota completa. Loading, indisponível e vazio são diferentes; o request ID não se
repete como métrica; atualização não aparece duplicada; grades de saúde se adaptam à
fonte e cada dispositivo expõe estado textual além da cor.

### 17. Melhorias de contas e acesso

Login, primeiro Admin, seleção de conta, bloqueio de sessão, criação/edição de conta,
troca de senha e logout receberam limites de largura, foco/IME, validação contextual,
loading e hierarquia de risco. Senhas não são persistidas como estado salvo. PIN e
biometria mantêm os mesmos fallbacks e permissões de perfil.

### 18. Melhorias de relatórios

Período e filtros são roláveis horizontalmente quando necessário; resumo, métricas e
cartões diários se reorganizam por espaço e font scale. Exportação CSV/PDF usa uma
folha compartilhada, executa fora da thread principal e informa progresso/falha sem
bloquear a leitura do relatório.

### 19. Melhorias de configurações

Gestão reúne status operacional, regras e duração personalizada com agrupamento e
explicações coerentes. Switches expõem estado acessível e área de toque completa.
Configurações de quiosque e notificações distinguem permissão do sistema,
habilitação do app/canal e vibração, oferecendo o destino correto em Ajustes.

### 20. Melhorias de formulários e diálogos

Campos têm sequência Next/Done, tipos de teclado, foco, erro e loading consistentes.
`PcDialogBody` limita altura e permite rolagem; `PcFormActions` mantém a ação primária
alcançável e empilha quando necessário. Confirmações destrutivas usam estilo próprio;
folhas inferiores respeitam insets e largura útil.

### 21. Melhorias de erro, loading e vazio

Skeletons e indicadores agora anunciam carregamento. Vazios explicam o que falta e,
quando há recuperação segura, oferecem próxima ação. Erros, offline e bloqueios não
se confundem com ausência de dados; feedbacks críticos usam live region e os botões
evitam submissão repetida durante operações assíncronas.

### 22. Melhorias de acessibilidade

Foram adicionados headings, `stateDescription`, agrupamento semântico, live regions,
resumo textual de gráficos, ícones direcionais autoespelhados e descrições de ações.
Controles interativos têm pelo menos 48 dp; elementos decorativos da câmera/animação
não poluem TalkBack; feedbacks temporizados consultam o timeout recomendado pelo
sistema sem alterar a duração visual normal.

### 23. Melhorias para tablets e telas grandes

Os breakpoints passaram a reconhecer compacta, média, expandida, grande e extra
grande. Conteúdo tem largura máxima legível; dashboards usam colunas úteis;
People usa master-detail somente quando há espaço real; métricas e ações deixam de
apenas esticar cartões de telefone.

### 24. Melhorias em paisagem

A altura disponível passou a ser uma dimensão de layout. Em paisagem curta,
instruções de câmera, cadastro biométrico, autenticação, bloqueio e overlays reduzem
densidade, rolam quando necessário e preservam ações. Colunas duplas são desativadas
quando font scale/altura tornam a leitura insegura.

### 25. Melhorias de desempenho da UI

Listas extensas continuam usando containers lazy; limites artificiais visíveis foram
removidos sem materializar cópias extras. Exportações usam dispatcher de IO. Estados
derivados e componentes compartilhados reduzem duplicação. A câmera permanece
montada no Ponto e não recebeu efeitos nem recomposição de análise adicionais.

### 26. Componentes intencionalmente não alterados e motivo

- `AdminRulesScreen`, `PointReceiptScreen` e
  `SupervisorCollaboratorManagement` foram apenas auditados: não têm rota ativa e
  alterar cópias substituídas criaria dois padrões concorrentes.
- `AdminArea`, `AdminPanelScreen`, `AdminManagementScreenV2`,
  `AdminNewAccountScreen` e `SupervisorNavigationShell` não precisaram de edição
  direta: são shells/wrappers corretos e recebem as melhorias dos destinos ou
  componentes filhos.
- `PontoCafeMotion` e `UiCompileCompatibility` já cumpriam, respectivamente,
  redução de movimento e compatibilidade de APIs; não havia ganho seguro em mudá-los.
- ViewModels, repositórios, banco, contratos de backend, regras 2/2,
  INÍCIO/RETORNO, idempotência, notificações/vibração e o pipeline CameraX/ML Kit/
  FaceNet/liveness ficaram fora das alterações. Em `FaceCamera`, somente lifecycle
  moderno e semântica decorativa do preview foram ajustados.

### 27. Riscos ou limitações restantes

- A validação feita foi de código/JVM; câmera, biometria, TalkBack, teclados OEM,
  tablets físicos, foldables e permissões em diferentes fabricantes ainda requerem
  uma rodada manual/instrumentada em aparelhos representativos.
- As três telas legadas continuam no código por compatibilidade e podem divergir no
  futuro se voltarem a receber rota; nesse caso devem ser removidas ou migradas antes.
- O resumo acessível do gráfico cobre seus dados, mas uma auditoria manual com
  TalkBack é necessária para validar ordem de foco e verbosidade em uso real.
- Não foi criado APK, bundle, artefato de release, deploy ou migração. A verificação
  executada foi `:app:compileDebugKotlin` e `:app:testDebugUnitTest`; os 16 testes
  concluíram sem falhas.

### 28. Matriz completa de cobertura UI

“Direta” indica alteração no arquivo; “compartilhada” indica resultado recebido por
um filho/componente alterado; “não” é reservado a wrappers corretos ou código legado
sem rota. Todas as superfícies abaixo foram revisadas no código e contra seus estados.

#### Raiz, autenticação e Ponto/Quiosque

| Área UI / arquivo | Perfil | Revisada | Alterada | Resultado / motivo |
|---|---|---:|---:|---|
| `MainActivity.kt` | Todos | Sim | Sim — direta | edge-to-edge explícito; host e restauração preservados |
| `DeviceSetupScreen.kt` | Ponto | Sim | Sim — direta | formulário adaptativo, IME, token salvo e loading |
| `PontoFlowHost.kt` | Ponto | Sim | Sim — direta | overlays roláveis/adaptativos e feedback acessível; mesmas chamadas e tempos normais |
| `FaceKioskScreen.kt` | Ponto | Sim | Sim — direta | altura compacta, permissão recuperável, PIN, alvos e estados |
| `camera/FaceCamera.kt` | Ponto/biometria | Sim | Sim — direta, visual | preview decorativo para TalkBack e import lifecycle moderno; pipeline intacto |
| `KioskModeScreen.kt` | Admin/Ponto | Sim | Sim — direta | linha inteira acionável, switch semântico e alvos mínimos |
| `RestrictedLoginModeScreen.kt` | Área restrita | Sim | Sim — direta | estados, chips, setas espelhadas e confirmação destrutiva |
| `RestrictedAreaLockScreen.kt` | Área restrita | Sim | Sim — direta | adaptação por altura/fonte, rolagem e semântica de bloqueio |
| `AdminLoginScreen.kt` | Admin | Sim | Sim — direta | largura/altura, foco, IME, erro e loading |
| `FirstAdminSetupScreen.kt` | Admin | Sim | Sim — direta | fluxo de foco/IME, ajuda, erro e loading |
| `SupervisorLoginScreenV2.kt` | Supervisor | Sim | Sim — direta | largura/altura, foco, IME, erro e loading |
| `PointReceiptScreen.kt` | Ponto | Sim | Não | legado sem rota; overlays ativos estão em `PontoFlowHost` |

#### Administrador

| Área UI / arquivo | Perfil | Revisada | Alterada | Resultado / motivo |
|---|---|---:|---:|---|
| `AdminArea.kt` | Admin | Sim | Sim — compartilhada | shell correto; destinos/componentes e edge-to-edge atualizados |
| `AdminPanelScreen.kt` | Admin | Sim | Não | wrapper fino para Home V2; sem UI própria a duplicar |
| `AdminHomeScreenV2.kt` | Admin | Sim | Sim — direta | prioridades, KPIs, histórico, atalhos e colunas adaptativas |
| `AdminPeopleScreenV4.kt` | Admin | Sim | Sim — direta | busca/filtro/ordenação, master-detail, modais e ações |
| `AdminManagementScreenV2.kt` | Admin | Sim | Não | wrapper/feedback para Gestão V3; sem layout próprio relevante |
| `AdminManagementScreenV3.kt` | Admin | Sim | Sim — direta | status, regras, switches, duração e diálogos adaptativos |
| `AdminNewCollaboratorScreen.kt` | Admin | Sim | Sim — direta | formulário, turnos roláveis, IME e loading |
| `AdminBiometricEnrollmentScreen.kt` | Admin | Sim | Sim — direta | permissão, câmera, progresso, compacto e ações adaptativas |
| `AdminNewAccountScreen.kt` | Admin | Sim | Sim — compartilhada | host preservado; formulário filho redesenhado |
| `AdminAccountForm.kt` | Admin | Sim | Sim — direta | perfis adaptativos, validação, foco, senha e loading |
| `AdminUserDetailScreen.kt` | Admin | Sim | Sim — direta | controles adaptativos, senha e ações destrutivas claras |
| `AdminAuthorizationScreen.kt` | Admin | Sim | Sim — direta | etapas, busca/período/motivo salvos, IME e loading |
| `AdminAuditScreen.kt` | Admin | Sim | Sim — direta | busca, filtros lazy, datas, loading e detalhe em folha |
| `AdminDevicesScreenV2.kt` | Admin | Sim | Sim — direta | frota completa, saúde, token/PIN e ações críticas |
| `CollaboratorHistoryScreen.kt` | Admin | Sim | Sim — direta | períodos, métricas, lista, vazio e ações adaptativas |
| `BiometricDiagnosticsScreen.kt` | Admin | Sim | Sim — direta | catálogo/sync, métricas, busca, câmera e retenção |
| `SyncCenterScreen.kt` | Admin | Sim | Sim — direta | loading/vazio/offline/falha separados e ações responsivas |
| `SystemDiagnosticsScreen.kt` | Admin | Sim | Sim — direta | integridade, telemetria, versões e frota sem truncar |
| `AdminRulesScreen.kt` | Admin | Sim | Não | legado sem rota; editor ativo está em Gestão V3 |

#### Supervisor

| Área UI / arquivo | Perfil | Revisada | Alterada | Resultado / motivo |
|---|---|---:|---:|---|
| `SupervisorNavigationShell.kt` | Supervisor | Sim | Sim — compartilhada | shell adaptativo preservado; destinos e componentes atualizados |
| `SupervisorOperationScreen.kt` | Supervisor | Sim | Sim — direta | prioridades, métricas, filtros salvos e refresh com loading |
| `SupervisorLiveAlerts.kt` | Supervisor | Sim | Sim — direta | live region e timeout recomendado por acessibilidade |
| `SupervisorPeopleScreenV3.kt` | Supervisor | Sim | Sim — direta | busca/filtros/master-detail e ações adaptativas |
| `SupervisorNewCollaboratorPersistentScreen.kt` | Supervisor | Sim | Sim — direta | persistência mantida; IME, turnos e loading melhorados |
| `SupervisorBiometricEnrollmentScreenV2.kt` | Supervisor | Sim | Sim — direta | paridade de estados/permissão/compacto com Admin |
| `SupervisorAuthorizationScreen.kt` | Supervisor | Sim | Sim — direta | lista completa, formulários, confirmação e feedback seguro |
| `SupervisorHistoryScreenV2.kt` | Supervisor | Sim | Sim — direta | métricas adaptativas, rótulos, semântica e diálogo rolável |
| `SupervisorReportsScreenV2.kt` | Supervisor | Sim | Sim — direta | períodos, métricas, dias, exportação IO e feedback |
| `SupervisorCollaboratorManagement.kt` | Supervisor | Sim | Não | módulo legado sem rota, substituído pelas telas V3/V2 |

#### Sistema de design e componentes compartilhados

| Área UI / arquivo | Perfil | Revisada | Alterada | Resultado / motivo |
|---|---|---:|---:|---|
| `PontoCafeTheme.kt` | Todos | Sim | Sim — direta | tokens de espaçamento/dimensão e toque mínimo |
| `ResponsiveLayout.kt` | Todos | Sim | Sim — direta | largura, altura, orientação, font scale, large e XL |
| `AdaptiveUiComponents.kt` | Todos | Sim | Sim — novo | corpos de folha/diálogo e ações de formulário adaptativas |
| `CameraPermissionComponents.kt` | Ponto/biometria | Sim | Sim — novo | estado único de solicitação, rationale, negação e Ajustes |
| `MaterialDesignSystem.kt` | Todos | Sim | Sim — direta | headings, loading, perigo, vazio, banner e semântica |
| `CommonComponents.kt` | Todos | Sim | Sim — direta | headers, métricas, status, mensagens e alvos de 48 dp |
| `ProfessionalUxComponents.kt` | Admin/Supervisor | Sim | Sim — direta | app bar, alvos, headings e resumo acessível do gráfico |
| `OperationalUxComponents.kt` | Admin/Supervisor | Sim | Sim — direta | FAB, chave/valor escalável e feedback live |
| `PeopleExperienceComponents.kt` | Admin/Supervisor | Sim | Sim — direta | busca, sorting, filtros, folhas, cartões e painel |
| `OperationalPauseFeed.kt` | Admin/Supervisor | Sim | Sim — direta | filtros lazy, status semântico e detalhe compartilhado |
| `AccountProfileComponents.kt` | Admin/Supervisor | Sim | Sim — direta | folha de perfil/logout com conteúdo adaptativo |
| `CollaboratorAvatarSourceDialog.kt` | Admin/Supervisor | Sim | Sim — direta | câmera/galeria, permissão e recuperação por Ajustes |
| `SecurePasswordField.kt` | Autenticação/contas | Sim | Sim — direta | visibilidade salva, IME e estado de erro |
| `LoadingSkeleton.kt` | Todos | Sim | Sim — direta | anúncio semântico de carregamento |
| `PontoCafeSkeleton.kt` | Todos | Sim | Sim — direta | estado de loading acessível |
| `BiometricRegistrationSuccessFeedback.kt` | Admin/Supervisor | Sim | Sim — direta | insets, compacto, live region e timeout acessível |
| `PontoCafeAnimations.kt` | Todos | Sim | Sim — direta | animação decorativa removida da árvore semântica |
| `PontoCafeMotion.kt` | Todos | Sim | Não | já respeitava redução de movimento; contrato preservado |
| `UiCompileCompatibility.kt` | Todos | Sim | Não | shim técnico sem decisão visual ou UX |

#### Fluxos e estados transversais

| Área UI | Perfil | Revisada | Alterada | Resultado / motivo |
|---|---|---:|---:|---|
| App bars, headers e navegação | Todos | Sim | Sim — compartilhada | insets, headings, back de 48 dp e adaptação barra/rail |
| Busca | Admin/Supervisor | Sim | Sim — compartilhada | padrão único, teclado Search, limpar e estado salvo |
| Filtros e ordenação | Admin/Supervisor | Sim | Sim — compartilhada | chips lazy, resumo e folha/painel conforme contexto |
| Formulários e teclado/IME | Todos | Sim | Sim — compartilhada | foco, Next/Done, validação, loading e ações alcançáveis |
| Seletores de data | Admin/Supervisor | Sim | Sim — direta/compartilhada | ações adaptativas e filtros temporais consistentes |
| Folha de perfil/logout | Admin/Supervisor | Sim | Sim — direta | largura, insets e ação de saída consistentes |
| Folhas de pessoas/filtros | Admin/Supervisor | Sim | Sim — direta | conteúdo rolável, ação clara e largura limitada |
| Folha de detalhe de pausa | Admin/Supervisor | Sim | Sim — direta | status textual e conteúdo adaptativo |
| Folha de exportação | Supervisor | Sim | Sim — direta | CSV/PDF, loading, erro e execução em IO |
| Diálogo de origem de avatar | Admin/Supervisor | Sim | Sim — direta | câmera/galeria e estados completos de permissão |
| Diálogo de acesso restrito/PIN | Ponto | Sim | Sim — direta | teclado Done, loading, erro e altura compacta |
| Diálogos de regras/horários | Admin | Sim | Sim — direta | rolagem, tempo, validação e ações adaptativas |
| Diálogos de dispositivo/PIN/token | Admin | Sim | Sim — direta | intenção separada e ação crítica diferenciada |
| Diálogos de conta/pessoa | Admin/Supervisor | Sim | Sim — direta/compartilhada | perigo explícito, loading e descarte seguro |
| Menus de conta, ferramentas e importação | Admin/Área restrita | Sim | Sim — compartilhada | alvos, rótulos e resultados consistentes |
| Loading | Todos | Sim | Sim — compartilhada | skeleton/inline, live region e prevenção de duplicidade |
| Estados vazios | Todos | Sim | Sim — compartilhada | título, explicação e próxima ação contextual |
| Erro | Todos | Sim | Sim — compartilhada | severidade, recuperação e erro de campo/modal distintos |
| Offline e sincronização | Ponto/Admin/Supervisor | Sim | Sim — direta/compartilhada | operação local, fila, falha e indisponível separados |
| Sucesso INÍCIO/RETORNO | Ponto | Sim | Sim — direta | legível à distância, acessível e retorno automático preservado |
| Falha/bloqueio de Ponto | Ponto | Sim | Sim — direta | motivo e próxima ação em overlay adaptativo |
| Permissões de câmera | Ponto/Admin/Supervisor | Sim | Sim — compartilhada | rationale, negação permanente e link para Ajustes |
| Permissão/notificação/vibração | Supervisor | Sim | Sim — revisada | estados diferenciados; lógica funcional preservada |
| Animação e movimento reduzido | Todos | Sim | Sim — compartilhada | feedback discreto, timeout acessível e câmera sem efeitos |
| Cor, tipografia, iconografia e espaçamento | Todos | Sim | Sim — compartilhada | tokens, contraste semântico, auto-mirror e escala de fonte |
| Tabelas/listas densas e cartões | Admin/Supervisor | Sim | Sim — direta/compartilhada | lazy, stack/grade adaptativa e texto sem truncamento artificial |

## Validação executada

- `:app:compileDebugKotlin --no-daemon`: concluído com sucesso após o redesign.
- `:app:testDebugUnitTest --no-daemon`: 16 testes, zero falhas, zero erros e zero
  ignorados, incluindo quatro testes novos dos breakpoints/`fontScale`.
- Busca reversa de superfícies, permissões, setas direcionais, alvos de toque,
  diálogos e truncamentos visíveis: concluída.
- `git diff --check`: sem erros de whitespace; apenas avisos de conversão LF/CRLF do
  worktree Windows.
- Nenhuma tarefa `assemble`, package, bundle, release, assinatura, deploy ou migração
  foi executada; nenhum APK foi criado por esta validação.
