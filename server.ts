import http from 'node:http';
import { parse as parseUrl } from 'node:url';

interface Collaborator {
  id: string;
  name: string;
  role: string;
  pin: string;
  department: string;
  status: 'active' | 'coffee_break' | 'meal_break' | 'off_duty';
  lastPontoTime?: string;
  lastPontoType?: string;
}

interface PontoRecord {
  id: string;
  collaboratorId: string;
  collaboratorName: string;
  type: 'entrada' | 'pausa_cafe' | 'retorno_cafe' | 'almoco_inicio' | 'almoco_retorno' | 'saida';
  timestamp: string;
  note?: string;
  source: 'kiosk' | 'supervisor';
}

interface OperationalAlert {
  id: string;
  severity: 'warning' | 'info' | 'critical';
  title: string;
  message: string;
  time: string;
  collaboratorName?: string;
}

// Initial state
const collaborators: Collaborator[] = [
  { id: 'col-1', name: 'Lucas Mendes', role: 'Barista Chefe', pin: '1024', department: 'Cafeteria & Balcão', status: 'active', lastPontoTime: '08:02', lastPontoType: 'Entrada' },
  { id: 'col-2', name: 'Camila Rocha', role: 'Atendente / Caixa', pin: '2048', department: 'Atendimento', status: 'coffee_break', lastPontoTime: '10:15', lastPontoType: 'Pausa Café' },
  { id: 'col-3', name: 'Bruno Silveira', role: 'Supervisor Geral', pin: '3072', department: 'Supervisão', status: 'active', lastPontoTime: '07:45', lastPontoType: 'Entrada' },
  { id: 'col-4', name: 'Mariana Souza', role: 'Barista Júnior', pin: '4096', department: 'Cafeteria & Balcão', status: 'off_duty', lastPontoTime: 'Ontem 17:30', lastPontoType: 'Saída' },
  { id: 'col-5', name: 'Rodrigo Fagundes', role: 'Confeiteiro / Cozinha', pin: '5120', department: 'Cozinha', status: 'active', lastPontoTime: '06:30', lastPontoType: 'Entrada' },
];

const pontoHistory: PontoRecord[] = [
  { id: 'rec-1', collaboratorId: 'col-5', collaboratorName: 'Rodrigo Fagundes', type: 'entrada', timestamp: '2026-09-10 06:30:14', source: 'kiosk' },
  { id: 'rec-2', collaboratorId: 'col-3', collaboratorName: 'Bruno Silveira', type: 'entrada', timestamp: '2026-09-10 07:45:02', source: 'kiosk' },
  { id: 'rec-3', collaboratorId: 'col-1', collaboratorName: 'Lucas Mendes', type: 'entrada', timestamp: '2026-09-10 08:02:40', source: 'kiosk' },
  { id: 'rec-4', collaboratorId: 'col-2', collaboratorName: 'Camila Rocha', type: 'entrada', timestamp: '2026-09-10 08:15:10', source: 'kiosk' },
  { id: 'rec-5', collaboratorId: 'col-2', collaboratorName: 'Camila Rocha', type: 'pausa_cafe', timestamp: '2026-09-10 10:15:22', note: 'Intervalo de café (15 min)', source: 'kiosk' },
];

const alerts: OperationalAlert[] = [
  { id: 'alt-1', severity: 'warning', title: 'Pausa de Café Excedente', message: 'Camila Rocha está há 16 min em pausa (limite: 15 min).', time: '10:31', collaboratorName: 'Camila Rocha' },
  { id: 'alt-2', severity: 'info', title: 'Abertura de Turno Concluída', message: '3 colaboradores presentes para o atendimento da manhã.', time: '08:30' }
];

function getFormattedNow(): string {
  const now = new Date();
  return now.toLocaleTimeString('pt-BR', { timeZone: 'America/Fortaleza', hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit' });
}

function getFullDateTimeNow(): string {
  const now = new Date();
  const dateStr = now.toISOString().slice(0, 10);
  const timeStr = getFormattedNow();
  return `${dateStr} ${timeStr}`;
}

const HTML_CONTENT = `<!DOCTYPE html>
<html lang="pt-BR" class="h-full">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Ponto Café - Sistema Operacional de Ponto</title>
  <meta name="description" content="Sistema de Ponto Eletrônico e Gestão de Presença para Cafeterias">
  <meta property="og:title" content="Ponto Café - Sistema Operacional de Ponto">
  <meta property="og:description" content="Sistema de Ponto Eletrônico e Gestão de Presença para Cafeterias">
  <script src="https://cdn.tailwindcss.com"></script>
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@300;400;500;600;700;800&family=JetBrains+Mono:wght@400;500;600&display=swap" rel="stylesheet">
  <script src="https://unpkg.com/lucide@latest"></script>
  <script>
    tailwind.config = {
      theme: {
        extend: {
          colors: {
            coffee: {
              50: '#FDF8F5',
              100: '#F7EBE1',
              200: '#EBD2BF',
              300: '#DDB396',
              400: '#C7906E',
              500: '#A46B47',
              600: '#845033',
              700: '#683D26',
              800: '#4F2E1C',
              900: '#341D12',
              950: '#1E0F09'
            },
            amberAccent: '#D97706',
            warmCream: '#FAF7F2'
          },
          fontFamily: {
            sans: ['"Plus Jakarta Sans"', 'sans-serif'],
            mono: ['"JetBrains Mono"', 'monospace']
          }
        }
      }
    };
  </script>
  <style>
    @keyframes pulse-subtle {
      0%, 100% { opacity: 1; }
      50% { opacity: 0.6; }
    }
    .animate-pulse-subtle {
      animation: pulse-subtle 2s cubic-bezier(0.4, 0, 0.6, 1) infinite;
    }
    /* A faixa de abas rola no eixo X em telas estreitas; a barra em si é ruído. */
    .no-scrollbar::-webkit-scrollbar { display: none; }
    .no-scrollbar { -ms-overflow-style: none; scrollbar-width: none; }
  </style>
</head>
<body class="bg-stone-100 text-stone-800 font-sans h-full flex flex-col antialiased select-none">

  <!-- Login: o painel fala com o backend real, entao precisa de sessao. -->
  <div id="login-overlay" class="hidden fixed inset-0 z-50 bg-coffee-950/60 backdrop-blur-sm items-center justify-center p-4">
    <form onsubmit="handleLogin(event)" class="w-full max-w-sm bg-white rounded-2xl shadow-xl border border-stone-200 p-6 flex flex-col space-y-4">
      <div class="flex items-center space-x-3">
        <div class="w-10 h-10 rounded-xl bg-amberAccent flex items-center justify-center text-white">
          <i data-lucide="coffee" class="w-5 h-5"></i>
        </div>
        <div>
          <h2 class="text-lg font-extrabold text-coffee-950 leading-tight">Ponto Café</h2>
          <p class="text-xs text-stone-500">Painel de gestão</p>
        </div>
      </div>
      <div>
        <label class="block text-xs font-semibold text-stone-600 mb-1">E-mail</label>
        <input id="login-email" type="email" required autocomplete="username"
          class="w-full px-3 py-2.5 rounded-xl border border-stone-200 bg-stone-50 text-sm focus:outline-none focus:border-amberAccent" />
      </div>
      <div>
        <label class="block text-xs font-semibold text-stone-600 mb-1">Senha</label>
        <input id="login-password" type="password" required autocomplete="current-password"
          class="w-full px-3 py-2.5 rounded-xl border border-stone-200 bg-stone-50 text-sm focus:outline-none focus:border-amberAccent" />
      </div>
      <p id="login-error" class="hidden text-xs text-red-600 font-semibold"></p>
      <button id="login-submit" type="submit"
        class="w-full py-2.5 rounded-xl bg-amberAccent text-white text-sm font-bold hover:opacity-95 transition-opacity">
        Entrar
      </button>
      <p class="text-[11px] text-stone-400 text-center">
        Mesma conta do app. Para bater ponto não é preciso entrar.
      </p>
      <button type="button" onclick="irParaTotem()"
        class="w-full py-2.5 rounded-xl border border-stone-200 text-stone-600 text-xs font-bold hover:bg-stone-50 transition-colors">
        Usar este aparelho como totem
      </button>
    </form>
  </div>

  <!-- Top Bar -->
  <header id="app-header" class="bg-white border-b border-stone-200 px-4 md:px-8 py-3 flex flex-wrap items-center justify-between gap-y-3 shadow-sm sticky top-0 z-30">
    <div class="flex items-center space-x-3">
      <div class="w-10 h-10 rounded-xl bg-coffee-800 text-amber-400 flex items-center justify-center shadow-inner">
        <i data-lucide="coffee" class="w-6 h-6"></i>
      </div>
      <div>
        <div class="flex items-center space-x-2">
          <h1 class="text-xl font-extrabold tracking-tight text-coffee-950">Ponto Café</h1>
          <span class="px-2 py-0.5 rounded-full text-xs font-semibold bg-amber-100 text-amber-800 border border-amber-200">v1.1.0</span>
        </div>
        <p class="text-xs text-stone-500 font-medium">Fuso Horário: America/Fortaleza</p>
      </div>
    </div>

    <!-- Live Digital Clock -->
    <div class="hidden sm:flex items-center space-x-4 bg-stone-50 px-4 py-2 rounded-xl border border-stone-200">
      <div class="text-right">
        <div id="live-time" class="font-mono text-2xl font-bold text-coffee-900 tracking-wider">--:--:--</div>
        <div id="live-date" class="text-xs font-medium text-stone-500 uppercase tracking-wider">Carregando data...</div>
      </div>
      <div class="w-2.5 h-2.5 rounded-full bg-emerald-500 animate-pulse-subtle" title="Sincronizado"></div>
    </div>

    <!-- Mode Selector Tabs
         Em telas estreitas (A55 tem ~412px) sete abas não cabem numa linha:
         a faixa rola no eixo X em vez de espremer ou quebrar o cabeçalho. -->
    <nav class="order-3 w-full lg:order-none lg:w-auto flex items-center bg-stone-100 p-1 rounded-xl border border-stone-200 text-xs font-semibold overflow-x-auto no-scrollbar">
      <button id="tab-kiosk" onclick="switchTab('kiosk')" class="shrink-0 flex items-center space-x-1.5 px-3 py-2 rounded-lg transition-all bg-white text-coffee-900 shadow-sm">
        <i data-lucide="calculator" class="w-4 h-4"></i>
        <span>Totem / Ponto</span>
      </button>
      <button id="tab-supervisor" onclick="switchTab('supervisor')" class="shrink-0 flex items-center space-x-1.5 px-3 py-2 rounded-lg transition-all text-stone-600 hover:text-stone-900">
        <i data-lucide="users" class="w-4 h-4"></i>
        <span>Painel Equipe</span>
      </button>
      <button id="tab-codes" onclick="switchTab('codes')" class="shrink-0 flex items-center space-x-1.5 px-3 py-2 rounded-lg transition-all text-stone-600 hover:text-stone-900">
        <i data-lucide="key-round" class="w-4 h-4"></i>
        <span>Códigos</span>
      </button>
      <button id="tab-devices" onclick="switchTab('devices')" class="shrink-0 flex items-center space-x-1.5 px-3 py-2 rounded-lg transition-all text-stone-600 hover:text-stone-900">
        <i data-lucide="smartphone" class="w-4 h-4"></i>
        <span>Dispositivos</span>
      </button>
      <button id="tab-audit" onclick="switchTab('audit')" class="shrink-0 flex items-center space-x-1.5 px-3 py-2 rounded-lg transition-all text-stone-600 hover:text-stone-900">
        <i data-lucide="shield-check" class="w-4 h-4"></i>
        <span>Auditoria</span>
      </button>
      <button id="tab-reports" onclick="switchTab('reports')" class="shrink-0 flex items-center space-x-1.5 px-3 py-2 rounded-lg transition-all text-stone-600 hover:text-stone-900">
        <i data-lucide="bar-chart-3" class="w-4 h-4"></i>
        <span>Relatórios</span>
      </button>
      <button id="tab-history" onclick="switchTab('history')" class="shrink-0 flex items-center space-x-1.5 px-3 py-2 rounded-lg transition-all text-stone-600 hover:text-stone-900">
        <i data-lucide="history" class="w-4 h-4"></i>
        <span>Registros</span>
      </button>
      <button id="tab-admin" onclick="switchTab('admin')" class="shrink-0 flex items-center space-x-1.5 px-3 py-2 rounded-lg transition-all text-stone-600 hover:text-stone-900">
        <i data-lucide="settings" class="w-4 h-4"></i>
        <span>Gestão</span>
      </button>
    </nav>
  </header>

  <!-- Notification Toast -->
  <div id="toast-container" class="fixed top-20 right-6 z-50 flex flex-col space-y-2 pointer-events-none"></div>

  <!-- Main Views Container -->
  <main class="flex-1 overflow-y-auto p-4 md:p-8 max-w-7xl mx-auto w-full">
    
    <!-- VIEW 1: TOTEM / PONTO -->
    <section id="view-kiosk" class="h-full flex flex-col justify-center items-center py-4">

      <!-- Aparelho ainda nao vinculado. Sem credencial de dispositivo nao ha
           batida: o servidor recusa /ponto sem X-Device-Token, e e por aqui que
           o codigo de ativacao gerado em Dispositivos entra. -->
      <div id="totem-pairing" class="hidden w-full max-w-md bg-white rounded-3xl p-6 sm:p-8 shadow-xl border border-stone-200">
        <div class="text-center mb-6">
          <div class="inline-flex items-center justify-center w-14 h-14 rounded-2xl bg-amber-50 text-coffee-700 border border-amber-200 mb-3 shadow-sm">
            <i data-lucide="monitor-smartphone" class="w-8 h-8"></i>
          </div>
          <h2 class="text-2xl font-bold text-coffee-950">Vincular este aparelho</h2>
          <p class="text-sm text-stone-500 mt-1">Digite o código de ativação de 10 caracteres que o Administrador gerou em Dispositivos.</p>
        </div>
        <input id="totem-activation-input" type="text" autocomplete="off" spellcheck="false" maxlength="10"
          oninput="onActivationInput()" onkeydown="if(event.key==='Enter')ativarTotem()"
          class="w-full text-center font-mono text-2xl tracking-[0.35em] px-4 py-4 rounded-2xl border border-stone-300 bg-stone-50 focus:border-amber-400 focus:bg-white focus:ring-2 focus:ring-amber-100 outline-none"
          placeholder="··········">
        <p class="text-[11px] text-stone-400 text-center mt-2">Letras e números. Maiúsculas e minúsculas contam: <span id="totem-activation-count" class="font-semibold">0</span>/10</p>
        <div id="totem-pairing-feedback" class="min-h-[24px] text-center text-xs font-semibold my-3 text-stone-500"></div>
        <button id="totem-activation-btn" onclick="ativarTotem()" disabled
          class="w-full py-3.5 rounded-2xl bg-coffee-900 text-white font-bold hover:bg-coffee-800 disabled:opacity-40 disabled:cursor-not-allowed transition-all">
          Ativar este aparelho
        </button>
        <p class="text-[11px] text-stone-400 text-center mt-4 leading-relaxed">O código curto vale uma vez só. Depois deste passo o navegador guarda uma credencial longa e passa a bater ponto por ela.</p>
        <div class="mt-5 pt-4 border-t border-stone-100 text-center">
          <button onclick="showLogin()" class="text-xs font-semibold text-stone-400 hover:text-coffee-900">Entrar como Administrador ou Supervisor</button>
        </div>
      </div>

      <!-- Aparelho vinculado: o fluxo real, em tres passos. -->
      <div id="totem-flow" class="hidden w-full max-w-lg">
        <div class="flex items-center justify-between mb-3 px-1 gap-3">
          <div class="flex items-center space-x-2 min-w-0">
            <span class="w-2 h-2 rounded-full bg-emerald-500 shrink-0"></span>
            <p class="text-xs font-semibold text-stone-600 truncate">Aparelho: <span id="totem-device-name">—</span></p>
          </div>
          <button onclick="desvincularTotem()" class="text-xs font-semibold text-stone-400 hover:text-red-600 shrink-0">Desvincular</button>
        </div>

        <div class="bg-white rounded-3xl p-5 sm:p-8 shadow-xl border border-stone-200">

          <!-- PASSO 1: escolher a pessoa -->
          <div id="totem-step-pessoa">
            <div class="text-center mb-5">
              <div class="inline-flex items-center justify-center w-14 h-14 rounded-2xl bg-amber-50 text-coffee-700 border border-amber-200 mb-3 shadow-sm">
                <i data-lucide="users" class="w-8 h-8"></i>
              </div>
              <h2 class="text-2xl font-bold text-coffee-950">Quem vai ao café?</h2>
              <p class="text-sm text-stone-500 mt-1">Toque no seu nome e depois digite o código.</p>
            </div>
            <input id="totem-busca" oninput="renderTotemPessoas()" type="text" autocomplete="off"
              placeholder="Buscar pelo nome ou matrícula"
              class="w-full px-4 py-3 rounded-2xl border border-stone-300 text-sm focus:border-amber-400 focus:ring-2 focus:ring-amber-100 outline-none mb-3">
            <div id="totem-pessoas" class="max-h-[44vh] overflow-y-auto divide-y divide-stone-100"></div>
          </div>

          <!-- PASSO 2: digitar o código de 6 caracteres -->
          <div id="totem-step-codigo" class="hidden">
            <button onclick="totemVoltarPessoa()" class="flex items-center space-x-1 text-xs font-semibold text-stone-400 hover:text-coffee-900 mb-3">
              <i data-lucide="arrow-left" class="w-3.5 h-3.5"></i><span>Trocar de pessoa</span>
            </button>
            <div class="text-center mb-4">
              <h2 id="totem-codigo-titulo" class="text-xl font-bold text-coffee-950">Código do café</h2>
              <p id="totem-codigo-sub" class="text-sm text-stone-500 mt-1"></p>
            </div>
            <div id="totem-boxes" class="grid grid-cols-6 gap-1.5 sm:gap-2 mb-3"></div>
            <div id="totem-feedback" class="min-h-[24px] text-center text-xs font-semibold mb-3 text-stone-500"></div>
            <div id="totem-keypad" class="grid grid-cols-8 gap-1 sm:gap-1.5 mb-2"></div>
            <button onclick="totemApagar()"
              class="w-full py-2.5 rounded-xl bg-stone-100 hover:bg-stone-200 text-stone-700 text-sm font-semibold mb-3">Apagar</button>
            <button id="totem-registrar" onclick="totemRegistrar()" disabled
              class="w-full py-3.5 rounded-2xl bg-coffee-900 text-white font-bold hover:bg-coffee-800 disabled:opacity-40 disabled:cursor-not-allowed transition-all">
              Registrar
            </button>
          </div>

          <!-- PASSO 3: comprovante -->
          <div id="totem-step-recibo" class="hidden text-center"></div>
        </div>
      </div>
    </section>

    <!-- VIEW 2: SUPERVISOR & TEAM OVERVIEW -->
    <section id="view-supervisor" class="hidden space-y-6">
      <div class="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div>
          <h2 class="text-2xl font-bold text-coffee-950">Painel Operacional da Cafeteria</h2>
          <p class="text-sm text-stone-500">Acompanhamento em tempo real de presença e pausas</p>
        </div>
        <div class="flex items-center space-x-2">
          <button onclick="refreshData()" class="px-3.5 py-2 rounded-xl bg-white border border-stone-200 text-stone-700 font-semibold hover:bg-stone-50 transition-all text-xs flex items-center space-x-1 shadow-sm">
            <i data-lucide="refresh-cw" class="w-4 h-4"></i>
            <span>Atualizar</span>
          </button>
        </div>
      </div>

      <!-- Quick Summary Cards -->
      <div class="grid grid-cols-2 md:grid-cols-4 gap-4">
        <div class="bg-white p-5 rounded-2xl border border-stone-200 shadow-sm">
          <div class="flex items-center justify-between text-stone-500 mb-2">
            <span class="text-xs font-semibold uppercase">Total Equipe</span>
            <i data-lucide="users" class="w-4 h-4"></i>
          </div>
          <div id="stat-total" class="text-2xl font-bold text-coffee-950">5</div>
          <span class="text-xs text-stone-400">Cadastrados</span>
        </div>

        <div class="bg-white p-5 rounded-2xl border border-emerald-100 shadow-sm">
          <div class="flex items-center justify-between text-emerald-600 mb-2">
            <span class="text-xs font-semibold uppercase">Em Atendimento</span>
            <i data-lucide="check-circle" class="w-4 h-4"></i>
          </div>
          <div id="stat-active" class="text-2xl font-bold text-emerald-700">3</div>
          <span class="text-xs text-emerald-600">Turno ativo</span>
        </div>

        <div class="bg-white p-5 rounded-2xl border border-amber-100 shadow-sm">
          <div class="flex items-center justify-between text-amber-600 mb-2">
            <span class="text-xs font-semibold uppercase">Pausa Café</span>
            <i data-lucide="coffee" class="w-4 h-4"></i>
          </div>
          <div id="stat-coffee" class="text-2xl font-bold text-amber-700">1</div>
          <span class="text-xs text-amber-600">Em intervalo</span>
        </div>

        <div class="bg-white p-5 rounded-2xl border border-stone-200 shadow-sm">
          <div class="flex items-center justify-between text-stone-400 mb-2">
            <span class="text-xs font-semibold uppercase">Fora de Turno</span>
            <i data-lucide="moon" class="w-4 h-4"></i>
          </div>
          <div id="stat-off" class="text-2xl font-bold text-stone-700">1</div>
          <span class="text-xs text-stone-400">Descanso / folga</span>
        </div>
      </div>

      <!-- Live Team Grid -->
      <div class="bg-white rounded-2xl border border-stone-200 overflow-hidden shadow-sm">
        <div class="px-6 py-4 border-b border-stone-100 flex items-center justify-between">
          <h3 class="font-bold text-coffee-950 text-base">Status Atual dos Colaboradores</h3>
          <span class="text-xs font-medium text-stone-500">Atualizado ao vivo</span>
        </div>
        <div class="divide-y divide-stone-100" id="team-list">
          <!-- Collaborators rendered dynamically -->
        </div>
      </div>

      <!-- Operational Alerts -->
      <div class="bg-white rounded-2xl border border-stone-200 p-6 shadow-sm">
        <h3 class="font-bold text-coffee-950 text-base mb-4 flex items-center space-x-2">
          <i data-lucide="bell" class="w-5 h-5 text-amber-600"></i>
          <span>Alertas Operacionais Recentes</span>
        </h3>
        <div class="space-y-3" id="alerts-list">
          <!-- Alerts rendered dynamically -->
        </div>
      </div>
    </section>

    <!-- VIEW 3: PONTO HISTORY LOG -->
    <!-- VIEW: RELATÓRIOS -->
    <section id="view-reports" class="hidden space-y-6">
      <div class="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div>
          <h2 class="text-2xl font-bold text-coffee-950">Relatórios</h2>
          <p class="text-sm text-stone-500">Resumo de pausas por período, para conferência e folha</p>
        </div>
      </div>

      <div class="bg-white rounded-2xl border border-stone-200 shadow-sm p-5 space-y-4">
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <div>
            <label class="block text-xs font-semibold text-stone-600 mb-1">Início</label>
            <input type="date" id="rep-inicio" class="w-full px-3 py-2.5 rounded-xl border border-stone-200 bg-stone-50 text-sm focus:outline-none focus:border-amberAccent">
          </div>
          <div>
            <label class="block text-xs font-semibold text-stone-600 mb-1">Fim</label>
            <input type="date" id="rep-fim" class="w-full px-3 py-2.5 rounded-xl border border-stone-200 bg-stone-50 text-sm focus:outline-none focus:border-amberAccent">
          </div>
        </div>
        <div class="flex flex-wrap gap-2">
          <button onclick="refreshReports()" class="flex items-center space-x-1.5 px-4 py-2.5 rounded-xl bg-amberAccent text-white text-sm font-semibold shadow hover:opacity-95">
            <i data-lucide="search" class="w-4 h-4"></i><span>Consultar</span>
          </button>
          <button onclick="baixarCsv()" class="flex items-center space-x-1.5 px-4 py-2.5 rounded-xl bg-white border border-stone-200 text-stone-700 text-sm font-semibold shadow-sm hover:bg-stone-50">
            <i data-lucide="download" class="w-4 h-4"></i><span>Baixar CSV</span>
          </button>
        </div>
      </div>

      <div class="grid grid-cols-2 lg:grid-cols-4 gap-3 md:gap-4" id="reports-stats"></div>

      <div class="bg-white rounded-2xl border border-stone-200 shadow-sm overflow-hidden">
        <div class="px-5 md:px-6 py-4 border-b border-stone-100">
          <h3 class="font-bold text-coffee-950 text-base">Maiores atrasos no período</h3>
        </div>
        <div class="divide-y divide-stone-100" id="reports-delays">
          <p class="px-5 py-8 text-xs text-stone-400 text-center">Escolha o período e consulte.</p>
        </div>
      </div>
    </section>

    <!-- VIEW: DISPOSITIVOS -->
    <section id="view-devices" class="hidden space-y-6">
      <div class="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div>
          <h2 class="text-2xl font-bold text-coffee-950">Dispositivos Protegidos</h2>
          <p class="text-sm text-stone-500">Aparelhos autorizados a registrar ponto</p>
        </div>
        <div class="flex items-center gap-2">
          <button onclick="abrirCadastroDispositivo()" class="flex items-center space-x-2 px-4 py-2.5 rounded-xl bg-coffee-900 text-white text-sm font-semibold shadow-sm hover:bg-coffee-800">
            <i data-lucide="plus" class="w-4 h-4"></i>
            <span>Cadastrar aparelho</span>
          </button>
          <button onclick="refreshDevices()" class="flex items-center space-x-2 px-4 py-2.5 rounded-xl bg-white border border-stone-200 text-stone-700 text-sm font-semibold shadow-sm hover:bg-stone-50">
            <i data-lucide="refresh-cw" class="w-4 h-4"></i>
            <span>Atualizar</span>
          </button>
        </div>
      </div>
      <div class="grid grid-cols-3 gap-3 md:gap-4" id="devices-stats"></div>
      <div class="bg-white rounded-2xl border border-stone-200 shadow-sm overflow-hidden">
        <div class="px-6 py-4 border-b border-stone-100">
          <h3 class="font-bold text-coffee-950 text-base">Aparelhos cadastrados</h3>
        </div>
        <div class="divide-y divide-stone-100" id="devices-list">
          <p class="px-6 py-8 text-xs text-stone-400 text-center">Carregando…</p>
        </div>
      </div>
    </section>

    <!-- MODAL: CADASTRAR APARELHO / MOSTRAR O CÓDIGO DE ATIVAÇÃO -->
    <div id="device-modal" class="hidden fixed inset-0 z-50 bg-stone-900/60 backdrop-blur-sm items-center justify-center p-4">
      <div class="bg-white w-full max-w-md rounded-3xl p-6 sm:p-8 shadow-2xl border border-stone-200">

        <!-- Formulário -->
        <div id="device-modal-form">
          <h3 class="text-xl font-bold text-coffee-950">Cadastrar aparelho</h3>
          <p class="text-sm text-stone-500 mt-1 mb-5">Ao salvar, o servidor gera um código de ativação de 10 caracteres. Ele aparece uma única vez.</p>
          <label class="block text-xs font-bold text-stone-600 mb-1">Nome do aparelho</label>
          <input id="device-nome" type="text" maxlength="120" placeholder="Totem do corredor"
            class="w-full px-4 py-3 rounded-2xl border border-stone-300 text-sm focus:border-amber-400 focus:ring-2 focus:ring-amber-100 outline-none mb-4">
          <label class="block text-xs font-bold text-stone-600 mb-1">PIN de desbloqueio <span class="font-medium text-stone-400">(opcional, 4 a 12 números)</span></label>
          <input id="device-pin" type="text" inputmode="numeric" maxlength="12" placeholder="Deixe em branco para não usar"
            class="w-full px-4 py-3 rounded-2xl border border-stone-300 text-sm focus:border-amber-400 focus:ring-2 focus:ring-amber-100 outline-none">
          <p class="text-[11px] text-stone-400 mt-1.5 leading-relaxed">Este PIN não bate ponto — ele só destrava o modo quiosque no aparelho. Quem bate ponto usa o código de 6 caracteres do Supervisor.</p>
          <div class="flex gap-2 mt-6">
            <button onclick="fecharCadastroDispositivo()" class="flex-1 py-3 rounded-2xl border border-stone-300 text-stone-700 font-semibold hover:bg-stone-50 text-sm">Cancelar</button>
            <button id="device-salvar" onclick="salvarDispositivo()" class="flex-1 py-3 rounded-2xl bg-coffee-900 text-white font-bold hover:bg-coffee-800 disabled:opacity-40 text-sm">Cadastrar</button>
          </div>
        </div>

        <!-- Código de ativação -->
        <div id="device-modal-token" class="hidden text-center">
          <div class="inline-flex items-center justify-center w-14 h-14 rounded-2xl bg-amber-50 text-coffee-700 border border-amber-200 mb-3 shadow-sm">
            <i data-lucide="key-round" class="w-8 h-8"></i>
          </div>
          <h3 class="text-xl font-bold text-coffee-950">Código de ativação</h3>
          <p id="device-token-nome" class="text-sm text-stone-500 mt-1 mb-4"></p>
          <div class="bg-stone-50 border border-dashed border-stone-300 rounded-2xl py-5 px-3 mb-2">
            <span id="device-token-valor" class="font-mono text-2xl sm:text-3xl font-bold text-coffee-950 tracking-[0.15em] break-all"></span>
          </div>
          <p class="text-[11px] text-red-600 font-semibold mb-4">Anote agora. Este código não volta a ser mostrado, e maiúsculas e minúsculas contam.</p>
          <div class="flex gap-2">
            <button onclick="copiarTokenDispositivo()" class="flex-1 py-3 rounded-2xl border border-stone-300 text-stone-700 font-semibold hover:bg-stone-50 text-sm">Copiar</button>
            <button onclick="fecharCadastroDispositivo()" class="flex-1 py-3 rounded-2xl bg-coffee-900 text-white font-bold hover:bg-coffee-800 text-sm">Já anotei</button>
          </div>
        </div>
      </div>
    </div>

    <!-- VIEW: AUDITORIA -->
    <section id="view-audit" class="hidden space-y-6">
      <div class="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div>
          <h2 class="text-2xl font-bold text-coffee-950">Trilha de Eventos</h2>
          <p class="text-sm text-stone-500">Registro imutável das ações administrativas</p>
        </div>
        <button onclick="refreshAudit()" class="flex items-center space-x-2 px-4 py-2.5 rounded-xl bg-white border border-stone-200 text-stone-700 text-sm font-semibold shadow-sm hover:bg-stone-50">
          <i data-lucide="refresh-cw" class="w-4 h-4"></i>
          <span>Atualizar</span>
        </button>
      </div>
      <div class="bg-white rounded-2xl border border-stone-200 shadow-sm overflow-hidden">
        <div class="divide-y divide-stone-100" id="audit-list">
          <p class="px-6 py-8 text-xs text-stone-400 text-center">Carregando…</p>
        </div>
      </div>
    </section>

    <!-- VIEW: CÓDIGOS DE CAFÉ -->
    <section id="view-codes" class="hidden space-y-6">
      <div class="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div>
          <h2 class="text-2xl font-bold text-coffee-950">Códigos de Café</h2>
          <p class="text-sm text-stone-500">Seis caracteres, letras e números, uso único — o colaborador digita no totem</p>
        </div>
        <button onclick="refreshCodes()" class="flex items-center space-x-2 px-4 py-2.5 rounded-xl bg-white border border-stone-200 text-stone-700 text-sm font-semibold shadow-sm hover:bg-stone-50">
          <i data-lucide="refresh-cw" class="w-4 h-4"></i>
          <span>Atualizar</span>
        </button>
      </div>

      <div class="bg-white rounded-2xl border border-stone-200 shadow-sm overflow-hidden">
        <div class="px-6 py-4 border-b border-stone-100 flex items-center justify-between">
          <h3 class="font-bold text-coffee-950 text-base">Códigos vivos</h3>
          <span id="codes-count" class="text-xs font-semibold text-stone-500">—</span>
        </div>
        <div class="divide-y divide-stone-100" id="codes-list">
          <p class="px-6 py-8 text-xs text-stone-400 text-center">Carregando…</p>
        </div>
      </div>

      <div class="bg-white rounded-2xl border border-stone-200 shadow-sm overflow-hidden">
        <div class="px-6 py-4 border-b border-stone-100">
          <h3 class="font-bold text-coffee-950 text-base">Emitir para um colaborador</h3>
          <p class="text-xs text-stone-500 mt-0.5">O código vale por poucos minutos; para o retorno ele não expira.</p>
        </div>
        <div class="divide-y divide-stone-100" id="codes-people-list">
          <p class="px-6 py-8 text-xs text-stone-400 text-center">Carregando…</p>
        </div>
      </div>
    </section>

    <section id="view-history" class="hidden space-y-6">
      <div class="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div>
          <h2 class="text-2xl font-bold text-coffee-950">Histórico de Batidas de Ponto</h2>
          <p class="text-sm text-stone-500">Linha do tempo auditável de registros de entrada, pausas e saída</p>
        </div>
      </div>

      <!-- Em telas estreitas a tabela vira lista de cartões: cinco colunas num
           A55 obrigariam a rolar de lado para ler cada linha. -->
      <div class="md:hidden bg-white rounded-2xl border border-stone-200 shadow-sm divide-y divide-stone-100" id="history-cards">
        <p class="px-5 py-8 text-xs text-stone-400 text-center">Carregando…</p>
      </div>

      <div class="hidden md:block bg-white rounded-2xl border border-stone-200 overflow-hidden shadow-sm">
        <div class="overflow-x-auto">
          <table class="w-full text-left text-sm">
            <thead class="bg-stone-50 text-xs font-semibold text-stone-500 uppercase border-b border-stone-200">
              <tr>
                <th class="px-6 py-3.5">Colaborador</th>
                <th class="px-6 py-3.5">Operação</th>
                <th class="px-6 py-3.5">Horário</th>
                <th class="px-6 py-3.5">Canal</th>
                <th class="px-6 py-3.5">Observação</th>
              </tr>
            </thead>
            <tbody id="history-table-body" class="divide-y divide-stone-100">
              <!-- Rendered dynamically -->
            </tbody>
          </table>
        </div>
      </div>
    </section>

    <!-- VIEW 4: ADMIN / COLLABORATORS MANAGEMENT -->
    <section id="view-admin" class="hidden space-y-6">
      <div class="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div>
          <h2 class="text-2xl font-bold text-coffee-950">Gestão de Colaboradores e Configurações</h2>
          <p class="text-sm text-stone-500">Cadastre colaboradores, defina PINs e parametrize a cafeteria</p>
        </div>
        <button onclick="openNewCollaboratorModal()" class="px-4 py-2.5 rounded-xl bg-coffee-800 hover:bg-coffee-900 text-white font-semibold transition-all text-xs flex items-center space-x-1.5 shadow-md">
          <i data-lucide="user-plus" class="w-4 h-4"></i>
          <span>Novo Colaborador</span>
        </button>
      </div>

      <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <!-- Collaborators List Card -->
        <div class="lg:col-span-2 bg-white rounded-2xl border border-stone-200 p-6 shadow-sm">
          <h3 class="font-bold text-coffee-950 text-base mb-4">Equipe Cadastrada</h3>
          <div class="divide-y divide-stone-100" id="admin-collaborator-list">
            <!-- Rendered dynamically -->
          </div>
        </div>

        <!-- Regras de café: janela e teto de cada período -->
        <div class="bg-white rounded-2xl border border-stone-200 p-5 md:p-6 shadow-sm">
          <div class="flex items-center justify-between mb-4">
            <div>
              <h3 class="font-bold text-coffee-950 text-base">Regras de Café</h3>
              <p class="text-xs text-stone-500 mt-0.5">Janela de cada período e o teto de tempo da pausa</p>
            </div>
          </div>
          <div class="space-y-3" id="rules-list">
            <p class="text-xs text-stone-400 py-3">Carregando…</p>
          </div>
        </div>

        <!-- Diagnóstico do sistema -->
        <div class="bg-white rounded-2xl border border-stone-200 p-5 md:p-6 shadow-sm">
          <div class="flex items-center justify-between mb-4">
            <div>
              <h3 class="font-bold text-coffee-950 text-base">Diagnóstico do Sistema</h3>
              <p class="text-xs text-stone-500 mt-0.5">Banco de dados e contadores da operação</p>
            </div>
            <button onclick="refreshDiagnostics()" class="p-2 rounded-xl border border-stone-200 text-stone-600 hover:bg-stone-50">
              <i data-lucide="refresh-cw" class="w-4 h-4"></i>
            </button>
          </div>
          <div class="grid grid-cols-2 sm:grid-cols-3 gap-3" id="diag-grid">
            <p class="text-xs text-stone-400 py-3">Carregando…</p>
          </div>
        </div>

        <!-- Contas de acesso: Admin e Supervisor -->
        <div class="bg-white rounded-2xl border border-stone-200 p-6 shadow-sm">
          <div class="flex items-center justify-between mb-4">
            <div>
              <h3 class="font-bold text-coffee-950 text-base">Contas de Acesso</h3>
              <p class="text-xs text-stone-500 mt-0.5">Administradores e Supervisores que entram no painel e no app</p>
            </div>
            <button onclick="openNewUserModal()" class="flex items-center space-x-1.5 px-3 py-2 rounded-xl bg-coffee-800 hover:bg-coffee-900 text-white text-xs font-semibold shadow">
              <i data-lucide="user-plus" class="w-4 h-4"></i>
              <span>Nova conta</span>
            </button>
          </div>
          <div class="divide-y divide-stone-100" id="user-account-list">
            <!-- Rendered dynamically -->
          </div>
        </div>

        <!-- Policy Settings Card -->
        <div class="bg-white rounded-2xl border border-stone-200 p-6 shadow-sm space-y-4">
          <h3 class="font-bold text-coffee-950 text-base">Políticas da Cafeteria</h3>
          
          <div>
            <label class="block text-xs font-semibold text-stone-600 mb-1">Tolerância Pausa Café (minutos)</label>
            <input type="number" id="setting-coffee-limit" value="15" class="w-full px-3 py-2 border border-stone-200 rounded-xl text-sm font-medium focus:outline-none focus:ring-2 focus:ring-amber-500">
          </div>

          <div>
            <label class="block text-xs font-semibold text-stone-600 mb-1">Fuso Horário Operacional</label>
            <input type="text" value="America/Fortaleza" readonly class="w-full px-3 py-2 border border-stone-200 bg-stone-50 rounded-xl text-sm font-mono text-stone-600">
          </div>

          <div>
            <label class="block text-xs font-semibold text-stone-600 mb-1">Síntese de Voz (Feedback)</label>
            <select id="setting-voice" class="w-full px-3 py-2 border border-stone-200 rounded-xl text-sm font-medium focus:outline-none focus:ring-2 focus:ring-amber-500">
              <option value="enabled">Ativada (Fala o nome ao bater ponto)</option>
              <option value="disabled">Desativada (Somente bipes/visual)</option>
            </select>
          </div>

          <div class="pt-4 border-t border-stone-100">
            <button onclick="saveSettings()" class="w-full py-2.5 rounded-xl bg-amber-600 hover:bg-amber-700 text-white font-semibold text-xs shadow transition-all">
              Salvar Configurações
            </button>
          </div>
        </div>
      </div>
    </section>

    <!-- MODAL: ADD COLLABORATOR -->
    <!-- Modal: nova conta de acesso -->
    <div id="new-user-modal" class="hidden fixed inset-0 z-50 bg-stone-900/60 backdrop-blur-sm items-center justify-center p-4">
      <div class="bg-white w-full max-w-md rounded-3xl p-6 sm:p-8 shadow-2xl border border-stone-200">
        <h3 class="text-xl font-bold text-coffee-950 mb-1">Nova Conta de Acesso</h3>
        <p class="text-xs text-stone-500 mb-6">Administrador vê tudo; Supervisor opera o turno indicado.</p>

        <form id="new-user-form" onsubmit="handleCreateUser(event)" class="space-y-4">
          <div>
            <label class="block text-xs font-semibold text-stone-700 mb-1">Nome</label>
            <input type="text" id="user-nome" required minlength="2" placeholder="Ex: Bernard Vasconcelos" class="w-full px-3.5 py-2.5 border border-stone-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-amber-500">
          </div>

          <div>
            <label class="block text-xs font-semibold text-stone-700 mb-1">E-mail</label>
            <input type="email" id="user-email" required placeholder="nome@empresa.com" class="w-full px-3.5 py-2.5 border border-stone-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-amber-500">
          </div>

          <div>
            <label class="block text-xs font-semibold text-stone-700 mb-1">Perfil</label>
            <select id="user-perfil" onchange="onPerfilChange()" class="w-full px-3.5 py-2.5 border border-stone-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-amber-500">
              <option value="SUPERVISOR">Supervisor</option>
              <option value="ADMIN">Administrador</option>
            </select>
          </div>

          <div id="user-turno-wrap">
            <label class="block text-xs font-semibold text-stone-700 mb-1">Turno do Supervisor</label>
            <select id="user-turno" class="w-full px-3.5 py-2.5 border border-stone-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-amber-500">
              <option value="A">Turno A</option>
              <option value="B">Turno B</option>
              <option value="C">Turno C</option>
              <option value="D">Turno D</option>
            </select>
          </div>

          <div>
            <label class="block text-xs font-semibold text-stone-700 mb-1">Senha inicial</label>
            <input type="password" id="user-senha" minlength="10" placeholder="Mínimo 10 caracteres" class="w-full px-3.5 py-2.5 border border-stone-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-amber-500">
            <p id="user-senha-hint" class="text-[11px] text-stone-400 mt-1">Opcional: em branco, o sistema gera uma senha provisória.</p>
          </div>

          <div class="flex items-center justify-end space-x-2 pt-4 border-t border-stone-100">
            <button type="button" onclick="closeNewUserModal()" class="px-4 py-2 rounded-xl border border-stone-300 text-stone-600 text-xs font-semibold hover:bg-stone-50">Cancelar</button>
            <button type="submit" class="px-5 py-2 rounded-xl bg-coffee-800 hover:bg-coffee-900 text-white text-xs font-semibold shadow">Criar conta</button>
          </div>
        </form>
      </div>
    </div>

    <div id="new-collaborator-modal" class="hidden fixed inset-0 z-50 bg-stone-900/60 backdrop-blur-sm items-center justify-center p-4">
      <div class="bg-white w-full max-w-md rounded-3xl p-6 sm:p-8 shadow-2xl border border-stone-200">
        <h3 class="text-xl font-bold text-coffee-950 mb-1">Cadastrar Colaborador</h3>
        <p class="text-xs text-stone-500 mb-6">O colaborador não tem senha nem PIN: quem libera a pausa é o código de café de 6 caracteres que o Supervisor emite na hora.</p>

        <form id="new-col-form" onsubmit="handleCreateCollaborator(event)" class="space-y-4">
          <div>
            <label class="block text-xs font-semibold text-stone-700 mb-1">Nome Completo</label>
            <input type="text" id="col-name" required placeholder="Ex: Patrícia Alves" class="w-full px-3.5 py-2.5 border border-stone-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-amber-500">
          </div>

          <div>
            <label class="block text-xs font-semibold text-stone-700 mb-1">Setor</label>
            <input type="text" id="col-setor" placeholder="Ex: Produção" class="w-full px-3.5 py-2.5 border border-stone-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-amber-500">
          </div>

          <div>
            <label class="block text-xs font-semibold text-stone-700 mb-1">Turno</label>
            <select id="col-role" class="w-full px-3.5 py-2.5 border border-stone-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-amber-500">
              <option value="">Sem turno</option>
              <option value="A">Turno A</option>
              <option value="B">Turno B</option>
              <option value="C">Turno C</option>
              <option value="D">Turno D</option>
            </select>
          </div>

          <div class="flex items-center justify-end space-x-2 pt-4 border-t border-stone-100">
            <button type="button" onclick="closeNewCollaboratorModal()" class="px-4 py-2 rounded-xl border border-stone-300 text-stone-600 text-xs font-semibold hover:bg-stone-50">Cancelar</button>
            <button type="submit" class="px-5 py-2 rounded-xl bg-coffee-800 hover:bg-coffee-900 text-white text-xs font-semibold shadow">Salvar Colaborador</button>
          </div>
        </form>
      </div>
    </div>

  </main>

  <script>
    // State management
    let state = {
      collaborators: [],
      history: [],
      alerts: [],
      codes: [],
      resumo: {},
      voiceEnabled: true
    };

    // Live Digital Clock
    function updateClock() {
      const now = new Date();
      const timeElem = document.getElementById('live-time');
      const dateElem = document.getElementById('live-date');
      
      const timeStr = now.toLocaleTimeString('pt-BR', { timeZone: 'America/Fortaleza', hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit' });
      const dateStr = now.toLocaleDateString('pt-BR', { timeZone: 'America/Fortaleza', weekday: 'short', day: '2-digit', month: 'short' });
      
      if (timeElem) timeElem.textContent = timeStr;
      if (dateElem) dateElem.textContent = dateStr;
    }
    setInterval(updateClock, 1000);
    updateClock();

    // Sound & Voice Guidance
    function speakVoice(text) {
      if (!state.voiceEnabled || !('speechSynthesis' in window)) return;
      try {
        window.speechSynthesis.cancel();
        const utterance = new SpeechSynthesisUtterance(text);
        utterance.lang = 'pt-BR';
        utterance.rate = 1.05;
        window.speechSynthesis.speak(utterance);
      } catch (err) {
        console.warn('Speech error:', err);
      }
    }

    // Toast notification
    function showToast(title, message, type = 'info') {
      const container = document.getElementById('toast-container');
      const toast = document.createElement('div');
      
      const bgColors = {
        success: 'bg-emerald-800 text-white',
        warning: 'bg-amber-700 text-white',
        error: 'bg-red-800 text-white',
        info: 'bg-coffee-900 text-white'
      };

      toast.className = \`p-4 rounded-2xl shadow-xl flex items-start space-x-3 pointer-events-auto transform transition-all duration-300 translate-y-[-10px] opacity-0 \${bgColors[type] || bgColors.info}\`;
      toast.innerHTML = \`
        <div class="mt-0.5"><i data-lucide="\${type === 'success' ? 'check-circle' : type === 'warning' ? 'alert-triangle' : 'info'}" class="w-5 h-5"></i></div>
        <div>
          <h4 class="font-bold text-sm">\${title}</h4>
          <p class="text-xs opacity-90 mt-0.5">\${message}</p>
        </div>
      \`;

      container.appendChild(toast);
      lucide.createIcons();

      requestAnimationFrame(() => {
        toast.classList.remove('translate-y-[-10px]', 'opacity-0');
      });

      setTimeout(() => {
        toast.classList.add('opacity-0', 'translate-y-[-10px]');
        setTimeout(() => toast.remove(), 300);
      }, 4000);
    }

    // Switch Tabs
    function switchTab(tabId) {
      const tabs = ['kiosk', 'supervisor', 'codes', 'devices', 'audit', 'reports', 'history', 'admin'];
      tabs.forEach(tab => {
        const btn = document.getElementById('tab-' + tab);
        const view = document.getElementById('view-' + tab);
        if (tab === tabId) {
          btn.className = 'shrink-0 flex items-center space-x-1.5 px-3 py-2 rounded-lg transition-all bg-white text-coffee-900 shadow-sm';
          view.classList.remove('hidden');
        } else {
          btn.className = 'shrink-0 flex items-center space-x-1.5 px-3 py-2 rounded-lg transition-all text-stone-600 hover:text-stone-900';
          view.classList.add('hidden');
        }
      });
      // Sair da aba do totem apaga o código a meio: o próximo a chegar não
      // pode encontrar os caracteres de outra pessoa nas caixas.
      if (tabId === 'kiosk') { totemBoot(); } else { totemLimparCodigo(); }
      // O prazo do código corre em segundos: ao abrir a aba, relê do servidor.
      if (tabId === 'codes') { refreshCodes(); }
      if (tabId === 'devices') { refreshDevices(); }
      if (tabId === 'audit') { refreshAudit(); }
      if (tabId === 'reports') { refreshReports(); }
      if (tabId === 'admin') { refreshRules(); refreshDiagnostics(); }
    }

    // ---- Totem / Ponto -----------------------------------------------------
    //
    // Esta aba deixou de ser maquete. Ela bate ponto de verdade, pelo mesmo
    // contrato que o totem Android usa: o aparelho guarda uma credencial
    // propria (X-Device-Token), a pessoa escolhe-se na lista e digita o codigo
    // de 6 caracteres que o Supervisor emitiu. Nao ha PIN em lado nenhum.
    //
    // Quem decide se aquilo foi uma saida ou um retorno e o servidor, nunca o
    // navegador: so o servidor sabe se aquele codigo ja foi usado para sair.
    // O painel envia o par (pessoa, codigo) e le a resposta.
    //
    // O alfabeto e o Crockford Base32 -- digitos e letras sem I, L, O e U, os
    // simbolos que ninguem distingue de 1 e 0 num papel escrito a pressa. O
    // teclado mostra so o que existe: a tecla que o servidor recusaria nem
    // chega a aparecer.
    const TOTEM_ALPHABET = '0123456789ABCDEFGHJKMNPQRSTVWXYZ';
    const TOTEM_CODE_LENGTH = 6;

    const totem = { codigo: '', pessoa: null, pessoas: [], enviando: false, erro: false, timer: null };

    function getDeviceToken() { return localStorage.getItem('ponto_device_token'); }
    function getDeviceName() { return localStorage.getItem('ponto_device_name') || 'Aparelho vinculado'; }

    // Converte a tecla que a pessoa carregou no simbolo canonico do alfabeto.
    // I e L viram 1, O vira 0 -- e a mesma tolerancia que o servidor aplica ao
    // normalizar o codigo, por isso digitar OL no lugar de 01 nao e um erro.
    function totemCanonical(char) {
      const upper = String(char).toUpperCase();
      const mapped = upper === 'I' || upper === 'L' ? '1' : (upper === 'O' ? '0' : upper);
      return TOTEM_ALPHABET.includes(mapped) ? mapped : null;
    }

    function totemPane(nome) {
      const vinculado = !!getDeviceToken();
      document.getElementById('totem-pairing').classList.toggle('hidden', vinculado);
      document.getElementById('totem-flow').classList.toggle('hidden', !vinculado);
      if (vinculado) document.getElementById('totem-device-name').textContent = nome || getDeviceName();
    }

    function totemBoot() {
      totemPane();
      if (getDeviceToken()) {
        totemVoltarPessoa();
        carregarTotemPessoas();
      } else {
        const campo = document.getElementById('totem-activation-input');
        if (campo) campo.focus();
      }
    }

    // --- Vinculacao do aparelho ---------------------------------------------

    // O token de ativacao distingue maiusculas de minusculas: o gerador usa as
    // 62 letras e digitos. Por isso nao se normaliza nada aqui alem de tirar o
    // que nao e letra nem digito -- passar por uppercase estragaria o codigo.
    function onActivationInput() {
      const campo = document.getElementById('totem-activation-input');
      campo.value = campo.value.replace(/[^A-Za-z0-9]/g, '').slice(0, 10);
      document.getElementById('totem-activation-count').textContent = String(campo.value.length);
      document.getElementById('totem-activation-btn').disabled = campo.value.length !== 10;
    }

    function pairingFeedback(texto, tom) {
      const cor = tom === 'error' ? 'text-red-600' : (tom === 'ok' ? 'text-emerald-600' : 'text-stone-500');
      const el = document.getElementById('totem-pairing-feedback');
      if (!el) return;
      el.textContent = texto;
      el.className = 'min-h-[24px] text-center text-xs font-semibold my-3 ' + cor;
    }

    async function ativarTotem() {
      const campo = document.getElementById('totem-activation-input');
      const codigo = campo.value.trim();
      if (codigo.length !== 10) return;

      const botao = document.getElementById('totem-activation-btn');
      botao.disabled = true;
      pairingFeedback('Validando o codigo...', 'info');
      try {
        const res = await fetch(API_BASE + '/setup/device-activation', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', 'X-App-Version': 'painel-web', 'X-Device-Model': 'Navegador' },
          body: JSON.stringify({ token: codigo })
        });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) {
          pairingFeedback(data.erro || 'Codigo de ativacao recusado.', 'error');
          botao.disabled = false;
          return;
        }
        localStorage.setItem('ponto_device_token', data.token);
        localStorage.setItem('ponto_device_name', (data.dispositivo && data.dispositivo.nome) || 'Aparelho vinculado');
        campo.value = '';
        onActivationInput();
        pairingFeedback('', 'info');
        showToast('Aparelho vinculado', 'Este navegador ja pode registrar ponto.', 'success');
        totemPane((data.dispositivo && data.dispositivo.nome) || null);
        carregarTotemPessoas();
      } catch (err) {
        pairingFeedback('Nao foi possivel falar com o servidor.', 'error');
        botao.disabled = false;
      }
    }

    function desvincularTotem() {
      if (!confirm('Desvincular este aparelho? Ele deixa de registrar ponto ate ser ativado com um codigo novo.')) return;
      localStorage.removeItem('ponto_device_token');
      localStorage.removeItem('ponto_device_name');
      totem.pessoa = null;
      totem.codigo = '';
      clearInterval(totem.timer);
      totemPane();
      const campo = document.getElementById('totem-activation-input');
      if (campo) { campo.value = ''; onActivationInput(); campo.focus(); }
    }

    // Todo pedido do totem leva a credencial do aparelho. Um 401 aqui nao e
    // sessao expirada de pessoa nenhuma: e o aparelho que perdeu autorizacao,
    // e o unico caminho de volta e um codigo de ativacao novo.
    async function deviceFetch(caminho, opcoes) {
      const cfg = opcoes || {};
      const res = await fetch(API_BASE + caminho, {
        method: cfg.method || 'GET',
        headers: Object.assign({
          'Content-Type': 'application/json',
          'X-Device-Token': getDeviceToken(),
          'X-App-Version': 'painel-web',
          'X-Device-Model': 'Navegador'
        }, cfg.headers || {}),
        body: cfg.body ? JSON.stringify(cfg.body) : undefined
      });
      const data = await res.json().catch(() => ({}));
      if (res.status === 401) {
        localStorage.removeItem('ponto_device_token');
        localStorage.removeItem('ponto_device_name');
        totemPane();
        pairingFeedback('Este aparelho nao esta mais autorizado. Peca um codigo novo ao Administrador.', 'error');
        throw new Error('device-unauthorized');
      }
      if (!res.ok) throw new Error(data.erro || ('HTTP ' + res.status));
      return data;
    }

    // --- Passo 1: escolher a pessoa -----------------------------------------

    async function carregarTotemPessoas() {
      const lista = document.getElementById('totem-pessoas');
      if (!lista || !getDeviceToken()) return;
      lista.innerHTML = '<p class="py-8 text-xs text-stone-400 text-center">Carregando...</p>';
      try {
        const data = await deviceFetch('/ponto/colaboradores');
        totem.pessoas = data.colaboradores || [];
        renderTotemPessoas();
      } catch (err) {
        if (err.message !== 'device-unauthorized') {
          lista.innerHTML = '<p class="py-8 text-xs text-red-500 text-center">' + err.message + '</p>';
        }
      }
    }

    function renderTotemPessoas() {
      const lista = document.getElementById('totem-pessoas');
      if (!lista) return;
      const campo = document.getElementById('totem-busca');
      const busca = ((campo && campo.value) || '').trim().toLowerCase();
      const visiveis = totem.pessoas.filter(p =>
        !busca ||
        (p.nome || '').toLowerCase().includes(busca) ||
        (p.matricula || '').toLowerCase().includes(busca)
      );

      if (visiveis.length === 0) {
        lista.innerHTML = '<p class="py-8 text-xs text-stone-400 text-center">'
          + (totem.pessoas.length === 0
            ? 'Ninguem disponivel para o cafe neste periodo.'
            : 'Nenhum nome corresponde a busca.')
          + '</p>';
        return;
      }

      lista.innerHTML = visiveis.map(p => {
        const iniciais = (p.nome || '?').split(' ').map(n => n[0]).join('').slice(0, 2).toUpperCase();
        const detalhe = [p.setor, p.turno ? 'Turno ' + p.turno : null, p.matricula].filter(Boolean).join(' · ');
        return '<button onclick="totemEscolher(&quot;' + p.id + '&quot;)" '
          + 'class="w-full flex items-center gap-3 px-2 py-3 text-left hover:bg-amber-50 rounded-xl transition-all">'
          + '<span class="w-10 h-10 shrink-0 rounded-2xl bg-amber-100 text-coffee-800 flex items-center justify-center font-bold text-sm">' + iniciais + '</span>'
          + '<span class="min-w-0 flex-1">'
          + '<span class="block text-sm font-bold text-coffee-950 truncate">' + p.nome + '</span>'
          + '<span class="block text-xs text-stone-500 truncate">' + (detalhe || 'Sem setor') + '</span>'
          + '</span>'
          + '<i data-lucide="chevron-right" class="w-4 h-4 text-stone-300 shrink-0"></i>'
          + '</button>';
      }).join('');
      lucide.createIcons();
    }

    async function totemEscolher(id) {
      const pessoa = totem.pessoas.find(p => p.id === id);
      if (!pessoa) return;
      clearInterval(totem.timer);
      totem.pessoa = pessoa;
      totem.codigo = '';
      totem.erro = false;

      document.getElementById('totem-step-pessoa').classList.add('hidden');
      document.getElementById('totem-step-recibo').classList.add('hidden');
      document.getElementById('totem-step-codigo').classList.remove('hidden');
      document.getElementById('totem-codigo-titulo').textContent = pessoa.nome;
      document.getElementById('totem-codigo-sub').textContent = 'Consultando...';
      renderTotemKeypad();
      renderTotemBoxes();
      totemFeedback('', 'info');

      // A consulta so decide o texto do ecra. Se falhar, o passo continua
      // valido: quem autoriza a batida e o registo, nao esta pergunta.
      try {
        const estado = await deviceFetch('/ponto/colaboradores/' + id + '/pausa');
        document.getElementById('totem-codigo-sub').textContent = estado.acaoEsperada === 'RETORNO'
          ? 'Digite o mesmo codigo que usou para sair.'
          : 'Digite os 6 caracteres entregues pelo Supervisor.';
      } catch (err) {
        if (err.message !== 'device-unauthorized') {
          document.getElementById('totem-codigo-sub').textContent = 'Digite os 6 caracteres entregues pelo Supervisor.';
        }
      }
    }

    function totemVoltarPessoa() {
      clearInterval(totem.timer);
      totem.pessoa = null;
      totem.codigo = '';
      totem.erro = false;
      const busca = document.getElementById('totem-busca');
      if (busca) busca.value = '';
      document.getElementById('totem-step-codigo').classList.add('hidden');
      document.getElementById('totem-step-recibo').classList.add('hidden');
      document.getElementById('totem-step-pessoa').classList.remove('hidden');
      renderTotemPessoas();
    }

    // --- Passo 2: digitar o codigo ------------------------------------------

    // Seis caixas em vez de um campo de texto: de pe, muitas vezes sem oculos,
    // o que a pessoa precisa de ver num relance e quantos caracteres ja
    // entraram e qual falta.
    function renderTotemBoxes() {
      const alvo = document.getElementById('totem-boxes');
      if (!alvo) return;
      let html = '';
      for (let i = 0; i < TOTEM_CODE_LENGTH; i++) {
        const char = totem.codigo[i] || '';
        const proxima = !totem.erro && i === totem.codigo.length;
        const base = 'h-14 sm:h-16 rounded-xl flex items-center justify-center text-2xl font-bold font-mono transition-all ';
        const estilo = totem.erro
          ? 'bg-red-50 border-2 border-red-400 text-red-700'
          : (proxima
            ? 'bg-amber-50 border-2 border-amber-300 text-coffee-950'
            : 'bg-stone-50 border border-stone-200 text-coffee-950');
        html += '<div class="' + base + estilo + '">' + char + '</div>';
      }
      alvo.innerHTML = html;
      const botao = document.getElementById('totem-registrar');
      if (botao) botao.disabled = totem.codigo.length !== TOTEM_CODE_LENGTH || totem.enviando;
    }

    function renderTotemKeypad() {
      const alvo = document.getElementById('totem-keypad');
      if (!alvo || alvo.dataset.pronto === '1') return;
      alvo.innerHTML = TOTEM_ALPHABET.split('').map(t =>
        '<button onclick="totemDigitar(&quot;' + t + '&quot;)" '
        + 'class="h-11 sm:h-12 rounded-xl bg-white hover:bg-amber-50 active:bg-amber-100 border border-stone-200 '
        + 'shadow-sm text-base font-bold font-mono text-coffee-900 transition-all">' + t + '</button>'
      ).join('');
      alvo.dataset.pronto = '1';
    }

    function totemFeedback(texto, tom) {
      const cor = tom === 'error' ? 'text-red-600' : (tom === 'ok' ? 'text-emerald-600' : 'text-stone-500');
      const el = document.getElementById('totem-feedback');
      if (!el) return;
      el.textContent = texto;
      el.className = 'min-h-[24px] text-center text-xs font-semibold mb-3 ' + cor;
    }

    function totemDigitar(char) {
      if (totem.enviando) return;
      const simbolo = totemCanonical(char);
      if (!simbolo) return;
      if (totem.erro) { totem.codigo = ''; totem.erro = false; totemFeedback('', 'info'); }
      if (totem.codigo.length >= TOTEM_CODE_LENGTH) return;
      totem.codigo += simbolo;
      renderTotemBoxes();
    }

    function totemApagar() {
      if (totem.enviando) return;
      if (totem.erro) { totem.codigo = ''; totem.erro = false; totemFeedback('', 'info'); }
      else totem.codigo = totem.codigo.slice(0, -1);
      renderTotemBoxes();
    }

    function totemLimparCodigo() {
      totem.codigo = '';
      totem.erro = false;
      renderTotemBoxes();
      totemFeedback('', 'info');
    }

    async function totemRegistrar() {
      if (totem.enviando || !totem.pessoa || totem.codigo.length !== TOTEM_CODE_LENGTH) return;
      totem.enviando = true;
      renderTotemBoxes();
      totemFeedback('Registrando...', 'info');
      try {
        const corpo = { colaboradorId: totem.pessoa.id, codigo: totem.codigo };
        // operacaoId torna a batida idempotente: se a resposta se perder no
        // caminho, reenviar nao abre uma segunda pausa.
        if (window.crypto && crypto.randomUUID) corpo.operacaoId = crypto.randomUUID();
        const data = await deviceFetch('/ponto/pausas/registrar', { method: 'POST', body: corpo });
        totem.enviando = false;
        totemRecibo(data);
        carregarTotemPessoas();
      } catch (err) {
        totem.enviando = false;
        if (err.message === 'device-unauthorized') return;
        totem.erro = true;
        renderTotemBoxes();
        totemFeedback(err.message, 'error');
      }
    }

    // --- Passo 3: comprovante -----------------------------------------------

    function totemRecibo(data) {
      const alvo = document.getElementById('totem-step-recibo');
      const nome = (data.colaborador && data.colaborador.nome) || (totem.pessoa && totem.pessoa.nome) || '';
      const primeiro = nome.split(' ')[0] || '';
      let icone, cor, titulo, detalhe;

      if (data.status === 'INICIO') {
        const i = data.inicio || {};
        icone = 'coffee';
        cor = 'amber';
        titulo = 'Bom cafe, ' + primeiro + '!';
        detalhe = 'Saida registrada as ' + (i.inicioLocal || '--:--')
          + '. Volte ate ' + (i.retornoAteLocal || '--:--') + '.';
      } else {
        const r = data.retorno || {};
        const minutos = Math.round((r.tempoContadoSegundos || 0) / 60);
        icone = r.excedeuLimite ? 'alert-triangle' : 'check-circle-2';
        cor = r.excedeuLimite ? 'red' : 'emerald';
        titulo = 'Retorno registrado';
        detalhe = 'Voltou as ' + (r.fimLocal || '--:--') + ' · ' + minutos + ' min contados'
          + (r.excedeuLimite ? ' - acima do limite.' : '.');
      }

      document.getElementById('totem-step-codigo').classList.add('hidden');
      document.getElementById('totem-step-pessoa').classList.add('hidden');
      alvo.classList.remove('hidden');
      alvo.innerHTML =
        '<div class="inline-flex items-center justify-center w-16 h-16 rounded-3xl mb-4 shadow-sm border '
        + (cor === 'amber' ? 'bg-amber-50 text-amber-600 border-amber-200'
          : (cor === 'red' ? 'bg-red-50 text-red-600 border-red-200' : 'bg-emerald-50 text-emerald-600 border-emerald-200'))
        + '"><i data-lucide="' + icone + '" class="w-9 h-9"></i></div>'
        + '<h2 class="text-2xl font-bold text-coffee-950">' + titulo + '</h2>'
        + '<p class="text-sm text-stone-500 mt-2 max-w-sm mx-auto">' + detalhe + '</p>'
        + '<button onclick="totemVoltarPessoa()" class="mt-6 w-full py-3.5 rounded-2xl bg-coffee-900 '
        + 'text-white font-bold hover:bg-coffee-800 transition-all">Liberar para o proximo</button>'
        + '<p class="text-[11px] text-stone-400 mt-3">Este totem se libera sozinho em '
        + '<span id="totem-countdown">8</span>s.</p>';
      lucide.createIcons();

      // O totem nao pode ficar parado no comprovante de outra pessoa: quem
      // chega a seguir veria o nome errado no ecra.
      let restantes = 8;
      clearInterval(totem.timer);
      totem.timer = setInterval(() => {
        restantes -= 1;
        const marcador = document.getElementById('totem-countdown');
        if (marcador) marcador.textContent = String(restantes);
        if (restantes <= 0) { clearInterval(totem.timer); totemVoltarPessoa(); }
      }, 1000);
    }

    // Teclado fisico: util no balcao, onde quase sempre ha um ligado.
    window.addEventListener('keydown', (e) => {
      const kioskView = document.getElementById('view-kiosk');
      const passoCodigo = document.getElementById('totem-step-codigo');
      if (!kioskView || !passoCodigo) return;
      if (kioskView.classList.contains('hidden') || passoCodigo.classList.contains('hidden')) return;
      if (e.key === 'Backspace') { e.preventDefault(); totemApagar(); }
      else if (e.key === 'Enter') { e.preventDefault(); totemRegistrar(); }
      else if (e.key === 'Escape') { totemLimparCodigo(); }
      else if (e.key.length === 1 && totemCanonical(e.key)) { e.preventDefault(); totemDigitar(e.key); }
    });

    // ---- Ligação com o backend real (Cloudflare Worker) --------------------
    // O painel deixou de ter dados próprios: tudo vem da mesma API que o totem
    // Android usa. A sessão é um Bearer emitido por /api/auth/sign-in/email e
    // guardado no localStorage deste navegador.
    const API_BASE = window.PONTO_API_BASE;

    function getToken() { return localStorage.getItem('ponto_token'); }
    function setToken(t) { localStorage.setItem('ponto_token', t); }
    function clearToken() { localStorage.removeItem('ponto_token'); }

    async function apiFetch(path) {
      const res = await fetch(API_BASE + path, {
        headers: { 'Authorization': 'Bearer ' + getToken() }
      });
      if (res.status === 401) {
        clearToken();
        showLogin('Sua sessão expirou. Entre novamente.');
        throw new Error('unauthenticated');
      }
      if (!res.ok) throw new Error('HTTP ' + res.status + ' em ' + path);
      return res.json();
    }

    async function doLogin(email, password) {
      const res = await fetch(API_BASE + '/api/auth/sign-in/email', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password })
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok || !data.token) {
        throw new Error(data.erro || 'E-mail ou senha inválidos.');
      }
      setToken(data.token);
      return data.user;
    }

    // O totem é quem regista ponto: ele tem o token de dispositivo, a fila
    // offline e o registo fiscal. O painel só lê -- por isso o separador do
    // quiosque fica desligado aqui em vez de oferecer um botão que falharia.
    function segundosParaRelogio(total) {
      const s = Math.max(0, total | 0);
      return String(Math.floor(s / 60)).padStart(2, '0') + ':' + String(s % 60).padStart(2, '0');
    }

    async function refreshData() {
      if (!getToken()) { showLogin(); return; }
      try {
        // allSettled, e nao all: /admin/operacao/resumo exige perfil ADMIN, e
        // com all um Supervisor perdia a tela inteira por causa de um 403 num
        // dado que e apenas complementar.
        const [pessoasR, ativasR, resumoR] = await Promise.allSettled([
          apiFetch('/gestao/colaboradores'),
          apiFetch('/supervisor/pausas/ativas'),
          apiFetch('/admin/operacao/resumo')
        ]);

        if (pessoasR.status === 'rejected') throw pessoasR.reason;
        const pessoas = pessoasR.value;
        const ativas = ativasR.status === 'fulfilled' ? ativasR.value : { pausas: [] };
        const resumo = resumoR.status === 'fulfilled' ? resumoR.value : null;

        state.resumo = resumo || {};
        const emPausaPorId = {};
        (ativas.pausas || []).forEach(p => { emPausaPorId[p.colaboradorId] = p; });

        state.collaborators = (pessoas.colaboradores || []).map(c => {
          const pausa = emPausaPorId[c.id];
          return {
            id: c.id,
            name: c.nome,
            role: c.turno ? 'Turno ' + c.turno : 'Sem turno',
            department: c.setor || 'Sem setor',
            status: c.emPausa ? 'coffee_break' : 'active',
            lastPontoType: pausa ? 'Em pausa desde' : (c.codigoAtivo ? 'Código ativo' : null),
            lastPontoTime: pausa ? pausa.inicioLocal : null
          };
        });

        // Alerta é a pausa que já passou do teto -- o mesmo critério do app.
        // O nome vem no campo nome: a consulta traz col.nome sem alias.
        state.alerts = (ativas.pausas || [])
          .filter(p => p.excedeuLimite)
          .map(p => ({
            severity: 'warning',
            title: p.nome + ' acima do limite',
            message: 'Saiu às ' + p.inicioLocal + ' · ' + segundosParaRelogio(p.tempoContadoSegundos) + ' contados'
          }));

        state.history = (ativas.pausas || []).map(p => ({
          collaboratorName: p.nome,
          type: 'pausa_cafe',
          timestamp: p.inicioLocal,
          source: p.setor || 'totem',
          note: p.foraHorario ? 'Fora do horário' : null
        }));

        renderTeamList();
        renderHistoryTable();
        renderAlerts();
        renderAdminList();
        updateSummaryStats();
        refreshUsers();
        refreshCodes();
        lucide.createIcons();
      } catch (err) {
        if (err.message !== 'unauthenticated') {
          console.error(err);
          showToast('Erro', 'Não foi possível carregar os dados: ' + err.message, 'error');
        }
      }
    }

    function updateSummaryStats() {
      // O resumo só chega para ADMIN; para Supervisor os números saem do que já
      // foi carregado, em vez de mostrar zeros.
      const r = state.resumo || {};
      const el = (id, v) => { const n = document.getElementById(id); if (n) n.textContent = v; };
      const total = r.colaboradoresAtivos ?? state.collaborators.length;
      const emPausa = r.pausasAbertas ?? state.collaborators.filter(c => c.status === 'coffee_break').length;
      el('stat-total', total);
      el('stat-active', Math.max(0, total - emPausa));
      el('stat-coffee', emPausa);
      el('stat-off', r.codigosPendentes ?? 0);
    }

    function renderTeamList() {
      const container = document.getElementById('team-list');
      if (!container) return;

      container.innerHTML = state.collaborators.map(c => {
        const statusMap = {
          active: { label: 'Em Turno', badgeClass: 'bg-emerald-50 text-emerald-700 border-emerald-200' },
          coffee_break: { label: 'Pausa Café', badgeClass: 'bg-amber-50 text-amber-700 border-amber-200' },
          off_duty: { label: 'Fora de Turno', badgeClass: 'bg-stone-100 text-stone-600 border-stone-200' }
        };
        const st = statusMap[c.status] || statusMap.off_duty;

        return \`
          <div class="px-4 md:px-6 py-4 hover:bg-stone-50/60 transition-colors">
            <div class="flex items-center justify-between gap-3 flex-wrap cursor-pointer" onclick="toggleHistorico('\${c.id}', '\${c.name.replace(/'/g, "\\\\'")}')">
              <div class="flex items-center space-x-3 min-w-0">
                <div class="w-10 h-10 rounded-xl bg-stone-100 border border-stone-200 text-coffee-900 font-bold flex items-center justify-center text-sm shrink-0">
                  \${c.name.slice(0, 2).toUpperCase()}
                </div>
                <div class="min-w-0">
                  <h4 class="font-bold text-coffee-950 text-sm truncate">\${c.name}</h4>
                  <p class="text-xs text-stone-500 truncate">\${c.role} • \${c.department}</p>
                </div>
              </div>
              <div class="flex items-center space-x-3 shrink-0">
                <div class="text-right hidden sm:block">
                  <div class="text-xs font-semibold text-stone-700">\${c.lastPontoType || 'Sem registro'}</div>
                  <div class="text-[11px] text-stone-400 font-mono">\${c.lastPontoTime || '--'}</div>
                </div>
                <span class="px-3 py-1 rounded-full text-xs font-semibold border \${st.badgeClass}">
                  \${st.label}
                </span>
                <i data-lucide="chevron-down" class="w-4 h-4 text-stone-400"></i>
              </div>
            </div>
            <div id="hist-\${c.id}" class="hidden"></div>
          </div>
        \`;
      }).join('');
    }

    function renderAlerts() {
      const container = document.getElementById('alerts-list');
      if (!container) return;

      if (state.alerts.length === 0) {
        container.innerHTML = '<p class="text-xs text-stone-400">Nenhum alerta operacional no momento.</p>';
        return;
      }

      container.innerHTML = state.alerts.map(a => {
        const borderClass = a.severity === 'warning' ? 'border-amber-200 bg-amber-50/50' : 'border-stone-200 bg-stone-50';
        return \`
          <div class="p-4 rounded-xl border \${borderClass} flex items-start space-x-3">
            <i data-lucide="\${a.severity === 'warning' ? 'alert-triangle' : 'info'}" class="w-5 h-5 \${a.severity === 'warning' ? 'text-amber-600' : 'text-stone-600'} mt-0.5"></i>
            <div>
              <h5 class="text-sm font-bold text-coffee-950">\${a.title}</h5>
              <p class="text-xs text-stone-600 mt-0.5">\${a.message}</p>
              <span class="text-[10px] text-stone-400 font-mono mt-1 block">\${a.time}</span>
            </div>
          </div>
        \`;
      }).join('');
      lucide.createIcons();
    }

    function renderHistoryTable() {
      const tbody = document.getElementById('history-table-body');
      const cards = document.getElementById('history-cards');
      if (!tbody) return;

      const typeLabels = {
        entrada: { label: 'Entrada', class: 'text-emerald-700 bg-emerald-50 border-emerald-200' },
        pausa_cafe: { label: 'Pausa Café', class: 'text-amber-700 bg-amber-50 border-amber-200' },
        retorno_cafe: { label: 'Retorno Café', class: 'text-amber-800 bg-amber-100 border-amber-300' },
        saida: { label: 'Saída', class: 'text-stone-700 bg-stone-100 border-stone-200' }
      };

      tbody.innerHTML = state.history.slice().reverse().map(item => {
        const tag = typeLabels[item.type] || { label: item.type, class: 'text-stone-600 bg-stone-50 border-stone-200' };
        return \`
          <tr class="hover:bg-stone-50/80 transition-colors">
            <td class="px-6 py-4 font-semibold text-coffee-950">\${item.collaboratorName}</td>
            <td class="px-6 py-4">
              <span class="px-2.5 py-1 rounded-full text-xs font-semibold border \${tag.class}">\${tag.label}</span>
            </td>
            <td class="px-6 py-4 font-mono text-xs text-stone-600">\${item.timestamp}</td>
            <td class="px-6 py-4 text-xs font-medium uppercase text-stone-500">\${item.source}</td>
            <td class="px-6 py-4 text-xs text-stone-500">\${item.note || '-'}</td>
          </tr>
        \`;
      }).join('');

      if (cards) {
        const linhas = state.history.slice().reverse();
        cards.innerHTML = linhas.length === 0
          ? '<p class="px-5 py-8 text-xs text-stone-400 text-center">Nenhum registro no período.</p>'
          : linhas.map(item => {
              const tag = typeLabels[item.type] || { label: item.type, class: 'text-stone-600 bg-stone-50 border-stone-200' };
              return \`
                <div class="px-5 py-4">
                  <div class="flex items-start justify-between gap-3">
                    <h4 class="text-sm font-bold text-coffee-950 min-w-0 truncate">\${item.collaboratorName}</h4>
                    <span class="shrink-0 font-mono text-xs text-stone-500">\${item.timestamp}</span>
                  </div>
                  <div class="flex items-center flex-wrap gap-2 mt-2">
                    <span class="px-2.5 py-1 rounded-full text-xs font-semibold border \${tag.class}">\${tag.label}</span>
                    <span class="text-[11px] uppercase font-semibold text-stone-400">\${item.source}</span>
                  </div>
                  \${item.note ? '<p class="text-xs text-stone-500 mt-2">' + item.note + '</p>' : ''}
                </div>
              \`;
            }).join('');
      }
    }

    function renderAdminList() {
      const container = document.getElementById('admin-collaborator-list');
      if (!container) return;

      // Colaborador não tem PIN neste modelo -- o que aparece é o estado da
      // pausa. O acesso ao café vem do código de 6 caracteres do Supervisor.
      container.innerHTML = state.collaborators.map(c => \`
        <div class="py-3 flex items-center justify-between">
          <div>
            <h4 class="text-sm font-bold text-coffee-950">\${c.name}</h4>
            <p class="text-xs text-stone-500">\${c.role} • \${c.department}</p>
          </div>
          <div class="flex items-center space-x-3">
            <span class="text-xs px-2 py-1 rounded-full border font-semibold \${c.status === 'coffee_break' ? 'bg-amber-50 text-amber-700 border-amber-200' : 'bg-emerald-50 text-emerald-700 border-emerald-200'}">
              \${c.status === 'coffee_break' ? 'Em pausa' : 'Disponível'}
            </span>
          </div>
        </div>
      \`).join('');
    }

    // ---- Histórico de um colaborador ---------------------------------------
    // Abre no lugar, dentro da própria linha da pessoa, em vez de trocar de
    // tela: quem consulta está comparando com o resto da equipe e perderia o
    // contexto se fosse levado para outro lugar.
    async function toggleHistorico(id, nome) {
      const alvo = document.getElementById('hist-' + id);
      if (!alvo) return;
      if (!alvo.classList.contains('hidden')) {
        alvo.classList.add('hidden');
        return;
      }
      alvo.classList.remove('hidden');
      alvo.innerHTML = '<p class="text-xs text-stone-400 py-3">Carregando…</p>';
      try {
        const data = await apiFetch('/gestao/colaboradores/' + id + '/historico');
        const r = data.resumo || {};
        const pausas = data.pausas || [];
        const linhas = pausas.slice(0, 10).map(p => \`
          <div class="flex items-center justify-between py-2 border-t border-stone-100 first:border-0">
            <span class="text-xs text-stone-600">\${p.inicioLocal || p.data || ''} \${p.periodo ? '· ' + p.periodo.toLowerCase() : ''}</span>
            <span class="font-mono text-xs \${p.excedeuLimite ? 'text-amber-700 font-bold' : 'text-stone-500'}">
              \${p.tempoContadoSegundos != null ? segundosParaRelogio(p.tempoContadoSegundos) : ''}
            </span>
          </div>\`).join('');
        alvo.innerHTML = \`
          <div class="mt-3 p-4 rounded-xl bg-stone-50 border border-stone-200">
            <div class="flex flex-wrap gap-4 mb-2">
              <div><p class="text-[10px] uppercase font-semibold text-stone-500">Pausas</p><p class="text-lg font-extrabold text-coffee-900">\${r.totalPausas ?? pausas.length}</p></div>
              <div><p class="text-[10px] uppercase font-semibold text-stone-500">Acima do teto</p><p class="text-lg font-extrabold text-amber-700">\${r.acimaLimite ?? 0}</p></div>
              <div><p class="text-[10px] uppercase font-semibold text-stone-500">Média</p><p class="text-lg font-extrabold text-emerald-700">\${r.mediaSegundos ? segundosParaRelogio(r.mediaSegundos) : '—'}</p></div>
            </div>
            \${linhas || '<p class="text-xs text-stone-400 py-2">Sem pausas registradas.</p>'}
          </div>\`;
      } catch (err) {
        alvo.innerHTML = '<p class="text-xs text-red-600 py-3">Não foi possível carregar: ' + err.message + '</p>';
      }
    }

    // ---- Ações sobre uma conta de acesso -----------------------------------
    async function acaoUsuario(metodo, caminho, corpo, sucesso) {
      const res = await fetch(API_BASE + caminho, {
        method: metodo,
        headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + getToken() },
        body: corpo ? JSON.stringify(corpo) : undefined
      });
      const data = await res.json().catch(() => ({}));
      if (res.ok) {
        showToast('Pronto', typeof sucesso === 'function' ? sucesso(data) : sucesso, 'success');
        await refreshUsers();
        return;
      }
      showToast('Erro', data.erro || 'A operação não foi aceita.', 'error');
    }

    function mudarPerfil(id, atualAdmin, turnoAtual) {
      const novo = confirm(
        atualAdmin
          ? 'Rebaixar esta conta para Supervisor?'
          : 'Promover esta conta a Administrador?'
      );
      if (!novo) return;
      if (atualAdmin) {
        const turno = prompt('Turno do Supervisor (A, B, C ou D):', turnoAtual || 'A');
        if (turno === null) return;
        if (!['A', 'B', 'C', 'D'].includes(turno.trim().toUpperCase())) {
          showToast('Turno inválido', 'Use A, B, C ou D.', 'error');
          return;
        }
        acaoUsuario('PUT', '/admin/usuarios/' + id + '/perfil',
          { perfil: 'SUPERVISOR', turno: turno.trim().toUpperCase() }, 'Perfil alterado.');
      } else {
        acaoUsuario('PUT', '/admin/usuarios/' + id + '/perfil', { perfil: 'ADMIN' }, 'Perfil alterado.');
      }
    }

    function redefinirSenha(id) {
      const nova = prompt('Nova senha (mínimo 10 caracteres). Em branco, o sistema gera uma provisória:');
      if (nova === null) return;
      const corpo = nova.trim() ? { novaSenha: nova.trim() } : {};
      if (nova.trim() && nova.trim().length < 10) {
        showToast('Senha curta', 'Use ao menos 10 caracteres.', 'error');
        return;
      }
      acaoUsuario('PUT', '/admin/usuarios/' + id + '/senha', corpo,
        (d) => d.senhaTemporaria ? 'Senha provisória: ' + d.senhaTemporaria : 'Senha redefinida.');
    }

    function bloquearUsuario(id, bloqueada) {
      const acao = bloqueada ? 'reativar' : 'bloquear';
      if (!confirm(bloqueada ? 'Reativar esta conta?' : 'Bloquear o acesso desta conta?')) return;
      acaoUsuario('POST', '/admin/usuarios/' + id + '/' + acao, null,
        bloqueada ? 'Conta reativada.' : 'Conta bloqueada.');
    }

    function excluirUsuario(id, nome) {
      if (!confirm('Excluir a conta de "' + nome + '"? Não há como desfazer.')) return;
      acaoUsuario('POST', '/admin/usuarios/' + id + '/excluir', null, 'Conta excluída.');
    }

    // ---- Relatórios --------------------------------------------------------
    function periodoRelatorio() {
      const hoje = new Date().toISOString().slice(0, 10);
      const inicio = document.getElementById('rep-inicio');
      const fim = document.getElementById('rep-fim');
      if (inicio && !inicio.value) {
        const d = new Date(); d.setDate(d.getDate() - 29);
        inicio.value = d.toISOString().slice(0, 10);
      }
      if (fim && !fim.value) fim.value = hoje;
      return { inicio: inicio ? inicio.value : hoje, fim: fim ? fim.value : hoje };
    }

    async function refreshReports() {
      const stats = document.getElementById('reports-stats');
      const delays = document.getElementById('reports-delays');
      if (!stats) return;
      const { inicio, fim } = periodoRelatorio();
      try {
        const data = await apiFetch('/supervisor/relatorios/resumo?inicio=' + inicio + '&fim=' + fim);
        const r = data.resumo || {};
        const media = r.mediaSegundos ? segundosParaRelogio(r.mediaSegundos) : '—';
        const tile = (rot, val, cor) => \`
          <div class="bg-white rounded-2xl border border-stone-200 p-3 md:p-5 shadow-sm">
            <p class="text-[10px] md:text-xs font-semibold text-stone-500 uppercase tracking-wider leading-tight">\${rot}</p>
            <p class="text-2xl md:text-3xl font-extrabold \${cor} mt-1">\${val}</p>
          </div>\`;
        stats.innerHTML =
          tile('Pausas', r.totalPausas ?? 0, 'text-coffee-900') +
          tile('Acima do limite', r.acimaLimite ?? 0, 'text-amber-700') +
          tile('Fora do horário', r.foraHorario ?? 0, 'text-stone-700') +
          tile('Média', media, 'text-emerald-700');

        const top = data.maioresAtrasos || [];
        delays.innerHTML = top.length === 0
          ? '<p class="px-5 py-8 text-xs text-stone-400 text-center">Nenhum atraso no período.</p>'
          : top.map(d => \`
              <div class="px-5 md:px-6 py-3 flex items-center justify-between gap-3">
                <div class="min-w-0">
                  <h4 class="text-sm font-bold text-coffee-950 truncate">\${d.nome || d.colaboradorNome || '—'}</h4>
                  <p class="text-xs text-stone-500">\${d.data || d.dia || ''}</p>
                </div>
                <span class="shrink-0 font-mono text-xs font-bold text-amber-700">
                  \${d.tempoContadoSegundos != null ? segundosParaRelogio(d.tempoContadoSegundos) : ''}
                </span>
              </div>
            \`).join('');
      } catch (err) {
        if (err.message !== 'unauthenticated') {
          stats.innerHTML = '';
          delays.innerHTML = '<p class="px-5 py-8 text-xs text-red-600 text-center">' + err.message + '</p>';
        }
      }
    }

    // O CSV vem por rota autenticada: o navegador não manda o Bearer num link,
    // então busca-se o corpo e entrega-se como arquivo local.
    async function baixarCsv() {
      const { inicio, fim } = periodoRelatorio();
      try {
        const res = await fetch(API_BASE + '/supervisor/relatorios/csv?inicio=' + inicio + '&fim=' + fim, {
          headers: { 'Authorization': 'Bearer ' + getToken() }
        });
        if (!res.ok) throw new Error('HTTP ' + res.status);
        const blob = await res.blob();
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'pontocafe-' + inicio + '-a-' + fim + '.csv';
        document.body.appendChild(a); a.click(); a.remove();
        URL.revokeObjectURL(url);
        showToast('CSV gerado', 'O arquivo foi baixado.', 'success');
      } catch (err) {
        showToast('Erro', 'Não foi possível gerar o CSV: ' + err.message, 'error');
      }
    }

    // ---- Regras de café ----------------------------------------------------
    async function refreshRules() {
      const box = document.getElementById('rules-list');
      if (!box) return;
      try {
        const data = await apiFetch('/admin/regras-cafe');
        const regras = data.regras || [];
        box.innerHTML = regras.map(r => {
          const minutos = Math.round((r.limiteSegundos ?? r.limite_segundos ?? 0) / 60);
          const nome = r.periodo === 'MANHA' ? 'Manhã' : 'Tarde';
          return \`
            <div class="p-4 rounded-xl bg-stone-50 border border-stone-200">
              <div class="flex items-center justify-between gap-3 flex-wrap">
                <div>
                  <h4 class="text-sm font-bold text-coffee-950">Período da \${nome}</h4>
                  <p class="text-xs text-stone-500">Janela \${r.inicio} – \${r.fim} · teto \${minutos} min</p>
                </div>
                <button onclick="editarRegra('\${r.periodo}', '\${r.inicio}', '\${r.fim}', \${minutos})"
                  class="px-3 py-1.5 rounded-lg border border-stone-300 text-stone-600 text-xs font-semibold hover:bg-white">Editar</button>
              </div>
            </div>\`;
        }).join('') || '<p class="text-xs text-stone-400 py-3">Nenhuma regra configurada.</p>';
      } catch (err) {
        if (err.message !== 'unauthenticated') {
          box.innerHTML = '<p class="text-xs text-stone-400 py-3">Apenas o Administrador pode ver as regras.</p>';
        }
      }
    }

    async function editarRegra(periodo, inicio, fim, minutos) {
      const novoInicio = prompt('Início da janela (HH:MM):', inicio);
      if (novoInicio === null) return;
      const novoFim = prompt('Fim da janela (HH:MM):', fim);
      if (novoFim === null) return;
      const novoLimite = prompt('Teto da pausa, em minutos:', String(minutos));
      if (novoLimite === null) return;
      const min = parseInt(novoLimite, 10);
      if (!/^\\d{2}:\\d{2}$/.test(novoInicio) || !/^\\d{2}:\\d{2}$/.test(novoFim)) {
        showToast('Horário inválido', 'Use o formato HH:MM.', 'error');
        return;
      }
      if (!(min >= 1 && min <= 120)) {
        showToast('Limite inválido', 'O teto vai de 1 a 120 minutos.', 'error');
        return;
      }
      const res = await fetch(API_BASE + '/admin/regras-cafe/' + periodo, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + getToken() },
        body: JSON.stringify({ inicio: novoInicio, fim: novoFim, limiteMinutos: min })
      });
      const data = await res.json().catch(() => ({}));
      if (res.ok) {
        showToast('Regra atualizada', 'Passa a valer para as próximas pausas.', 'success');
        await refreshRules();
      } else {
        showToast('Erro', data.erro || 'Não foi possível salvar a regra.', 'error');
      }
    }

    // ---- Diagnóstico -------------------------------------------------------
    async function refreshDiagnostics() {
      const grid = document.getElementById('diag-grid');
      if (!grid) return;
      try {
        const d = await apiFetch('/admin/diagnostico');
        const op = d.operacao || {};
        const cel = (rot, val, cor) => \`
          <div class="p-3 rounded-xl bg-stone-50 border border-stone-200 text-center">
            <p class="text-[10px] font-semibold text-stone-500 uppercase tracking-wider leading-tight">\${rot}</p>
            <p class="text-lg font-extrabold \${cor} mt-1">\${val}</p>
          </div>\`;
        grid.innerHTML =
          cel('Banco', (d.banco && d.banco.status === 'ok') ? 'OK' : 'Falha', (d.banco && d.banco.status === 'ok') ? 'text-emerald-700' : 'text-red-700') +
          cel('Latência', (d.banco && d.banco.latenciaMs != null) ? d.banco.latenciaMs + ' ms' : '—', 'text-coffee-900') +
          cel('Sessões', op.sessoesAtivas ?? '—', 'text-stone-700') +
          cel('Colaboradores', op.colaboradoresAtivos ?? '—', 'text-stone-700') +
          cel('Dispositivos', op.dispositivosAtivos ?? '—', 'text-stone-700') +
          cel('Pausas abertas', op.pausasAbertas ?? '—', 'text-amber-700');
      } catch (err) {
        if (err.message !== 'unauthenticated') {
          grid.innerHTML = '<p class="text-xs text-stone-400 py-3 col-span-full">Apenas o Administrador pode ver o diagnóstico.</p>';
        }
      }
    }

    // ---- Dispositivos ------------------------------------------------------
    // Só o Administrador enxerga: /admin/devices exige esse perfil. Para
    // Supervisor a aba explica em vez de mostrar erro.
    async function refreshDevices() {
      const lista = document.getElementById('devices-list');
      const stats = document.getElementById('devices-stats');
      if (!lista) return;
      try {
        const data = await apiFetch('/admin/devices');
        const devices = data.dispositivos || [];

        const ativos = devices.filter(d => d.ativo).length;
        const semPin = devices.filter(d => !d.pinConfigurado).length;
        const aguardando = devices.filter(d => d.aguardandoAtivacao).length;
        const tile = (rotulo, valor, cor) => \`
          <div class="bg-white rounded-2xl border border-stone-200 p-3 md:p-5 shadow-sm">
            <p class="text-[10px] md:text-xs font-semibold text-stone-500 uppercase tracking-wider leading-tight">\${rotulo}</p>
            <p class="text-2xl md:text-3xl font-extrabold \${cor} mt-1">\${valor}</p>
          </div>\`;
        stats.innerHTML =
          tile('Ativos', ativos, 'text-emerald-700') +
          tile('Sem PIN', semPin, 'text-amber-700') +
          tile('Aguardando ativação', aguardando, 'text-stone-700');

        lista.innerHTML = devices.length === 0
          ? '<p class="px-6 py-8 text-xs text-stone-400 text-center">Nenhum aparelho cadastrado.</p>'
          : devices.map(d => \`
              <div class="px-4 md:px-6 pt-4 pb-2 flex flex-wrap items-center justify-between gap-3">
                <div class="flex items-center space-x-3 min-w-0">
                  <div class="w-10 h-10 rounded-xl bg-stone-100 border border-stone-200 flex items-center justify-center text-coffee-900 shrink-0">
                    <i data-lucide="smartphone" class="w-5 h-5"></i>
                  </div>
                  <div class="min-w-0">
                    <h4 class="text-sm font-bold text-coffee-950 truncate">\${d.nome}</h4>
                    <p class="text-xs text-stone-500 truncate">
                      \${d.ultimoAcessoEm ? 'Último acesso: ' + d.ultimoAcessoEm.slice(0, 16).replace('T', ' ') : 'Sem acesso registrado'}
                    </p>
                  </div>
                </div>
                <div class="flex items-center space-x-2 shrink-0">
                  \${!d.pinConfigurado ? '<span class="px-2.5 py-1 rounded-full text-xs font-semibold border bg-amber-50 text-amber-700 border-amber-200">Sem PIN</span>' : ''}
                  <span class="px-2.5 py-1 rounded-full text-xs font-semibold border \${d.ativo ? 'bg-emerald-50 text-emerald-700 border-emerald-200' : 'bg-stone-100 text-stone-500 border-stone-200'}">
                    \${d.aguardandoAtivacao ? 'Aguardando ativação' : (d.ativo ? 'Operacional' : 'Bloqueado')}
                  </span>
                </div>
              </div>
              <div class="px-4 md:px-6 pb-4 flex flex-wrap gap-2">
                <button onclick="renomearDispositivo('\${d.id}', '\${(d.nome || '').replace(/'/g, "\\\\'")}')"
                  class="px-3 py-1.5 rounded-lg border border-stone-300 text-stone-600 text-xs font-semibold hover:bg-stone-50">Renomear</button>
                <button onclick="definirPinDispositivo('\${d.id}')"
                  class="px-3 py-1.5 rounded-lg border border-stone-300 text-stone-600 text-xs font-semibold hover:bg-stone-50">\${d.pinConfigurado ? 'Trocar PIN' : 'Definir PIN'}</button>
                <button onclick="novoTokenDispositivo('\${d.id}', '\${(d.nome || '').replace(/'/g, "\\\\'")}')"
                  class="px-3 py-1.5 rounded-lg border border-stone-300 text-stone-600 text-xs font-semibold hover:bg-stone-50">Novo código</button>
                \${d.ativo ? \`<button onclick="desativarDispositivo('\${d.id}')"
                  class="px-3 py-1.5 rounded-lg border border-amber-300 text-amber-700 text-xs font-semibold hover:bg-amber-50">Bloquear acesso</button>\` : ''}
                <button onclick="excluirDispositivo('\${d.id}', '\${(d.nome || '').replace(/'/g, "\\\\'")}')"
                  class="px-3 py-1.5 rounded-lg border border-red-300 text-red-700 text-xs font-semibold hover:bg-red-50">Excluir</button>
              </div>
            \`).join('');
        lucide.createIcons();
      } catch (err) {
        if (err.message !== 'unauthenticated') {
          stats.innerHTML = '';
          lista.innerHTML = '<p class="px-6 py-8 text-xs text-stone-400 text-center">Apenas o Administrador pode ver os dispositivos.</p>';
        }
      }
    }

    // Ações sobre um aparelho. Bloquear e excluir são destrutivas: cortam o
    // registro de ponto naquele terminal, por isso pedem confirmação explícita.
    async function acaoDispositivo(metodo, caminho, corpo, sucesso) {
      const res = await fetch(API_BASE + caminho, {
        method: metodo,
        headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + getToken() },
        body: corpo ? JSON.stringify(corpo) : undefined
      });
      const data = await res.json().catch(() => ({}));
      if (res.ok) {
        showToast('Pronto', typeof sucesso === 'function' ? sucesso(data) : sucesso, 'success');
        await refreshDevices();
        return true;
      }
      showToast('Erro', data.erro || 'A operação não foi aceita pelo servidor.', 'error');
      return false;
    }

    // Cadastro de aparelho.
    //
    // Sem isto o painel sabia listar, renomear e excluir dispositivos, mas nao
    // sabia criar nenhum -- e o totem ficava sem nada para digitar. O servidor
    // devolve o codigo de ativacao de 10 caracteres uma unica vez, por isso ele
    // aparece grande, copiavel, e o modal so fecha com "Ja anotei".
    let idempotenciaDispositivo = null;

    function novaChaveIdempotencia() {
      if (window.crypto && crypto.randomUUID) return crypto.randomUUID();
      return 'dev-' + Date.now() + '-' + Math.random().toString(36).slice(2, 12);
    }

    function abrirCadastroDispositivo() {
      idempotenciaDispositivo = novaChaveIdempotencia();
      document.getElementById('device-nome').value = '';
      document.getElementById('device-pin').value = '';
      document.getElementById('device-modal-form').classList.remove('hidden');
      document.getElementById('device-modal-token').classList.add('hidden');
      const modal = document.getElementById('device-modal');
      modal.classList.remove('hidden');
      modal.classList.add('flex');
      lucide.createIcons();
      document.getElementById('device-nome').focus();
    }

    function fecharCadastroDispositivo() {
      const modal = document.getElementById('device-modal');
      modal.classList.add('hidden');
      modal.classList.remove('flex');
    }

    function mostrarTokenDispositivo(nome, token, subtitulo) {
      document.getElementById('device-modal-form').classList.add('hidden');
      document.getElementById('device-token-nome').textContent = subtitulo || nome;
      document.getElementById('device-token-valor').textContent = token;
      document.getElementById('device-modal-token').classList.remove('hidden');
      const modal = document.getElementById('device-modal');
      modal.classList.remove('hidden');
      modal.classList.add('flex');
      lucide.createIcons();
    }

    function copiarTokenDispositivo() {
      const valor = document.getElementById('device-token-valor').textContent;
      if (navigator.clipboard) {
        navigator.clipboard.writeText(valor)
          .then(() => showToast('Copiado', 'O código está na área de transferência.', 'success'))
          .catch(() => showToast('Não deu', 'Copie o código à mão.', 'error'));
      } else {
        showToast('Não deu', 'Este navegador não deixa copiar. Anote o código à mão.', 'error');
      }
    }

    async function salvarDispositivo() {
      const nome = document.getElementById('device-nome').value.trim();
      const pin = document.getElementById('device-pin').value.trim();
      if (nome.length < 2) {
        showToast('Nome inválido', 'Use ao menos 2 caracteres.', 'error');
        return;
      }
      if (pin && !/^\\d{4,12}$/.test(pin)) {
        showToast('PIN inválido', 'O PIN de desbloqueio tem de 4 a 12 números.', 'error');
        return;
      }

      const botao = document.getElementById('device-salvar');
      botao.disabled = true;
      botao.textContent = 'Cadastrando...';
      try {
        const corpo = pin ? { nome: nome, pin: pin } : { nome: nome };
        // A chave de idempotencia nasce ao abrir o modal: se a resposta se
        // perder e a pessoa carregar outra vez, o servidor devolve o mesmo
        // aparelho em vez de criar um segundo. Se ela mudou os dados no meio,
        // o servidor recusa a chave e nos geramos uma nova, uma unica vez.
        let res = await fetch(API_BASE + '/admin/device-activation', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'Authorization': 'Bearer ' + getToken(),
            'Idempotency-Key': idempotenciaDispositivo
          },
          body: JSON.stringify(corpo)
        });
        let data = await res.json().catch(() => ({}));

        if (res.status === 409 && data.codigo === 'IDEMPOTENCY_KEY_REUSED') {
          idempotenciaDispositivo = novaChaveIdempotencia();
          res = await fetch(API_BASE + '/admin/device-activation', {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json',
              'Authorization': 'Bearer ' + getToken(),
              'Idempotency-Key': idempotenciaDispositivo
            },
            body: JSON.stringify(corpo)
          });
          data = await res.json().catch(() => ({}));
        }

        if (!res.ok) {
          showToast('Erro', data.erro || 'O servidor não aceitou o cadastro.', 'error');
          return;
        }

        mostrarTokenDispositivo(
          data.nome || nome,
          data.token,
          'Digite este código no aparelho, em Totem / Ponto.'
        );
        await refreshDevices();
      } catch (err) {
        showToast('Erro', 'Não foi possível falar com o servidor.', 'error');
      } finally {
        botao.disabled = false;
        botao.textContent = 'Cadastrar';
      }
    }

    function renomearDispositivo(id, atual) {
      const nome = prompt('Novo nome do aparelho:', atual || '');
      if (nome === null) return;
      if (nome.trim().length < 2) {
        showToast('Nome inválido', 'Use ao menos 2 caracteres.', 'error');
        return;
      }
      acaoDispositivo('PUT', '/admin/devices/' + id + '/nome', { nome: nome.trim() }, 'Nome atualizado.');
    }

    function definirPinDispositivo(id) {
      const pin = prompt('PIN de desbloqueio do terminal (4 a 12 números):');
      if (pin === null) return;
      if (!/^\\d{4,12}$/.test(pin.trim())) {
        showToast('PIN inválido', 'Use de 4 a 12 números.', 'error');
        return;
      }
      acaoDispositivo('PUT', '/admin/devices/' + id + '/unlock-pin', { pin: pin.trim() }, 'PIN definido.');
    }

    // O token novo tambem so e mostrado uma vez -- num toast que some em quatro
    // segundos nao da para copiar 10 caracteres com maiusculas e minusculas.
    async function novoTokenDispositivo(id, nome) {
      if (!confirm('Gerar novo código de ativação? O aparelho para de registrar ponto até ser ativado outra vez com o código novo.')) return;
      try {
        const res = await fetch(API_BASE + '/admin/devices/' + id + '/novo-token', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + getToken() }
        });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) {
          showToast('Erro', data.erro || 'O servidor não aceitou a rotação.', 'error');
          return;
        }
        await refreshDevices();
        if (data.token) {
          mostrarTokenDispositivo(nome || 'Aparelho', data.token, 'O código anterior foi revogado. Digite este no aparelho.');
        } else {
          showToast('Pronto', 'Código rotacionado.', 'success');
        }
      } catch (err) {
        showToast('Erro', 'Não foi possível falar com o servidor.', 'error');
      }
    }

    function desativarDispositivo(id) {
      if (!confirm('Bloquear o acesso deste aparelho? Ele deixa de registrar ponto até ser reativado.')) return;
      acaoDispositivo('POST', '/admin/devices/' + id + '/desativar', null, 'Acesso bloqueado.');
    }

    function excluirDispositivo(id, nome) {
      if (!confirm('Excluir "' + nome + '" definitivamente? A credencial é revogada e não há como desfazer.')) return;
      acaoDispositivo('POST', '/admin/devices/' + id + '/excluir', null, 'Aparelho excluído.');
    }

    // ---- Auditoria ---------------------------------------------------------
    async function refreshAudit() {
      const lista = document.getElementById('audit-list');
      if (!lista) return;
      try {
        const data = await apiFetch('/admin/auditoria');
        const eventos = data.eventos || [];
        lista.innerHTML = eventos.length === 0
          ? '<p class="px-6 py-8 text-xs text-stone-400 text-center">Nenhum evento no período.</p>'
          : eventos.map(e => {
              const alvo = e.detalhes && e.detalhes.nome ? e.detalhes.nome : (e.entidade || '');
              return \`
                <div class="px-6 py-4 flex items-start justify-between gap-4">
                  <div class="min-w-0">
                    <h4 class="text-sm font-bold text-coffee-950">\${e.acao}</h4>
                    <p class="text-xs text-stone-500 truncate">\${alvo}</p>
                    <p class="text-[11px] text-stone-400 mt-1">\${e.atorNome} · \${(e.atorTipo || '').toLowerCase()}</p>
                  </div>
                  <span class="font-mono text-xs text-stone-500 shrink-0">\${e.criadoLocal}</span>
                </div>
              \`;
            }).join('');
      } catch (err) {
        if (err.message !== 'unauthenticated') {
          lista.innerHTML = '<p class="px-6 py-8 text-xs text-stone-400 text-center">Apenas o Administrador pode ver a auditoria.</p>';
        }
      }
    }

    // ---- Códigos de café ---------------------------------------------------
    // O código é de 6 caracteres, letras e números, e vale uma vez só. O
    // alfabeto exclui I, L, O e U de propósito: quem digita está de pé no
    // corredor e confundiria I com 1 e O com 0.
    //
    // As rotas existem sob /admin e sob /supervisor. Usamos /supervisor porque
    // aceita os dois perfis -- um Supervisor perderia a tela em /admin.
    const ESTADO_CODIGO = {
      AGUARDANDO_SAIDA: { label: 'Aguardando saída', cls: 'bg-amber-50 text-amber-700 border-amber-200' },
      EM_PAUSA: { label: 'No café', cls: 'bg-emerald-50 text-emerald-700 border-emerald-200' },
      EXPIRADO: { label: 'Expirado', cls: 'bg-stone-100 text-stone-500 border-stone-200' }
    };

    async function refreshCodes() {
      const lista = document.getElementById('codes-list');
      const pessoas = document.getElementById('codes-people-list');
      if (!lista) return;
      try {
        const data = await apiFetch('/supervisor/codigos');
        state.codes = data.codigos || [];

        const vivos = state.codes.filter(c => c.estado !== 'EXPIRADO');
        document.getElementById('codes-count').textContent =
          vivos.length === 0 ? 'nenhum ativo' : vivos.length + ' ativo(s)';

        lista.innerHTML = vivos.length === 0
          ? '<p class="px-6 py-8 text-xs text-stone-400 text-center">Nenhum código vivo agora.</p>'
          : vivos.map(c => {
              const st = ESTADO_CODIGO[c.estado] || ESTADO_CODIGO.EXPIRADO;
              const prazo = c.estado === 'EM_PAUSA'
                ? 'Válido para o retorno, sem prazo'
                : 'Expira em ' + segundosParaRelogio(c.expiraEmSegundos);
              return \`
                <div class="px-6 py-4 flex items-center justify-between gap-4">
                  <div class="min-w-0">
                    <div class="flex items-center space-x-2">
                      <span class="font-mono text-lg font-extrabold text-coffee-900 tracking-widest">\${c.codigoFormatado}</span>
                      <span class="px-2.5 py-1 rounded-full text-xs font-semibold border \${st.cls}">\${st.label}</span>
                    </div>
                    <p class="text-xs text-stone-500 mt-0.5 truncate">\${c.nome} · \${prazo}</p>
                  </div>
                  \${c.estado === 'AGUARDANDO_SAIDA' ? \`
                    <button onclick="cancelarCodigo('\${c.colaboradorId}')"
                      class="shrink-0 px-3 py-2 rounded-xl border border-stone-300 text-stone-600 text-xs font-semibold hover:bg-stone-50">
                      Cancelar
                    </button>\` : ''}
                </div>
              \`;
            }).join('');

        // Quem já tem código vivo não aparece para emitir outro: dois códigos
        // ao mesmo tempo deixariam em aberto qual deles fecha a pausa.
        const comCodigo = {};
        vivos.forEach(c => { comCodigo[c.colaboradorId] = true; });
        const livres = state.collaborators.filter(p => !comCodigo[p.id]);

        pessoas.innerHTML = livres.length === 0
          ? '<p class="px-6 py-8 text-xs text-stone-400 text-center">Todo mundo já tem código vivo.</p>'
          : livres.map(p => \`
              <div class="px-6 py-3 flex items-center justify-between gap-4">
                <div class="min-w-0">
                  <h4 class="text-sm font-bold text-coffee-950 truncate">\${p.name}</h4>
                  <p class="text-xs text-stone-500 truncate">\${p.role} • \${p.department}</p>
                </div>
                <button onclick="emitirCodigo('\${p.id}')"
                  class="shrink-0 flex items-center space-x-1.5 px-3 py-2 rounded-xl bg-amberAccent text-white text-xs font-semibold shadow hover:opacity-95">
                  <i data-lucide="coffee" class="w-4 h-4"></i>
                  <span>Gerar</span>
                </button>
              </div>
            \`).join('');

        lucide.createIcons();
      } catch (err) {
        if (err.message !== 'unauthenticated') {
          lista.innerHTML = '<p class="px-6 py-8 text-xs text-red-600 text-center">Não foi possível carregar: ' + err.message + '</p>';
        }
      }
    }

    async function emitirCodigo(colaboradorId) {
      const res = await fetch(API_BASE + '/supervisor/codigos', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + getToken() },
        body: JSON.stringify({ colaboradorId })
      });
      const data = await res.json().catch(() => ({}));
      if (res.ok) {
        showToast('Código emitido', 'Código ' + (data.codigoFormatado || data.codigo || '') + ' — anote e entregue à pessoa.', 'success');
        await refreshCodes();
      } else {
        showToast('Erro', data.erro || 'Não foi possível emitir o código.', 'error');
      }
    }

    async function cancelarCodigo(colaboradorId) {
      const res = await fetch(API_BASE + '/supervisor/codigos/cancelar', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + getToken() },
        body: JSON.stringify({ colaboradorId })
      });
      const data = await res.json().catch(() => ({}));
      if (res.ok) {
        showToast('Código cancelado', 'O passe deixou de valer.', 'success');
        await refreshCodes();
      } else {
        showToast('Erro', data.erro || 'Não foi possível cancelar.', 'error');
      }
    }

    // ---- Contas de acesso (Admin / Supervisor) -----------------------------
    // Esta seção não existia no protótipo: ele só cadastrava colaboradores.
    // Contas de acesso são outra coisa -- têm e-mail, senha e perfil, e só o
    // Administrador pode criá-las (POST /admin/usuarios).
    async function refreshUsers() {
      const container = document.getElementById('user-account-list');
      if (!container) return;
      try {
        const data = await apiFetch('/admin/usuarios');
        const users = data.usuarios || data || [];
        if (!users.length) {
          container.innerHTML = '<p class="text-xs text-stone-400 py-3">Nenhuma conta de acesso cadastrada.</p>';
          return;
        }
        container.innerHTML = users.map(u => {
          const admin = (u.role || '').toLowerCase() === 'admin';
          const perfil = admin ? 'Administrador' : 'Supervisor' + (u.turno ? ' · Turno ' + u.turno : '');
          const nomeEsc = (u.name || '').replace(/'/g, "\\\\'");
          return \`
            <div class="py-3">
              <div class="flex items-center justify-between gap-3 flex-wrap">
                <div class="min-w-0">
                  <h4 class="text-sm font-bold text-coffee-950 truncate">\${u.name}</h4>
                  <p class="text-xs text-stone-500 truncate">\${u.email}</p>
                </div>
                <div class="flex items-center space-x-2 shrink-0">
                  \${u.banned ? '<span class="text-xs px-2 py-1 rounded-full border bg-red-50 text-red-700 border-red-200 font-semibold">Bloqueada</span>' : ''}
                  \${u.mustChangePassword ? '<span class="text-xs px-2 py-1 rounded-full border bg-amber-50 text-amber-700 border-amber-200 font-semibold">Senha provisória</span>' : ''}
                  <span class="text-xs px-2 py-1 rounded-full border font-semibold \${admin ? 'bg-coffee-100 text-coffee-800 border-coffee-200' : 'bg-stone-100 text-stone-600 border-stone-200'}">\${perfil}</span>
                </div>
              </div>
              <div class="flex flex-wrap gap-2 mt-2">
                <button onclick="mudarPerfil('\${u.id}', \${admin}, '\${u.turno || ''}')"
                  class="px-2.5 py-1.5 rounded-lg border border-stone-300 text-stone-600 text-xs font-semibold hover:bg-stone-50">\${admin ? 'Tornar Supervisor' : 'Tornar Admin'}</button>
                <button onclick="redefinirSenha('\${u.id}')"
                  class="px-2.5 py-1.5 rounded-lg border border-stone-300 text-stone-600 text-xs font-semibold hover:bg-stone-50">Redefinir senha</button>
                <button onclick="bloquearUsuario('\${u.id}', \${!!u.banned})"
                  class="px-2.5 py-1.5 rounded-lg border border-amber-300 text-amber-700 text-xs font-semibold hover:bg-amber-50">\${u.banned ? 'Reativar' : 'Bloquear'}</button>
                <button onclick="excluirUsuario('\${u.id}', '\${nomeEsc}')"
                  class="px-2.5 py-1.5 rounded-lg border border-red-300 text-red-700 text-xs font-semibold hover:bg-red-50">Excluir</button>
              </div>
            </div>
          \`;
        }).join('');
      } catch (err) {
        // Supervisor não enxerga contas; a seção some em vez de mostrar erro.
        container.innerHTML = '<p class="text-xs text-stone-400 py-3">Apenas o Administrador pode ver e criar contas de acesso.</p>';
      }
    }

    function openNewUserModal() {
      document.getElementById('new-user-modal').classList.remove('hidden');
      document.getElementById('new-user-modal').classList.add('flex');
    }

    function closeNewUserModal() {
      document.getElementById('new-user-modal').classList.add('hidden');
      document.getElementById('new-user-modal').classList.remove('flex');
    }

    function onPerfilChange() {
      const perfil = document.getElementById('user-perfil').value;
      const admin = perfil === 'ADMIN';
      document.getElementById('user-turno-wrap').classList.toggle('hidden', admin);
      document.getElementById('user-senha-hint').textContent = admin
        ? 'Obrigatória para Administrador, mínimo 10 caracteres.'
        : 'Opcional: em branco, o sistema gera uma senha provisória.';
      document.getElementById('user-senha').required = admin;
    }

    async function handleCreateUser(e) {
      e.preventDefault();
      const perfil = document.getElementById('user-perfil').value;
      const senha = document.getElementById('user-senha').value;
      const turno = document.getElementById('user-turno').value;
      const payload = {
        nome: document.getElementById('user-nome').value.trim(),
        email: document.getElementById('user-email').value.trim(),
        perfil: perfil
      };
      if (senha) payload.senha = senha;
      if (perfil !== 'ADMIN') payload.turno = turno;

      const res = await fetch(API_BASE + '/admin/usuarios', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + getToken() },
        body: JSON.stringify(payload)
      });
      const data = await res.json().catch(() => ({}));
      if (res.ok) {
        const provisoria = data.senhaTemporaria || data.senhaProvisoria;
        showToast(
          'Conta criada',
          provisoria ? 'Senha provisória: ' + provisoria : 'A pessoa já pode entrar com a senha definida.',
          'success'
        );
        closeNewUserModal();
        document.getElementById('new-user-form').reset();
        onPerfilChange();
        await refreshUsers();
      } else {
        showToast('Erro', data.erro || 'Falha ao criar a conta.', 'error');
      }
    }

    function openNewCollaboratorModal() {
      document.getElementById('new-collaborator-modal').classList.remove('hidden');
      document.getElementById('new-collaborator-modal').classList.add('flex');
    }

    function closeNewCollaboratorModal() {
      document.getElementById('new-collaborator-modal').classList.add('hidden');
      document.getElementById('new-collaborator-modal').classList.remove('flex');
    }

    async function handleCreateCollaborator(e) {
      e.preventDefault();
      const name = document.getElementById('col-name').value;
      const role = document.getElementById('col-role').value;
      const setor = document.getElementById('col-setor').value.trim();

      // Vai para o backend real: mesma rota que o app usa, com a sessão Bearer.
      // O PIN não existe neste modelo -- quem libera a pausa é o código de café
      // emitido pelo Supervisor, com prazo e uso único.
      const res = await fetch(API_BASE + '/gestao/colaboradores', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer ' + getToken()
        },
        body: JSON.stringify({ nome: name, turno: role || null, setor: setor || null })
      });

      const data = await res.json().catch(() => ({}));
      if (res.ok) {
        showToast('Sucesso', 'Colaborador adicionado!', 'success');
        closeNewCollaboratorModal();
        document.getElementById('new-col-form').reset();
        await refreshData();
      } else {
        showToast('Erro', data.erro || 'Falha ao salvar.', 'error');
      }
    }

    function saveSettings() {
      const voice = document.getElementById('setting-voice').value;
      state.voiceEnabled = (voice === 'enabled');
      showToast('Configurações Salvas', 'Parâmetros atualizados com sucesso.', 'success');
    }

    // ---- Login -------------------------------------------------------------
    function showLogin(aviso) {
      const el = document.getElementById('login-overlay');
      if (!el) return;
      el.classList.remove('hidden');
      el.classList.add('flex');
      const erro = document.getElementById('login-error');
      if (erro) {
        erro.textContent = aviso || '';
        erro.classList.toggle('hidden', !aviso);
      }
    }

    // Vincular um totem nao exige sessao de gestao: /setup/device-activation e
    // a unica rota aberta do sistema, justamente para o aparelho do corredor
    // poder nascer sem ninguem fazer login nele. Por isso o overlay sai da
    // frente em vez de barrar o caminho.
    function irParaTotem() {
      hideLogin();
      switchTab('kiosk');
    }

    function hideLogin() {
      const el = document.getElementById('login-overlay');
      if (!el) return;
      el.classList.add('hidden');
      el.classList.remove('flex');
    }

    async function handleLogin(e) {
      e.preventDefault();
      const btn = document.getElementById('login-submit');
      const email = document.getElementById('login-email').value.trim();
      const password = document.getElementById('login-password').value;
      btn.disabled = true;
      btn.textContent = 'Entrando...';
      try {
        const user = await doLogin(email, password);
        hideLogin();
        const quem = document.getElementById('session-user');
        if (quem) quem.textContent = user.name || user.email;
        await refreshData();
      } catch (err) {
        const erro = document.getElementById('login-error');
        erro.textContent = err.message;
        erro.classList.remove('hidden');
      } finally {
        btn.disabled = false;
        btn.textContent = 'Entrar';
      }
    }

    function handleLogout() {
      clearToken();
      state.collaborators = [];
      state.history = [];
      state.alerts = [];
      renderTeamList(); renderHistoryTable(); renderAlerts(); renderAdminList();
      showLogin();
    }

    // Initialize on load
    window.addEventListener('DOMContentLoaded', () => {
      // O totem tem credencial propria. Um aparelho ja vinculado bate ponto
      // sem ninguem da gestao ter sessao aberta neste navegador -- e isso que
      // faz dele um totem, e nao mais um ecra de administrador. As outras abas
      // continuam a pedir login: quem toca nelas cai no overlay.
      totemBoot();
      if (getToken()) { refreshData(); }
      else if (getDeviceToken()) { switchTab('kiosk'); }
      else { showLogin(); }
      lucide.createIcons();
    });
  </script>
</body>
</html>`;

// O painel nao tem mais API propria: quem responde e o backend Cloudflare, o
// mesmo que o totem Android usa. Este processo so serve a pagina e injeta o
// endereco da API, para o navegador falar direto com o Worker (o CORS ja
// permite Authorization de qualquer origem).
const API_BASE = (process.env.PONTO_API_BASE ?? 'https://pontocafe.bernard-castillo.workers.dev').replace(/\/+$/, '');

const server = http.createServer((req, res) => {
  const pathname = parseUrl(req.url ?? '/').pathname ?? '/';

  if (req.method !== 'GET') {
    res.writeHead(405, { 'Content-Type': 'text/plain; charset=utf-8' });
    res.end('O painel apenas serve a pagina; os dados vem do backend.');
    return;
  }

  if (pathname === '/health') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ status: 'ok', apiBase: API_BASE }));
    return;
  }

  const page = HTML_CONTENT.replace(
    '</head>',
    `  <script>window.PONTO_API_BASE = ${JSON.stringify(API_BASE)};</script>\n</head>`,
  );

  res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
  res.end(page);
});

const PORT = Number(process.env.PORT ?? 3000);
server.listen(PORT, '0.0.0.0', () => {
  console.log(`[Ponto Café] Painel em http://localhost:${PORT} — API: ${API_BASE}`);
});
