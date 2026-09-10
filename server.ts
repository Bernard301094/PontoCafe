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
  </style>
</head>
<body class="bg-stone-100 text-stone-800 font-sans h-full flex flex-col antialiased select-none">
  <!-- Top Bar -->
  <header id="app-header" class="bg-white border-b border-stone-200 px-4 md:px-8 py-3 flex items-center justify-between shadow-sm sticky top-0 z-30">
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

    <!-- Mode Selector Tabs -->
    <nav class="flex items-center bg-stone-100 p-1 rounded-xl border border-stone-200 text-xs font-semibold">
      <button id="tab-kiosk" onclick="switchTab('kiosk')" class="flex items-center space-x-1.5 px-3 py-2 rounded-lg transition-all bg-white text-coffee-900 shadow-sm">
        <i data-lucide="calculator" class="w-4 h-4"></i>
        <span>Totem / Ponto</span>
      </button>
      <button id="tab-supervisor" onclick="switchTab('supervisor')" class="flex items-center space-x-1.5 px-3 py-2 rounded-lg transition-all text-stone-600 hover:text-stone-900">
        <i data-lucide="users" class="w-4 h-4"></i>
        <span>Painel Equipe</span>
      </button>
      <button id="tab-history" onclick="switchTab('history')" class="flex items-center space-x-1.5 px-3 py-2 rounded-lg transition-all text-stone-600 hover:text-stone-900">
        <i data-lucide="history" class="w-4 h-4"></i>
        <span>Registros</span>
      </button>
      <button id="tab-admin" onclick="switchTab('admin')" class="flex items-center space-x-1.5 px-3 py-2 rounded-lg transition-all text-stone-600 hover:text-stone-900">
        <i data-lucide="settings" class="w-4 h-4"></i>
        <span>Gestão</span>
      </button>
    </nav>
  </header>

  <!-- Notification Toast -->
  <div id="toast-container" class="fixed top-20 right-6 z-50 flex flex-col space-y-2 pointer-events-none"></div>

  <!-- Main Views Container -->
  <main class="flex-1 overflow-y-auto p-4 md:p-8 max-w-7xl mx-auto w-full">
    
    <!-- VIEW 1: KIOSK TOTEM CLOCK-IN SCREEN -->
    <section id="view-kiosk" class="h-full flex flex-col justify-center items-center py-4">
      <div class="w-full max-w-md bg-white rounded-3xl p-6 sm:p-8 shadow-xl border border-stone-200">
        <div class="text-center mb-6">
          <div class="inline-flex items-center justify-center w-14 h-14 rounded-2xl bg-amber-50 text-coffee-700 border border-amber-200 mb-3 shadow-sm">
            <i data-lucide="fingerprint" class="w-8 h-8"></i>
          </div>
          <h2 class="text-2xl font-bold text-coffee-950">Registro de Ponto</h2>
          <p class="text-sm text-stone-500 mt-1">Digite seu PIN de 4 dígitos para registrar</p>
        </div>

        <!-- PIN Display Dots -->
        <div class="flex justify-center items-center space-x-4 mb-6 py-2">
          <div id="pin-dot-0" class="w-5 h-5 rounded-full border-2 border-stone-300 bg-stone-100 transition-all"></div>
          <div id="pin-dot-1" class="w-5 h-5 rounded-full border-2 border-stone-300 bg-stone-100 transition-all"></div>
          <div id="pin-dot-2" class="w-5 h-5 rounded-full border-2 border-stone-300 bg-stone-100 transition-all"></div>
          <div id="pin-dot-3" class="w-5 h-5 rounded-full border-2 border-stone-300 bg-stone-100 transition-all"></div>
        </div>

        <!-- Feedback message area -->
        <div id="kiosk-feedback" class="min-h-[28px] text-center text-xs font-semibold mb-4 text-stone-500">
          Toque nos números ou use o teclado
        </div>

        <!-- Numpad Grid -->
        <div class="grid grid-cols-3 gap-3 mb-6">
          <button onclick="pressPin('1')" class="h-16 rounded-2xl bg-stone-50 hover:bg-amber-50 active:bg-amber-100 text-2xl font-bold text-coffee-900 border border-stone-200 shadow-sm transition-all flex items-center justify-center">1</button>
          <button onclick="pressPin('2')" class="h-16 rounded-2xl bg-stone-50 hover:bg-amber-50 active:bg-amber-100 text-2xl font-bold text-coffee-900 border border-stone-200 shadow-sm transition-all flex items-center justify-center">2</button>
          <button onclick="pressPin('3')" class="h-16 rounded-2xl bg-stone-50 hover:bg-amber-50 active:bg-amber-100 text-2xl font-bold text-coffee-900 border border-stone-200 shadow-sm transition-all flex items-center justify-center">3</button>
          
          <button onclick="pressPin('4')" class="h-16 rounded-2xl bg-stone-50 hover:bg-amber-50 active:bg-amber-100 text-2xl font-bold text-coffee-900 border border-stone-200 shadow-sm transition-all flex items-center justify-center">4</button>
          <button onclick="pressPin('5')" class="h-16 rounded-2xl bg-stone-50 hover:bg-amber-50 active:bg-amber-100 text-2xl font-bold text-coffee-900 border border-stone-200 shadow-sm transition-all flex items-center justify-center">5</button>
          <button onclick="pressPin('6')" class="h-16 rounded-2xl bg-stone-50 hover:bg-amber-50 active:bg-amber-100 text-2xl font-bold text-coffee-900 border border-stone-200 shadow-sm transition-all flex items-center justify-center">6</button>
          
          <button onclick="pressPin('7')" class="h-16 rounded-2xl bg-stone-50 hover:bg-amber-50 active:bg-amber-100 text-2xl font-bold text-coffee-900 border border-stone-200 shadow-sm transition-all flex items-center justify-center">7</button>
          <button onclick="pressPin('8')" class="h-16 rounded-2xl bg-stone-50 hover:bg-amber-50 active:bg-amber-100 text-2xl font-bold text-coffee-900 border border-stone-200 shadow-sm transition-all flex items-center justify-center">8</button>
          <button onclick="pressPin('9')" class="h-16 rounded-2xl bg-stone-50 hover:bg-amber-50 active:bg-amber-100 text-2xl font-bold text-coffee-900 border border-stone-200 shadow-sm transition-all flex items-center justify-center">9</button>
          
          <button onclick="clearPin()" class="h-16 rounded-2xl bg-red-50 hover:bg-red-100 active:bg-red-200 text-sm font-bold text-red-700 border border-red-200 transition-all flex items-center justify-center">
            <i data-lucide="rotate-ccw" class="w-5 h-5 mr-1"></i> Limpar
          </button>
          <button onclick="pressPin('0')" class="h-16 rounded-2xl bg-stone-50 hover:bg-amber-50 active:bg-amber-100 text-2xl font-bold text-coffee-900 border border-stone-200 shadow-sm transition-all flex items-center justify-center">0</button>
          <button onclick="backspacePin()" class="h-16 rounded-2xl bg-stone-100 hover:bg-stone-200 active:bg-stone-300 text-stone-700 border border-stone-200 transition-all flex items-center justify-center">
            <i data-lucide="delete" class="w-6 h-6"></i>
          </button>
        </div>

        <!-- Quick PIN Helper for testing -->
        <div class="mt-4 p-3 bg-stone-50 rounded-xl border border-dashed border-stone-300 text-center">
          <p class="text-xs text-stone-500 font-semibold mb-1">PINs de Teste Rápidos:</p>
          <div class="flex flex-wrap justify-center gap-1.5 text-xs">
            <button onclick="quickFillPin('1024')" class="px-2 py-1 bg-white hover:bg-amber-50 rounded border border-stone-200 text-coffee-900 font-mono">Lucas (1024)</button>
            <button onclick="quickFillPin('2048')" class="px-2 py-1 bg-white hover:bg-amber-50 rounded border border-stone-200 text-coffee-900 font-mono">Camila (2048)</button>
            <button onclick="quickFillPin('3072')" class="px-2 py-1 bg-white hover:bg-amber-50 rounded border border-stone-200 text-coffee-900 font-mono">Bruno (3072)</button>
          </div>
        </div>
      </div>
    </section>

    <!-- MODAL: CHOOSE OPERATION AFTER VALID PIN -->
    <div id="ponto-modal" class="hidden fixed inset-0 z-50 bg-stone-900/60 backdrop-blur-sm flex items-center justify-center p-4">
      <div class="bg-white w-full max-w-lg rounded-3xl p-6 sm:p-8 shadow-2xl border border-stone-200">
        <div class="flex items-center space-x-3 mb-4 pb-4 border-b border-stone-100">
          <div class="w-12 h-12 rounded-2xl bg-amber-100 text-coffee-800 flex items-center justify-center font-bold text-lg">
            <span id="modal-collaborator-initials">LM</span>
          </div>
          <div>
            <h3 id="modal-collaborator-name" class="text-xl font-bold text-coffee-950">Lucas Mendes</h3>
            <p id="modal-collaborator-role" class="text-xs text-stone-500 font-medium">Barista Chefe • Balcão</p>
          </div>
          <div class="ml-auto">
            <span id="modal-current-status" class="px-2.5 py-1 rounded-full text-xs font-semibold bg-emerald-100 text-emerald-800">Presente</span>
          </div>
        </div>

        <p class="text-sm font-semibold text-stone-700 mb-4">Selecione o registro para o momento atual:</p>

        <div class="grid grid-cols-1 sm:grid-cols-2 gap-3 mb-6">
          <button onclick="submitPonto('entrada')" class="p-4 rounded-2xl border border-emerald-200 bg-emerald-50 hover:bg-emerald-100 text-left transition-all">
            <div class="flex items-center justify-between mb-1">
              <span class="font-bold text-emerald-900">Entrada</span>
              <i data-lucide="log-in" class="w-5 h-5 text-emerald-600"></i>
            </div>
            <p class="text-xs text-emerald-700 font-medium">Início de turno de trabalho</p>
          </button>

          <button onclick="submitPonto('pausa_cafe')" class="p-4 rounded-2xl border border-amber-200 bg-amber-50 hover:bg-amber-100 text-left transition-all">
            <div class="flex items-center justify-between mb-1">
              <span class="font-bold text-amber-900">Pausa Café</span>
              <i data-lucide="coffee" class="w-5 h-5 text-amber-600"></i>
            </div>
            <p class="text-xs text-amber-700 font-medium">Intervalo de descanso (15 min)</p>
          </button>

          <button onclick="submitPonto('retorno_cafe')" class="p-4 rounded-2xl border border-amber-200 bg-amber-50 hover:bg-amber-100 text-left transition-all">
            <div class="flex items-center justify-between mb-1">
              <span class="font-bold text-amber-900">Retorno Café</span>
              <i data-lucide="check-circle-2" class="w-5 h-5 text-amber-600"></i>
            </div>
            <p class="text-xs text-amber-700 font-medium">Fim do intervalo de descanso</p>
          </button>

          <button onclick="submitPonto('saida')" class="p-4 rounded-2xl border border-stone-200 bg-stone-50 hover:bg-stone-100 text-left transition-all">
            <div class="flex items-center justify-between mb-1">
              <span class="font-bold text-stone-900">Saída</span>
              <i data-lucide="log-out" class="w-5 h-5 text-stone-600"></i>
            </div>
            <p class="text-xs text-stone-600 font-medium">Encerramento do expediente</p>
          </button>
        </div>

        <div class="flex justify-end">
          <button onclick="closeModal()" class="px-5 py-2.5 rounded-xl border border-stone-300 text-stone-700 font-semibold hover:bg-stone-50 transition-all text-sm">
            Cancelar
          </button>
        </div>
      </div>
    </div>

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
    <section id="view-history" class="hidden space-y-6">
      <div class="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div>
          <h2 class="text-2xl font-bold text-coffee-950">Histórico de Batidas de Ponto</h2>
          <p class="text-sm text-stone-500">Linha do tempo auditável de registros de entrada, pausas e saída</p>
        </div>
      </div>

      <div class="bg-white rounded-2xl border border-stone-200 overflow-hidden shadow-sm">
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
    <div id="new-collaborator-modal" class="hidden fixed inset-0 z-50 bg-stone-900/60 backdrop-blur-sm flex items-center justify-center p-4">
      <div class="bg-white w-full max-w-md rounded-3xl p-6 sm:p-8 shadow-2xl border border-stone-200">
        <h3 class="text-xl font-bold text-coffee-950 mb-1">Cadastrar Colaborador</h3>
        <p class="text-xs text-stone-500 mb-6">Preencha os dados e escolha um PIN exclusivo de 4 dígitos</p>

        <form id="new-col-form" onsubmit="handleCreateCollaborator(event)" class="space-y-4">
          <div>
            <label class="block text-xs font-semibold text-stone-700 mb-1">Nome Completo</label>
            <input type="text" id="col-name" required placeholder="Ex: Patrícia Alves" class="w-full px-3.5 py-2.5 border border-stone-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-amber-500">
          </div>

          <div>
            <label class="block text-xs font-semibold text-stone-700 mb-1">Cargo / Função</label>
            <select id="col-role" class="w-full px-3.5 py-2.5 border border-stone-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-amber-500">
              <option value="Barista">Barista</option>
              <option value="Atendente / Caixa">Atendente / Caixa</option>
              <option value="Supervisor">Supervisor</option>
              <option value="Confeiteiro / Cozinha">Confeiteiro / Cozinha</option>
              <option value="Gerente">Gerente</option>
            </select>
          </div>

          <div>
            <label class="block text-xs font-semibold text-stone-700 mb-1">PIN Numérico (4 Dígitos)</label>
            <input type="text" id="col-pin" required maxlength="4" pattern="[0-9]{4}" placeholder="Ex: 8899" class="w-full px-3.5 py-2.5 border border-stone-200 rounded-xl text-sm font-mono tracking-widest focus:outline-none focus:ring-2 focus:ring-amber-500">
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
      currentPin: '',
      activeCollaborator: null,
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
      const tabs = ['kiosk', 'supervisor', 'history', 'admin'];
      tabs.forEach(tab => {
        const btn = document.getElementById('tab-' + tab);
        const view = document.getElementById('view-' + tab);
        if (tab === tabId) {
          btn.className = 'flex items-center space-x-1.5 px-3 py-2 rounded-lg transition-all bg-white text-coffee-900 shadow-sm';
          view.classList.remove('hidden');
        } else {
          btn.className = 'flex items-center space-x-1.5 px-3 py-2 rounded-lg transition-all text-stone-600 hover:text-stone-900';
          view.classList.add('hidden');
        }
      });
      if (tabId !== 'kiosk') {
        clearPin();
      }
    }

    // Numpad PIN logic
    function updatePinDisplay() {
      for (let i = 0; i < 4; i++) {
        const dot = document.getElementById('pin-dot-' + i);
        if (i < state.currentPin.length) {
          dot.className = 'w-5 h-5 rounded-full border-2 border-coffee-800 bg-coffee-800 scale-110 transition-all';
        } else {
          dot.className = 'w-5 h-5 rounded-full border-2 border-stone-300 bg-stone-100 transition-all';
        }
      }

      if (state.currentPin.length === 4) {
        verifyPin(state.currentPin);
      }
    }

    function pressPin(digit) {
      if (state.currentPin.length < 4) {
        state.currentPin += digit;
        updatePinDisplay();
      }
    }

    function backspacePin() {
      if (state.currentPin.length > 0) {
        state.currentPin = state.currentPin.slice(0, -1);
        updatePinDisplay();
      }
    }

    function clearPin() {
      state.currentPin = '';
      updatePinDisplay();
      const feedback = document.getElementById('kiosk-feedback');
      if (feedback) {
        feedback.textContent = 'Toque nos números ou use o teclado';
        feedback.className = 'min-h-[28px] text-center text-xs font-semibold mb-4 text-stone-500';
      }
    }

    function quickFillPin(pin) {
      state.currentPin = pin;
      updatePinDisplay();
    }

    // Physical keyboard input
    window.addEventListener('keydown', (e) => {
      const kioskView = document.getElementById('view-kiosk');
      if (!kioskView.classList.contains('hidden') && !document.getElementById('ponto-modal').classList.contains('flex')) {
        if (e.key >= '0' && e.key <= '9') {
          pressPin(e.key);
        } else if (e.key === 'Backspace') {
          backspacePin();
        } else if (e.key === 'Escape') {
          clearPin();
        }
      }
    });

    async function verifyPin(pin) {
      const feedback = document.getElementById('kiosk-feedback');
      feedback.textContent = 'Verificando PIN...';
      feedback.className = 'min-h-[28px] text-center text-xs font-semibold mb-4 text-amber-600';

      const found = state.collaborators.find(c => c.pin === pin);
      if (found) {
        feedback.textContent = 'PIN Reconhecido: ' + found.name;
        feedback.className = 'min-h-[28px] text-center text-xs font-semibold mb-4 text-emerald-600';
        openPontoModal(found);
      } else {
        feedback.textContent = 'PIN não encontrado. Tente novamente.';
        feedback.className = 'min-h-[28px] text-center text-xs font-semibold mb-4 text-red-600';
        setTimeout(clearPin, 1200);
      }
    }

    function openPontoModal(collaborator) {
      state.activeCollaborator = collaborator;
      document.getElementById('modal-collaborator-name').textContent = collaborator.name;
      document.getElementById('modal-collaborator-role').textContent = collaborator.role + ' • ' + collaborator.department;
      
      const initials = collaborator.name.split(' ').map(n => n[0]).join('').slice(0, 2).toUpperCase();
      document.getElementById('modal-collaborator-initials').textContent = initials;
      
      const statusBadges = {
        active: { text: 'Em Atendimento', class: 'bg-emerald-100 text-emerald-800' },
        coffee_break: { text: 'Em Pausa Café', class: 'bg-amber-100 text-amber-800' },
        off_duty: { text: 'Fora de Turno', class: 'bg-stone-100 text-stone-700' }
      };
      const badge = statusBadges[collaborator.status] || statusBadges.off_duty;
      const stElem = document.getElementById('modal-current-status');
      stElem.textContent = badge.text;
      stElem.className = 'px-2.5 py-1 rounded-full text-xs font-semibold ' + badge.class;

      const modal = document.getElementById('ponto-modal');
      modal.classList.remove('hidden');
      modal.classList.add('flex');
    }

    function closeModal() {
      const modal = document.getElementById('ponto-modal');
      modal.classList.add('hidden');
      modal.classList.remove('flex');
      state.activeCollaborator = null;
      clearPin();
    }

    async function submitPonto(type) {
      if (!state.activeCollaborator) return;
      const col = state.activeCollaborator;

      const res = await fetch('/api/ponto', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          collaboratorId: col.id,
          type: type,
          source: 'kiosk'
        })
      });

      const data = await res.json();
      closeModal();

      if (data.success) {
        showToast('Ponto Registrado!', data.message, 'success');
        speakVoice(data.voiceMessage || data.message);
        await refreshData();
      } else {
        showToast('Aviso', data.message || 'Erro ao registrar ponto.', 'error');
      }
    }

    // Refresh Data from API
    async function refreshData() {
      try {
        const [colRes, histRes, alertRes] = await Promise.all([
          fetch('/api/collaborators'),
          fetch('/api/history'),
          fetch('/api/alerts')
        ]);

        state.collaborators = await colRes.json();
        state.history = await histRes.json();
        state.alerts = await alertRes.json();

        renderTeamList();
        renderHistoryTable();
        renderAlerts();
        renderAdminList();
        updateSummaryStats();
      } catch (err) {
        console.error('Error fetching data:', err);
      }
    }

    function updateSummaryStats() {
      document.getElementById('stat-total').textContent = state.collaborators.length;
      document.getElementById('stat-active').textContent = state.collaborators.filter(c => c.status === 'active').length;
      document.getElementById('stat-coffee').textContent = state.collaborators.filter(c => c.status === 'coffee_break').length;
      document.getElementById('stat-off').textContent = state.collaborators.filter(c => c.status === 'off_duty').length;
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
          <div class="px-6 py-4 flex items-center justify-between hover:bg-stone-50/60 transition-colors">
            <div class="flex items-center space-x-3">
              <div class="w-10 h-10 rounded-xl bg-stone-100 border border-stone-200 text-coffee-900 font-bold flex items-center justify-center text-sm">
                \${c.name.slice(0, 2).toUpperCase()}
              </div>
              <div>
                <h4 class="font-bold text-coffee-950 text-sm">\${c.name}</h4>
                <p class="text-xs text-stone-500">\${c.role} • \${c.department}</p>
              </div>
            </div>
            <div class="flex items-center space-x-4">
              <div class="text-right hidden sm:block">
                <div class="text-xs font-semibold text-stone-700">\${c.lastPontoType || 'Sem registro'}</div>
                <div class="text-[11px] text-stone-400 font-mono">\${c.lastPontoTime || '--'}</div>
              </div>
              <span class="px-3 py-1 rounded-full text-xs font-semibold border \${st.badgeClass}">
                \${st.label}
              </span>
            </div>
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
    }

    function renderAdminList() {
      const container = document.getElementById('admin-collaborator-list');
      if (!container) return;

      container.innerHTML = state.collaborators.map(c => \`
        <div class="py-3 flex items-center justify-between">
          <div>
            <h4 class="text-sm font-bold text-coffee-950">\${c.name}</h4>
            <p class="text-xs text-stone-500">\${c.role} • \${c.department}</p>
          </div>
          <div class="flex items-center space-x-3">
            <span class="font-mono text-xs bg-stone-100 px-2 py-1 rounded border border-stone-200 text-stone-600 font-bold">PIN: \${c.pin}</span>
          </div>
        </div>
      \`).join('');
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
      const pin = document.getElementById('col-pin').value;

      const res = await fetch('/api/collaborators', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name, role, pin, department: 'Atendimento' })
      });

      const data = await res.json();
      if (data.success) {
        showToast('Sucesso', 'Colaborador adicionado!', 'success');
        closeNewCollaboratorModal();
        document.getElementById('new-col-form').reset();
        await refreshData();
      } else {
        showToast('Erro', data.message || 'Falha ao salvar.', 'error');
      }
    }

    function saveSettings() {
      const voice = document.getElementById('setting-voice').value;
      state.voiceEnabled = (voice === 'enabled');
      showToast('Configurações Salvas', 'Parâmetros atualizados com sucesso.', 'success');
    }

    // Initialize on load
    window.addEventListener('DOMContentLoaded', () => {
      refreshData();
      lucide.createIcons();
    });
  </script>
</body>
</html>`;

const server = http.createServer((req, res) => {
  const parsed = parseUrl(req.url || '/', true);
  const pathname = parsed.pathname || '/';

  // CORS headers
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    res.end();
    return;
  }

  // API Routes
  if (pathname === '/api/collaborators' && req.method === 'GET') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify(collaborators));
    return;
  }

  if (pathname === '/api/collaborators' && req.method === 'POST') {
    let body = '';
    req.on('data', chunk => { body += chunk; });
    req.on('end', () => {
      try {
        const data = JSON.parse(body);
        if (!data.name || !data.pin) {
          res.writeHead(400, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({ success: false, message: 'Nome e PIN são obrigatórios' }));
          return;
        }
        const newCol: Collaborator = {
          id: 'col-' + (collaborators.length + 1),
          name: data.name,
          role: data.role || 'Barista',
          pin: data.pin,
          department: data.department || 'Cafeteria',
          status: 'off_duty'
        };
        collaborators.push(newCol);
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ success: true, collaborator: newCol }));
      } catch {
        res.writeHead(400, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ success: false, message: 'Payload inválido' }));
      }
    });
    return;
  }

  if (pathname === '/api/history' && req.method === 'GET') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify(pontoHistory));
    return;
  }

  if (pathname === '/api/alerts' && req.method === 'GET') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify(alerts));
    return;
  }

  if (pathname === '/api/ponto' && req.method === 'POST') {
    let body = '';
    req.on('data', chunk => { body += chunk; });
    req.on('end', () => {
      try {
        const data = JSON.parse(body);
        const col = collaborators.find(c => c.id === data.collaboratorId);
        if (!col) {
          res.writeHead(404, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({ success: false, message: 'Colaborador não encontrado' }));
          return;
        }

        const nowTime = getFormattedNow();
        const fullTime = getFullDateTimeNow();
        const typeNames: Record<string, string> = {
          entrada: 'Entrada',
          pausa_cafe: 'Pausa Café',
          retorno_cafe: 'Retorno Café',
          saida: 'Saída'
        };

        const readableType = typeNames[data.type] || data.type;

        // Update collaborator status
        if (data.type === 'entrada' || data.type === 'retorno_cafe') {
          col.status = 'active';
        } else if (data.type === 'pausa_cafe') {
          col.status = 'coffee_break';
        } else if (data.type === 'saida') {
          col.status = 'off_duty';
        }

        col.lastPontoTime = nowTime;
        col.lastPontoType = readableType;

        // Add history record
        const record: PontoRecord = {
          id: 'rec-' + (pontoHistory.length + 1),
          collaboratorId: col.id,
          collaboratorName: col.name,
          type: data.type,
          timestamp: fullTime,
          source: data.source || 'kiosk'
        };
        pontoHistory.push(record);

        // Voice Message synthesis
        const voiceMessages: Record<string, string> = {
          entrada: `Bom dia, ${col.name}! Entrada confirmada às ${nowTime}. Tenha um ótimo turno!`,
          pausa_cafe: `${col.name}, pausa para o café registrada. Bom descanso de 15 minutos!`,
          retorno_cafe: `Retorno registrado, ${col.name}. Bem-vindo de volta!`,
          saida: `Saída confirmada, ${col.name}. Bom descanso e até o próximo expediente!`
        };

        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({
          success: true,
          message: `${readableType} registrada às ${nowTime} para ${col.name}`,
          voiceMessage: voiceMessages[data.type] || `${readableType} confirmada.`
        }));
      } catch {
        res.writeHead(400, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ success: false, message: 'Erro ao processar dados de ponto' }));
      }
    });
    return;
  }

  // Frontend HTML
  res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
  res.end(HTML_CONTENT);
});

const PORT = 3000;
server.listen(PORT, '0.0.0.0', () => {
  console.log(`[Ponto Café] Servidor em execução na porta ${PORT}`);
});
