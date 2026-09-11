import http from 'node:http';
import { parse as parseUrl } from 'node:url';

const HTML_CONTENT = `<!DOCTYPE html>
<html lang="pt-BR" class="h-full">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Ponto Café - Sistema Operacional de Ponto</title>
  <meta name="description" content="Sistema de Ponto Eletrônico e controle de pausas de café">
  <meta property="og:title" content="Ponto Café - Sistema Operacional de Ponto">
  <meta property="og:description" content="Sistema de Ponto Eletrônico e controle de pausas de café">
  <script src="https://cdn.tailwindcss.com"></script>
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@300;400;500;600;700;800&family=JetBrains+Mono:wght@400;500;600&display=swap" rel="stylesheet">
  <script src="https://unpkg.com/lucide@latest"></script>
  <!-- Desenha o QR do código de café num canvas. Versão fixa: um QR ilegível
       num turno inteiro por causa de uma atualização silenciosa do CDN seria
       caro de descobrir. -->
  <script src="https://cdnjs.cloudflare.com/ajax/libs/qrious/4.0.2/qrious.min.js"></script>
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
        <!-- Os dois ícones vão inline, e não como data-lucide: o lucide troca o
             <i> por um <svg> ao carregar a página, e depois já não há atributo
             para alternar. O pr-11 no input abre o espaço do botão. -->
        <div class="relative">
          <input id="login-password" type="password" required autocomplete="current-password"
            class="w-full px-3 py-2.5 pr-11 rounded-xl border border-stone-200 bg-stone-50 text-sm focus:outline-none focus:border-amberAccent" />
          <button type="button" id="login-password-toggle" onclick="alternarSenhaLogin()"
            aria-label="Mostrar senha" title="Mostrar senha" aria-pressed="false"
            class="absolute inset-y-0 right-0 px-3 flex items-center rounded-r-xl text-stone-400 hover:text-stone-600 focus:outline-none focus:text-amberAccent">
            <svg id="login-eye" xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0" />
              <circle cx="12" cy="12" r="3" />
            </svg>
            <svg id="login-eye-off" class="hidden" xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M10.733 5.076a10.744 10.744 0 0 1 11.205 6.575 1 1 0 0 1 0 .696 10.747 10.747 0 0 1-1.444 2.49" />
              <path d="M14.084 14.158a3 3 0 0 1-4.242-4.242" />
              <path d="M17.479 17.499a10.75 10.75 0 0 1-15.417-5.151 1 1 0 0 1 0-.696 10.75 10.75 0 0 1 4.446-5.143" />
              <path d="m2 2 20 20" />
            </svg>
          </button>
        </div>
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

    <!-- Menu do perfil.
         Ate aqui nao havia forma nenhuma de sair: handleLogout existia e nenhum
         botao lhe chamava, e o session-user que o JS procurava nunca esteve no
         HTML. Quem entrasse num aparelho partilhado ficava la dentro. -->
    <div id="perfil-wrap" class="relative">
      <button type="button" id="perfil-botao" onclick="alternarMenuPerfil(event)"
        aria-haspopup="true" aria-expanded="false"
        class="flex items-center gap-2 pl-2 pr-3 py-1.5 rounded-xl border border-stone-200 hover:bg-stone-50 transition-colors">
        <span id="perfil-iniciais" class="w-8 h-8 rounded-lg bg-coffee-800 text-amber-300 text-xs font-bold flex items-center justify-center shrink-0">--</span>
        <span class="text-left hidden sm:block">
          <span id="session-user" class="block text-xs font-bold text-coffee-950 leading-tight">—</span>
          <span id="perfil-papel" class="block text-[10px] font-semibold text-stone-400 uppercase tracking-wider leading-tight">—</span>
        </span>
        <i data-lucide="chevron-down" class="w-4 h-4 text-stone-400"></i>
      </button>

      <div id="perfil-menu" class="hidden absolute right-0 mt-2 w-60 bg-white rounded-2xl border border-stone-200 shadow-xl z-40 overflow-hidden">
        <div class="px-4 py-3 border-b border-stone-100">
          <p id="perfil-nome" class="text-sm font-bold text-coffee-950 truncate">—</p>
          <p id="perfil-email" class="text-xs text-stone-500 truncate">—</p>
        </div>
        <!-- O código próprio. Um Supervisor também toma café: aqui está o QR
             que ele mostra ao totem, no mesmo formato que o totem já lê. Só
             aparece se a conta estiver vinculada a um colaborador. -->
        <div id="perfil-codigo" class="px-4 py-3 border-b border-stone-100"></div>

        <div class="p-1.5">
          <button type="button" onclick="handleLogout()"
            class="w-full flex items-center gap-2.5 px-3 py-2.5 rounded-xl text-sm font-semibold text-red-600 hover:bg-red-50 transition-colors">
            <i data-lucide="log-out" class="w-4 h-4"></i><span>Sair da conta</span>
          </button>
        </div>
      </div>
    </div>

    <!-- Mode Selector Tabs
         Em telas estreitas (A55 tem ~412px) sete abas não cabem numa linha:
         a faixa rola no eixo X em vez de espremer ou quebrar o cabeçalho. -->
    <nav class="order-3 w-full lg:order-none lg:w-auto flex items-center bg-stone-100 p-1 rounded-xl border border-stone-200 text-xs font-semibold overflow-x-auto no-scrollbar">
      <button id="tab-kiosk" onclick="switchTab('kiosk')" aria-current="page" class="shrink-0 flex items-center space-x-1.5 px-3 py-2 rounded-lg transition-all bg-coffee-900 text-white shadow-sm">
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
              <p class="text-sm text-stone-500 mt-1">Toque para se encontrar e depois digite o código.</p>
            </div>

            <!-- A lista fechada, e não aberta.
                 Antes ficavam uma centena de nomes permanentemente no ecrã, à
                 vista de quem passasse ao lado do quiosque -- a escala de quem
                 trabalha ali, exposta o dia inteiro. Agora não se vê ninguém
                 até alguém tocar, e mesmo aí a busca por matrícula deixa uma
                 linha em vez de cem. -->
            <div id="totem-combo" class="relative">
              <button type="button" id="totem-combo-botao" onclick="totemAbrirLista()"
                aria-haspopup="listbox" aria-expanded="false"
                class="w-full flex items-center justify-between gap-3 px-4 py-4 rounded-2xl border-2 border-stone-300 bg-white text-left hover:border-amber-400 transition-colors">
                <span class="flex items-center gap-3 min-w-0">
                  <i data-lucide="user-round-search" class="w-5 h-5 text-stone-400 shrink-0"></i>
                  <span id="totem-combo-rotulo" class="text-base font-semibold text-stone-500 truncate">Toque aqui para se encontrar</span>
                </span>
                <i data-lucide="chevron-down" class="w-5 h-5 text-stone-400 shrink-0"></i>
              </button>

              <div id="totem-combo-painel" class="hidden absolute left-0 right-0 top-full mt-2 z-30 bg-white rounded-2xl border border-stone-200 shadow-2xl overflow-hidden">
                <div class="p-2 border-b border-stone-100">
                  <input id="totem-busca" oninput="renderTotemPessoas()" type="text" autocomplete="off" inputmode="search"
                    placeholder="Matrícula ou nome"
                    class="w-full px-4 py-3 rounded-xl border border-stone-300 text-sm focus:border-amber-400 focus:ring-2 focus:ring-amber-100 outline-none">
                </div>
                <div id="totem-pessoas" role="listbox" class="max-h-[42vh] overflow-y-auto divide-y divide-stone-100 p-1"></div>
              </div>
            </div>

            <!-- Atalho da câmara. Só aparece onde o QR foi liberado para este
                 aparelho, e só se o navegador souber ler códigos. Um botão que
                 existisse sempre e falhasse a seguir ensinaria a ignorá-lo. -->
            <button type="button" id="totem-qr-botao" onclick="totemAbrirCamera()"
              class="hidden w-full mt-2 flex items-center justify-center gap-2 px-4 py-3.5 rounded-2xl border-2 border-amber-300 bg-amber-50 text-coffee-900 font-bold hover:bg-amber-100 transition-colors">
              <i data-lucide="qr-code" class="w-5 h-5"></i><span>Ler meu QR</span>
            </button>

            <!-- Leitor. O vídeo só existe enquanto está aberto: uma câmara
                 ligada em segundo plano num quiosque é uma câmara a filmar a
                 sala o dia inteiro. -->
            <div id="totem-camera" class="hidden mt-3">
              <div class="relative rounded-2xl overflow-hidden bg-stone-900 aspect-[4/3]">
                <video id="totem-video" playsinline muted class="w-full h-full object-cover"></video>
                <div class="absolute inset-0 flex items-center justify-center pointer-events-none">
                  <div class="w-2/3 aspect-square border-4 border-white/70 rounded-3xl"></div>
                </div>
              </div>
              <p id="totem-camera-aviso" class="text-xs text-stone-500 text-center mt-2">Aponte o QR para a câmara.</p>
              <button type="button" onclick="totemFecharCamera()"
                class="w-full mt-2 py-3 rounded-2xl border border-stone-300 text-stone-600 font-semibold hover:bg-stone-50 text-sm">Cancelar</button>
            </div>
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
          <h2 class="text-2xl font-bold text-coffee-950">Painel Operacional da Equipe</h2>
          <p class="text-sm text-stone-500">Acompanhamento em tempo real de presença e pausas</p>
        </div>
        <div class="flex items-center space-x-2">
          <span id="ultima-atualizacao" class="text-xs text-stone-400 font-mono">--:--:--</span>
          <button onclick="refreshData()" class="px-3.5 py-2 rounded-xl bg-white border border-stone-200 text-stone-700 font-semibold hover:bg-stone-50 transition-all text-xs flex items-center space-x-1 shadow-sm">
            <i data-lucide="refresh-cw" class="w-4 h-4"></i>
            <span>Atualizar</span>
          </button>
        </div>
      </div>

      <!-- Resumo operacional.
           Quatro numeros compactos, e cada um filtra a lista ao ser tocado --
           e o gesto que toda a gente tenta. Trocaram-se os que nao serviam:
           "Em Atendimento" era total menos pausas, uma conta sem pergunta por
           tras, e "Pausa Cafe" repetia o numero que a tarjeta de baixo ja da
           com nomes. Entra "Ainda sem pausa", que e o que permite escalonar as
           saidas antes de ficar sem gente ao balcao. -->
      <div class="grid grid-cols-2 lg:grid-cols-4 gap-3">
        <button type="button" onclick="aplicarFiltroEquipe('todos')"
          class="text-left bg-white px-4 py-3 rounded-xl border border-stone-200 shadow-sm hover:border-stone-300 transition-colors">
          <div class="flex items-center justify-between text-stone-400 mb-1">
            <span class="text-[10px] font-bold uppercase tracking-wider">Equipe</span>
            <i data-lucide="users" class="w-3.5 h-3.5"></i>
          </div>
          <div id="stat-total" class="text-xl font-bold text-coffee-950 leading-none">0</div>
        </button>

        <button type="button" onclick="aplicarFiltroEquipe('pausa')"
          class="text-left bg-white px-4 py-3 rounded-xl border border-amber-200 shadow-sm hover:border-amber-300 transition-colors">
          <div class="flex items-center justify-between text-amber-600 mb-1">
            <span class="text-[10px] font-bold uppercase tracking-wider">Em pausa</span>
            <i data-lucide="coffee" class="w-3.5 h-3.5"></i>
          </div>
          <div id="stat-coffee" class="text-xl font-bold text-amber-700 leading-none">0</div>
        </button>

        <button type="button" onclick="aplicarFiltroEquipe('sem-pausa')"
          class="text-left bg-white px-4 py-3 rounded-xl border border-stone-200 shadow-sm hover:border-stone-300 transition-colors">
          <div class="flex items-center justify-between text-stone-400 mb-1">
            <span class="text-[10px] font-bold uppercase tracking-wider">Ainda sem pausa</span>
            <i data-lucide="clock" class="w-3.5 h-3.5"></i>
          </div>
          <div id="stat-sem-pausa" class="text-xl font-bold text-coffee-950 leading-none">0</div>
        </button>

        <button type="button" onclick="switchTab('codes')"
          class="text-left bg-white px-4 py-3 rounded-xl border border-stone-200 shadow-sm hover:border-stone-300 transition-colors">
          <div class="flex items-center justify-between text-stone-400 mb-1">
            <span class="text-[10px] font-bold uppercase tracking-wider">Códigos vivos</span>
            <i data-lucide="key-round" class="w-3.5 h-3.5"></i>
          </div>
          <div id="stat-codigos-pendentes" class="text-xl font-bold text-coffee-950 leading-none">0</div>
        </button>
      </div>

      <!-- Quem está fora agora.
           A pergunta que o Supervisor faz o dia inteiro merece resposta sem
           cliques: o chip "Em pausa" obriga a filtrar e, de caminho, esconde
           toda a gente. Esta lista vem do /supervisor/pausas/ativas, que ja
           devolve exactamente estas pessoas -- ate aqui o painel desfazia essa
           lista para decorar as cem linhas da equipa. -->
      <div class="bg-white rounded-2xl border border-stone-200 shadow-sm overflow-hidden">
        <div class="px-4 md:px-6 py-4 border-b border-stone-100 flex items-center justify-between gap-3">
          <h3 class="font-bold text-coffee-950 text-base flex items-center gap-2">
            <i data-lucide="coffee" class="w-5 h-5 text-amber-600"></i>
            <span>Quem está fora agora</span>
          </h3>
          <div class="flex items-center gap-3">
            <span id="fora-agora-count" class="text-xs font-medium text-stone-500"></span>
            <button type="button" id="avisos-toggle" onclick="alternarAvisos()"
              title="Avisar quando alguém sai ou volta do café" aria-pressed="false"
              class="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg border border-stone-200 text-stone-500 text-xs font-semibold hover:bg-stone-50 transition-colors">
              <i data-lucide="bell-off" class="w-3.5 h-3.5"></i><span>Avisos</span>
            </button>
          </div>
        </div>
        <!-- Em hora de ponta podem ser vinte: a lista rola em vez de empurrar
             o resto do painel para fora do ecra. -->
        <div id="fora-agora-lista" class="divide-y divide-stone-100 max-h-80 overflow-y-auto"></div>
      </div>

      <!-- Movimentacao.
           Por omissao mostra so quem esta em pausa ou com codigo vivo. As cem
           linhas de quem esta simplesmente a trabalhar nao respondem a nenhuma
           pergunta de operacao, e essa lista ja existe duas vezes -- na aba
           Codigos, para emitir, e na Gestao, para editar. O chip "Todos"
           continua aqui para quem precisar dela. -->
      <div class="bg-white rounded-2xl border border-stone-200 overflow-hidden shadow-sm">
        <div class="px-4 md:px-6 py-4 border-b border-stone-100 space-y-3">
          <div class="flex items-center justify-between gap-3 flex-wrap">
            <h3 class="font-bold text-coffee-950 text-base">Movimentação</h3>
            <span id="team-count" class="text-xs font-medium text-stone-500">Atualizado ao vivo</span>
          </div>
          <!-- Com uma equipa de uma centena de pessoas, uma lista sem busca
               obriga a percorrer tudo com o olho para encontrar alguem. -->
          <div class="flex flex-col lg:flex-row lg:items-center gap-2.5">
            <div class="relative flex-1 min-w-0">
              <i data-lucide="search" class="w-4 h-4 text-stone-400 absolute left-3 top-1/2 -translate-y-1/2 pointer-events-none"></i>
              <input id="team-search" type="search" autocomplete="off" placeholder="Buscar por nome..."
                aria-label="Buscar colaborador por nome" oninput="buscarEquipe(this.value)"
                class="w-full pl-9 pr-3 py-2 rounded-xl border border-stone-200 bg-stone-50 text-sm focus:outline-none focus:border-amberAccent" />
            </div>
            <select id="team-setor" aria-label="Filtrar por setor" onchange="filtrarSetorEquipe(this.value)"
              class="shrink-0 px-3 py-2 rounded-xl border border-stone-200 bg-stone-50 text-sm text-stone-700 focus:outline-none focus:border-amberAccent">
              <option value="">Todos os setores</option>
            </select>
            <div role="group" aria-label="Filtrar por estado" class="shrink-0 flex items-center bg-stone-100 p-1 rounded-xl border border-stone-200">
              <button type="button" data-filtro-equipe="atividade" onclick="aplicarFiltroEquipe('atividade')" aria-pressed="true"
                class="px-3 py-1.5 rounded-lg text-xs font-semibold transition-all bg-coffee-900 text-white shadow-sm">Com atividade</button>
              <button type="button" data-filtro-equipe="turno" onclick="aplicarFiltroEquipe('turno')" aria-pressed="false"
                class="px-3 py-1.5 rounded-lg text-xs font-semibold transition-all text-stone-600 hover:text-stone-900">Em turno</button>
              <button type="button" data-filtro-equipe="pausa" onclick="aplicarFiltroEquipe('pausa')" aria-pressed="false"
                class="px-3 py-1.5 rounded-lg text-xs font-semibold transition-all text-stone-600 hover:text-stone-900">Em pausa</button>
              <button type="button" data-filtro-equipe="sem-pausa" onclick="aplicarFiltroEquipe('sem-pausa')" aria-pressed="false"
                class="px-3 py-1.5 rounded-lg text-xs font-semibold transition-all text-stone-600 hover:text-stone-900">Ainda sem pausa</button>
              <button type="button" data-filtro-equipe="todos" onclick="aplicarFiltroEquipe('todos')" aria-pressed="false"
                class="px-3 py-1.5 rounded-lg text-xs font-semibold transition-all text-stone-600 hover:text-stone-900">Todos</button>
            </div>
          </div>
        </div>
        <div class="divide-y divide-stone-100" id="team-list">
          <!-- Collaborators rendered dynamically -->
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
          <div class="flex items-center gap-1.5 mr-1">
            <!-- Escrever duas datas para ver a semana e trabalho a mais para a
                 pergunta que se faz todos os dias. -->
            <button type="button" onclick="periodoRapido(0)" class="px-2.5 py-2 rounded-lg border border-stone-200 text-stone-600 text-xs font-semibold hover:bg-stone-50">Hoje</button>
            <button type="button" onclick="periodoRapido(6)" class="px-2.5 py-2 rounded-lg border border-stone-200 text-stone-600 text-xs font-semibold hover:bg-stone-50">7 dias</button>
            <button type="button" onclick="periodoRapido(29)" class="px-2.5 py-2 rounded-lg border border-stone-200 text-stone-600 text-xs font-semibold hover:bg-stone-50">30 dias</button>
          </div>
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

    <!-- QR EM ECRÃ CHEIO.
         O QR existe para ser lido por uma câmara a meio metro de distância, e
         não para caber num cartão. Ocupa o ecrã todo, em fundo branco: um QR
         pequeno, ou sobre fundo escuro, obriga a aproximar o aparelho até quase
         tocar. O código de seis caracteres vai por baixo, grande, porque quando
         a leitura falha é ele que se digita. -->
    <div id="qr-modal" class="hidden fixed inset-0 z-50 bg-white items-center justify-center p-4">
      <button type="button" onclick="fecharQr()" aria-label="Fechar"
        class="absolute top-4 right-4 w-11 h-11 rounded-2xl border border-stone-200 text-stone-500 hover:bg-stone-50 flex items-center justify-center">
        <i data-lucide="x" class="w-5 h-5"></i>
      </button>

      <div class="w-full max-w-2xl text-center">
        <h3 id="qr-nome" class="text-2xl sm:text-3xl font-extrabold text-coffee-950">—</h3>
        <p id="qr-detalhe" class="text-sm text-stone-500 mt-1">—</p>

        <div class="my-5 sm:my-7 flex justify-center">
          <canvas id="qr-canvas" class="block w-full max-w-[min(78vw,420px)] h-auto"></canvas>
        </div>

        <p id="qr-codigo" class="font-mono text-4xl sm:text-5xl font-extrabold text-coffee-900 tracking-[0.2em] leading-none">—</p>
        <p class="text-xs text-stone-400 mt-2">Se a câmara não ler, digite este código no totem.</p>

        <p class="text-[11px] text-stone-500 mt-6 max-w-md mx-auto leading-relaxed">
          Este QR <span class="font-semibold">é</span> o pase: quem o fotografar pode registar esta pausa.
          Mostre-o ao totem e feche-o a seguir.
        </p>

        <div class="flex gap-2 mt-5 max-w-sm mx-auto">
          <button onclick="baixarQr()" class="flex-1 py-3 rounded-2xl border border-stone-300 text-stone-700 font-semibold hover:bg-stone-50 text-sm">Baixar PNG</button>
          <button onclick="fecharQr()" class="flex-1 py-3 rounded-2xl bg-coffee-900 text-white font-bold hover:bg-coffee-800 text-sm">Fechar</button>
        </div>
      </div>
    </div>

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

      <!-- O backend ja aceita ?acao= e ?limite= (ate 250); o painel nunca os
           enviou e ficava preso aos 100 ultimos eventos de tudo misturado. -->
      <div class="bg-white rounded-2xl border border-stone-200 shadow-sm px-4 md:px-6 py-3 flex items-center gap-2.5 flex-wrap">
        <div class="relative flex-1 min-w-0 max-w-xs">
          <i data-lucide="search" class="w-4 h-4 text-stone-400 absolute left-3 top-1/2 -translate-y-1/2 pointer-events-none"></i>
          <input id="audit-busca" type="search" autocomplete="off" placeholder="Buscar por pessoa ou alvo..."
            aria-label="Buscar evento" oninput="buscarAuditoria(this.value)"
            class="w-full pl-9 pr-3 py-2 rounded-xl border border-stone-200 bg-stone-50 text-sm focus:outline-none focus:border-amberAccent" />
        </div>
        <select id="audit-acao" aria-label="Filtrar por ação" onchange="refreshAudit()"
          class="px-3 py-2 rounded-xl border border-stone-200 bg-stone-50 text-sm text-stone-700 focus:outline-none focus:border-amberAccent">
          <option value="">Todas as ações</option>
        </select>
        <select id="audit-limite" aria-label="Quantidade de eventos" onchange="refreshAudit()"
          class="px-3 py-2 rounded-xl border border-stone-200 bg-stone-50 text-sm text-stone-700 focus:outline-none focus:border-amberAccent">
          <option value="50">50 eventos</option>
          <option value="100" selected>100 eventos</option>
          <option value="250">250 eventos</option>
        </select>
        <span id="audit-count" class="text-xs font-medium text-stone-500 ml-auto"></span>
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
          <p class="text-sm text-stone-500">Seis caracteres, letras e números — um por período, serve para a ida e a volta</p>
        </div>
        <div class="flex items-center gap-2">
          <button onclick="gerarCodigosDoDia()" class="flex items-center space-x-2 px-4 py-2.5 rounded-xl bg-coffee-900 text-white text-sm font-semibold shadow-sm hover:bg-coffee-800">
            <i data-lucide="calendar-check" class="w-4 h-4"></i>
            <span>Códigos do dia</span>
          </button>
          <button onclick="refreshCodes()" class="flex items-center space-x-2 px-4 py-2.5 rounded-xl bg-white border border-stone-200 text-stone-700 text-sm font-semibold shadow-sm hover:bg-stone-50">
            <i data-lucide="refresh-cw" class="w-4 h-4"></i>
            <span>Atualizar</span>
          </button>
        </div>
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
        <div class="px-4 md:px-6 py-4 border-b border-stone-100 space-y-3">
          <div class="flex items-center justify-between gap-3 flex-wrap">
            <div>
              <h3 class="font-bold text-coffee-950 text-base">Emitir para um colaborador</h3>
              <p class="text-xs text-stone-500 mt-0.5">O código vale por poucos minutos; para o retorno ele não expira.</p>
            </div>
            <span id="codes-people-count" class="text-xs font-medium text-stone-500"></span>
          </div>
          <!-- Esta lista tem a equipa inteira. Sem busca, emitir um codigo para
               uma pessoa obriga a percorrer uma centena de linhas com a pessoa
               a espera em frente ao balcao. -->
          <div class="relative">
            <i data-lucide="search" class="w-4 h-4 text-stone-400 absolute left-3 top-1/2 -translate-y-1/2 pointer-events-none"></i>
            <input id="codes-search" type="search" autocomplete="off" placeholder="Buscar quem precisa do código..."
              aria-label="Buscar colaborador para emitir código" oninput="buscarPessoaCodigo(this.value)"
              class="w-full pl-9 pr-3 py-2 rounded-xl border border-stone-200 bg-stone-50 text-sm focus:outline-none focus:border-amberAccent" />
          </div>
        </div>
        <div class="divide-y divide-stone-100" id="codes-people-list">
          <p class="px-6 py-8 text-xs text-stone-400 text-center">Carregando…</p>
        </div>
      </div>
    </section>

    <section id="view-history" class="hidden space-y-6">
      <div class="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div>
          <h2 class="text-2xl font-bold text-coffee-950">Registros de Pausa</h2>
          <p class="text-sm text-stone-500">Saídas e retornos do café, por dia</p>
        </div>
        <div class="flex items-end gap-2 flex-wrap">
          <div>
            <label for="hist-data" class="block text-xs font-semibold text-stone-600 mb-1">Dia</label>
            <input type="date" id="hist-data" onchange="refreshHistorico()"
              class="px-3 py-2.5 rounded-xl border border-stone-200 bg-stone-50 text-sm focus:outline-none focus:border-amberAccent">
          </div>
          <button onclick="irParaHoje()" class="px-3.5 py-2.5 rounded-xl bg-white border border-stone-200 text-stone-700 text-xs font-semibold shadow-sm hover:bg-stone-50">Hoje</button>
          <button onclick="refreshHistorico()" class="px-3.5 py-2.5 rounded-xl bg-white border border-stone-200 text-stone-700 text-xs font-semibold shadow-sm hover:bg-stone-50 flex items-center gap-1.5">
            <i data-lucide="refresh-cw" class="w-4 h-4"></i><span>Atualizar</span>
          </button>
        </div>
      </div>

      <div class="bg-white rounded-2xl border border-stone-200 shadow-sm px-4 md:px-6 py-3 flex items-center justify-between gap-3 flex-wrap">
        <div class="relative flex-1 min-w-0 max-w-sm">
          <i data-lucide="search" class="w-4 h-4 text-stone-400 absolute left-3 top-1/2 -translate-y-1/2 pointer-events-none"></i>
          <input id="hist-busca" type="search" autocomplete="off" placeholder="Buscar por nome..."
            aria-label="Buscar registro por nome" oninput="buscarHistorico(this.value)"
            class="w-full pl-9 pr-3 py-2 rounded-xl border border-stone-200 bg-stone-50 text-sm focus:outline-none focus:border-amberAccent" />
        </div>
        <span id="hist-count" class="text-xs font-medium text-stone-500"></span>
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
                <th class="px-6 py-3.5">Período</th>
                <th class="px-6 py-3.5">Saída</th>
                <th class="px-6 py-3.5">Retorno</th>
                <th class="px-6 py-3.5">Duração</th>
                <th class="px-6 py-3.5">Situação</th>
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
          <p class="text-sm text-stone-500">Cadastre colaboradores e parametrize as regras de pausa</p>
        </div>
        <button onclick="openNewCollaboratorModal()" class="px-4 py-2.5 rounded-xl bg-coffee-800 hover:bg-coffee-900 text-white font-semibold transition-all text-xs flex items-center space-x-1.5 shadow-md">
          <i data-lucide="user-plus" class="w-4 h-4"></i>
          <span>Novo Colaborador</span>
        </button>
      </div>

      <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <!-- Collaborators List Card -->
        <div class="lg:col-span-2 bg-white rounded-2xl border border-stone-200 p-6 shadow-sm">
          <div class="flex items-center justify-between gap-3 flex-wrap mb-3">
            <h3 class="font-bold text-coffee-950 text-base">Cadastro da Equipe</h3>
            <span id="admin-count" class="text-xs font-medium text-stone-500"></span>
          </div>
          <!-- Mesma razao do Painel Equipe: uma centena de linhas sem busca
               obriga a percorrer tudo com o olho. -->
          <div class="relative mb-3">
            <i data-lucide="search" class="w-4 h-4 text-stone-400 absolute left-3 top-1/2 -translate-y-1/2 pointer-events-none"></i>
            <input id="admin-busca" type="search" autocomplete="off" placeholder="Buscar colaborador..."
              aria-label="Buscar colaborador cadastrado" oninput="buscarAdmin(this.value)"
              class="w-full pl-9 pr-3 py-2 rounded-xl border border-stone-200 bg-stone-50 text-sm focus:outline-none focus:border-amberAccent" />
          </div>
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
          <h3 class="font-bold text-coffee-950 text-base">Políticas de Pausa</h3>
          
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
        <h3 id="col-modal-titulo" class="text-xl font-bold text-coffee-950 mb-1">Cadastrar Colaborador</h3>
        <p id="col-modal-texto" class="text-xs text-stone-500 mb-6">O colaborador não tem senha nem PIN: quem libera a pausa é o código de café de 6 caracteres que o Supervisor emite na hora.</p>

        <form id="new-col-form" onsubmit="handleSalvarColaborador(event)" class="space-y-4">
          <div>
            <label class="block text-xs font-semibold text-stone-700 mb-1">Nome Completo</label>
            <input type="text" id="col-name" required placeholder="Ex: Patrícia Alves" class="w-full px-3.5 py-2.5 border border-stone-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-amber-500">
          </div>

          <div>
            <label class="block text-xs font-semibold text-stone-700 mb-1">Matrícula</label>
            <input type="text" id="col-matricula" placeholder="Ex: 047" class="w-full px-3.5 py-2.5 border border-stone-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-amber-500">
            <p class="text-[11px] text-stone-400 mt-1">É o atalho do totem: digitar o número encontra a pessoa mais depressa do que o nome.</p>
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
            <button type="submit" id="col-modal-salvar" class="px-5 py-2 rounded-xl bg-coffee-800 hover:bg-coffee-900 text-white text-xs font-semibold shadow">Salvar Colaborador</button>
          </div>
        </form>
      </div>
    </div>

    <!-- Vincular conta a colaborador.
         Vincular tem consequencia: a partir daqui a pessoa conta como
         colaboradora nos relatorios e as pausas dela medem-se pelos mesmos
         limites. O dialogo diz isso antes, e nao depois. -->
    <div id="vinculo-modal" class="hidden fixed inset-0 z-50 bg-stone-900/60 backdrop-blur-sm items-center justify-center p-4">
      <div class="bg-white w-full max-w-md rounded-3xl p-6 sm:p-8 shadow-2xl border border-stone-200">
        <h3 class="text-xl font-bold text-coffee-950 mb-1">Vincular colaborador</h3>
        <p id="vinculo-conta" class="text-xs text-stone-500 mb-5">—</p>

        <div class="relative mb-2">
          <i data-lucide="search" class="w-4 h-4 text-stone-400 absolute left-3 top-1/2 -translate-y-1/2 pointer-events-none"></i>
          <input id="vinculo-busca" type="search" autocomplete="off" placeholder="Buscar colaborador..."
            aria-label="Buscar colaborador para vincular" oninput="renderOpcoesVinculo()"
            class="w-full pl-9 pr-3 py-2.5 rounded-xl border border-stone-200 bg-stone-50 text-sm focus:outline-none focus:border-amberAccent" />
        </div>
        <div id="vinculo-lista" class="max-h-56 overflow-y-auto divide-y divide-stone-100 border border-stone-100 rounded-xl"></div>

        <div class="flex items-start gap-2 p-3 rounded-xl bg-amber-50 border border-amber-200 mt-3">
          <i data-lucide="info" class="w-4 h-4 text-amber-600 mt-0.5 shrink-0"></i>
          <p class="text-[11px] text-amber-800">A partir do vínculo esta pessoa passa a contar como colaboradora: aparece nos relatórios e as pausas dela medem-se pelos mesmos limites.</p>
        </div>

        <div class="flex items-center justify-between gap-2 pt-4 mt-3 border-t border-stone-100">
          <button type="button" onclick="salvarVinculo(null)" class="px-3 py-2 rounded-xl border border-stone-300 text-stone-600 text-xs font-semibold hover:bg-stone-50">Desvincular</button>
          <button type="button" onclick="fecharVinculo()" class="px-4 py-2 rounded-xl border border-stone-300 text-stone-600 text-xs font-semibold hover:bg-stone-50">Cancelar</button>
        </div>
      </div>
    </div>

    <!-- Pausa manual (abrir ou encerrar).
         O backend exige 20 caracteres de motivo. Isso nao e um capricho de
         validacao: a pausa normal e autenticada pelo codigo de cafe, e a manual
         so pode ser autenticada pela responsabilidade de alguem com nome. O
         dialogo explica a exigencia em vez de devolver um 400 seco. -->
    <div id="manual-pause-modal" class="hidden fixed inset-0 z-50 bg-stone-900/60 backdrop-blur-sm items-center justify-center p-4">
      <div class="bg-white w-full max-w-md rounded-3xl p-6 sm:p-8 shadow-2xl border border-stone-200">
        <h3 id="manual-pause-titulo" class="text-xl font-bold text-coffee-950 mb-1">Encerrar pausa</h3>
        <p id="manual-pause-pessoa" class="text-xs text-stone-500 mb-5"></p>

        <form id="manual-pause-form" onsubmit="confirmarPausaManual(event)" class="space-y-3">
          <div>
            <label for="manual-pause-motivo" class="block text-xs font-semibold text-stone-700 mb-1">Motivo</label>
            <textarea id="manual-pause-motivo" rows="3" oninput="contarMotivo()" required
              placeholder="Ex: voltou do café e o totem estava sem rede"
              class="w-full px-3.5 py-2.5 border border-stone-200 rounded-xl text-sm resize-none focus:outline-none focus:ring-2 focus:ring-amber-500"></textarea>
            <p id="manual-pause-contador" class="text-[11px] text-stone-400 mt-1">Faltam 20 caracteres.</p>
          </div>

          <div class="flex items-start gap-2 p-3 rounded-xl bg-amber-50 border border-amber-200">
            <i data-lucide="shield-alert" class="w-4 h-4 text-amber-600 mt-0.5 shrink-0"></i>
            <p class="text-[11px] text-amber-800">Fica registado na auditoria com o seu nome. O painel de operação conta os registos manuais dos últimos 7 dias.</p>
          </div>

          <div class="flex items-center justify-end space-x-2 pt-3 border-t border-stone-100">
            <button type="button" onclick="fecharPausaManual()" class="px-4 py-2 rounded-xl border border-stone-300 text-stone-600 text-xs font-semibold hover:bg-stone-50">Cancelar</button>
            <button type="submit" id="manual-pause-confirmar" disabled
              class="px-5 py-2 rounded-xl bg-coffee-800 text-white text-xs font-semibold shadow disabled:opacity-40 disabled:cursor-not-allowed hover:bg-coffee-900">Confirmar</button>
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

    // O cabecalho da lista promete "ao vivo" desde sempre, mas nada repetia a
    // leitura: quem deixasse o painel aberto ficava a olhar para uma fotografia
    // antiga. Vinte segundos chega para uma pausa de quinze minutos.
    //
    // Com o separador escondido nao se pede nada: ninguem esta a ver, e o
    // refresh seguinte corrige tudo de uma vez quando a pessoa voltar.
    setInterval(() => {
      if (document.hidden) return;
      if (!getToken()) return;
      refreshData();
    }, 20000);

    // O relogio da pausa anda todo o segundo, mesmo entre leituras.
    setInterval(() => {
      if (document.hidden) return;
      atualizarContadores();
      atualizarPrazosCodigos();
    }, 1000);
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
          btn.className = 'shrink-0 flex items-center space-x-1.5 px-3 py-2 rounded-lg transition-all bg-coffee-900 text-white shadow-sm';
          btn.setAttribute('aria-current', 'page');
          view.classList.remove('hidden');
          // Sao oito abas numa faixa que rola no eixo X: num ecra estreito a
          // activa pode ficar fora do campo de visao, e a barra deixa de
          // responder a pergunta 'onde estou?'. Trazemo-la para dentro.
          btn.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'nearest' });
        } else {
          btn.className = 'shrink-0 flex items-center space-x-1.5 px-3 py-2 rounded-lg transition-all text-stone-600 hover:text-stone-900';
          btn.removeAttribute('aria-current');
          view.classList.add('hidden');
        }
      });
      // Sair da aba do totem apaga o código a meio: o próximo a chegar não
      // pode encontrar os caracteres de outra pessoa nas caixas.
      if (tabId === 'kiosk') { totemBoot(); } else { totemFecharCamera(); totemLimparCodigo(); }
      // O prazo do código corre em segundos: ao abrir a aba, relê do servidor.
      if (tabId === 'codes') { refreshCodes(); }
      if (tabId === 'history') { refreshHistorico(); }
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
        totemAtualizarQr();
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

    // ---- Leitor de QR do quiosque ------------------------------------------
    //
    // O QR traz PONTOCAFE1|<uuid>|<codigo>: resolve de uma vez os dois passos
    // que de outro modo sao procurar-se na lista e digitar seis caracteres.
    //
    // Tres condicoes para o botao aparecer, e a ausencia e a resposta:
    //   * o aparelho tem o QR liberado (por aparelho, decidido na Gestao);
    //   * o navegador sabe ler codigos (BarcodeDetector);
    //   * a pagina esta num contexto seguro -- a camara exige HTTPS ou
    //     localhost, por isso entrar pelo IP da rede nao serve.

    const camera = { stream: null, timer: null, lendo: false };

    async function totemAtualizarQr() {
      const botao = document.getElementById('totem-qr-botao');
      if (!botao) return;
      let liberado = false;
      try {
        const h = await deviceFetch('/ponto/horario');
        liberado = !!(h && h.qrHabilitado);
      } catch (_) { liberado = false; }

      const suportado = typeof window !== 'undefined' && 'BarcodeDetector' in window && window.isSecureContext;
      botao.classList.toggle('hidden', !(liberado && suportado));
    }

    async function totemAbrirCamera() {
      const caixa = document.getElementById('totem-camera');
      const video = document.getElementById('totem-video');
      const aviso = document.getElementById('totem-camera-aviso');
      if (!caixa || !video) return;

      caixa.classList.remove('hidden');
      aviso.textContent = 'A pedir acesso à câmara...';
      try {
        // A traseira, que e a que aponta para quem esta em frente ao quiosque.
        camera.stream = await navigator.mediaDevices.getUserMedia({
          video: { facingMode: 'environment' },
        });
      } catch (_) {
        aviso.textContent = 'Sem acesso à câmara. Digite o código.';
        return;
      }

      video.srcObject = camera.stream;
      await video.play().catch(() => {});
      aviso.textContent = 'Aponte o QR para a câmara.';

      const detector = new window.BarcodeDetector({ formats: ['qr_code'] });
      camera.lendo = true;
      camera.timer = setInterval(async () => {
        if (!camera.lendo) return;
        let codigos = [];
        try { codigos = await detector.detect(video); } catch (_) { return; }
        const bruto = codigos[0] && codigos[0].rawValue;
        if (bruto) totemLerQr(bruto);
      }, 400);
    }

    function totemFecharCamera() {
      camera.lendo = false;
      clearInterval(camera.timer);
      camera.timer = null;
      if (camera.stream) {
        // Parar cada faixa, e nao so esconder o video: a luz da camara tem de
        // apagar-se: e o unico sinal que diz a quem esta na sala que ninguem
        // esta a filmar.
        camera.stream.getTracks().forEach((t) => t.stop());
        camera.stream = null;
      }
      const video = document.getElementById('totem-video');
      if (video) video.srcObject = null;
      const caixa = document.getElementById('totem-camera');
      if (caixa) caixa.classList.add('hidden');
    }

    /** Mesmo formato que o app Android le: PONTOCAFE1|<uuid>|<codigo>. */
    function totemLerQr(bruto) {
      const partes = String(bruto).trim().split('|');
      if (partes.length !== 3 || partes[0] !== 'PONTOCAFE1') return;
      const [, colaboradorId, codigo] = partes;

      const pessoa = totem.pessoas.find((p) => p.id === colaboradorId);
      if (!pessoa) {
        // O servidor ja tira da lista quem fechou a pausa deste periodo. Dizer
        // isto aqui da a frase certa de imediato, em vez de a ir buscar a um 403.
        const aviso = document.getElementById('totem-camera-aviso');
        if (aviso) aviso.textContent = 'Este QR não corresponde a ninguém disponível agora.';
        return;
      }

      camera.lendo = false;
      totemFecharCamera();
      totemEscolher(colaboradorId).then(() => {
        // O codigo entra sozinho: quem mostrou o QR nao tem de o digitar.
        totem.codigo = String(codigo).toUpperCase().slice(0, TOTEM_CODE_LENGTH);
        renderTotemBoxes();
        if (totem.codigo.length === TOTEM_CODE_LENGTH) totemRegistrar();
      });
    }

    function totemAbrirLista() {
      const painel = document.getElementById('totem-combo-painel');
      const botao = document.getElementById('totem-combo-botao');
      const busca = document.getElementById('totem-busca');
      if (!painel) return;
      const abrir = painel.classList.contains('hidden');
      painel.classList.toggle('hidden', !abrir);
      if (botao) botao.setAttribute('aria-expanded', String(abrir));
      if (abrir && busca) {
        // Campo limpo a cada abertura: quem chega a seguir nao encontra a busca
        // de quem esteve antes, nem fica a saber quem foi.
        busca.value = '';
        renderTotemPessoas();
        busca.focus();
      }
    }

    function totemFecharLista() {
      const painel = document.getElementById('totem-combo-painel');
      const botao = document.getElementById('totem-combo-botao');
      if (painel) painel.classList.add('hidden');
      if (botao) botao.setAttribute('aria-expanded', 'false');
    }

    // Tocar ao lado fecha. Num quiosque isto conta mais do que num ecra pessoal:
    // a lista nao pode ficar aberta atras de quem ja se foi embora.
    document.addEventListener('click', (e) => {
      const combo = document.getElementById('totem-combo');
      if (combo && !combo.contains(e.target)) totemFecharLista();
    });

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
      totemFecharLista();
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
      totemFecharCamera();
      document.getElementById('totem-step-codigo').classList.add('hidden');
      document.getElementById('totem-step-recibo').classList.add('hidden');
      document.getElementById('totem-step-pessoa').classList.remove('hidden');
      totemFecharLista();
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

    // Ao recarregar a pagina so sobrevive o token: quem esta ligado, e com que
    // papel, tem de ser perguntado outra vez. Sem isto, um Administrador que
    // recarregasse deixava de ver as accoes de Administrador.
    async function recuperarSessao() {
      if (state.usuario || !getToken()) return;
      try {
        const res = await fetch(API_BASE + '/api/auth/get-session', {
          headers: { 'Authorization': 'Bearer ' + getToken() }
        });
        if (!res.ok) return;
        const data = await res.json().catch(() => ({}));
        if (data && data.user) { state.usuario = data.user; pintarPerfil(); }
      } catch (_) { /* sem sessao, fica o que o backend disser em cada rota */ }
    }

    function ehAdmin() {
      return ((state.usuario && state.usuario.role) || '').toLowerCase() === 'admin';
    }

    async function refreshData() {
      if (!getToken()) { showLogin(); return; }
      await recuperarSessao();
      carregarMeuCodigo();
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

        // Marca do momento da leitura. O contador da pausa anda no navegador a
        // partir daqui, em vez de ficar parado ate ao proximo refresh.
        state.lidoEm = Date.now();

        state.collaborators = (pessoas.colaboradores || []).map(c => {
          const pausa = emPausaPorId[c.id];
          return {
            id: c.id,
            name: c.nome,
            role: c.turno ? 'Turno ' + c.turno : 'Sem turno',
            department: c.setor || 'Sem setor',
            matricula: c.matricula || '',
            setorCru: c.setor || '',
            turnoCru: c.turno || '',
            // Tem um passe vivo: e quem esta prestes a sair, ou ja saiu.
            codigoAtivo: !!c.codigoAtivo,
            status: c.emPausa ? 'coffee_break' : 'active',
            // Ja gozou a pausa deste periodo. Responde a outra metade da
            // pergunta do supervisor: nao "quem esta fora", mas "quem ainda
            // nem saiu" -- que e o que permite escalonar as saidas.
            pausaConcluida: !!c.pausaPeriodoConcluida,
            pausa: pausa ? {
              inicioLocal: pausa.inicioLocal,
              tempoSegundos: pausa.tempoSegundos ?? 0,
              limiteSegundos: pausa.limiteSegundos ?? 0,
              carenciaSegundos: pausa.carenciaSegundos ?? 0
            } : null,
            lastPontoType: pausa ? 'Em pausa desde' : (c.codigoAtivo ? 'Código ativo' : null),
            lastPontoTime: pausa ? pausa.inicioLocal : null
          };
        });

        const carimbo = document.getElementById('ultima-atualizacao');
        if (carimbo) carimbo.textContent = new Date().toLocaleTimeString('pt-BR', { hour12: false });

        detectarMovimentacao();
        renderForaAgora();
        renderTeamList();
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
      const pessoas = state.collaborators || [];
      el('stat-total', r.colaboradoresAtivos ?? pessoas.length);
      el('stat-coffee', pessoas.filter(c => c.status === 'coffee_break').length);
      // Nem esta fora agora, nem ja gozou a pausa deste periodo: e o mesmo
      // criterio do chip, para o numero e a lista nunca discordarem.
      el('stat-sem-pausa', pessoas.filter(c => c.status !== 'coffee_break' && !c.pausaConcluida).length);
      el('stat-codigos-pendentes', pessoas.filter(c => c.codigoAtivo).length);
    }

    // Estado dos filtros do Painel Equipe. Vive fora do render para sobreviver
    // ao refresh automatico: quem estava a filtrar nao perde o filtro de dez em
    // dez segundos.
    const filtrosEquipe = { busca: '', setor: '', status: 'atividade' };

    // Sem acentos e sem caixa: quem escreve "araujo" tem de encontrar "Araújo".
    const semAcento = (t) => (t || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();

    function equipeFiltrada() {
      const busca = semAcento(filtrosEquipe.busca.trim());
      return state.collaborators.filter(c => {
        // Em pausa agora, ou com codigo vivo na mao. Quem nao tem nem uma coisa
        // nem outra nao esta a acontecer nada -- e para esse caso existem a
        // Gestao (cadastro) e a aba Codigos (emitir).
        if (filtrosEquipe.status === 'atividade' && !(c.status === 'coffee_break' || c.codigoAtivo)) return false;
        if (filtrosEquipe.status === 'pausa' && c.status !== 'coffee_break') return false;
        if (filtrosEquipe.status === 'turno' && c.status === 'coffee_break') return false;
        // Nem esta fora agora, nem ja gozou a pausa deste periodo.
        if (filtrosEquipe.status === 'sem-pausa' && (c.status === 'coffee_break' || c.pausaConcluida)) return false;
        if (filtrosEquipe.setor && c.department !== filtrosEquipe.setor) return false;
        if (busca && !semAcento(c.name).includes(busca)) return false;
        return true;
      });
    }

    /**
     * Repete no navegador a conta que live-routes faz em SQL:
     *   contado  = max(0, decorrido - carencia)
     *   carencia = decorrido < carencia
     *   excedeu  = decorrido > carencia + limite
     * O decorrido cresce a partir do que veio na ultima leitura, por isso o
     * relogio anda de segundo a segundo sem pedir nada ao servidor. Qualquer
     * desvio e corrigido no refresh seguinte -- quem manda continua a ser o
     * servidor, o navegador so preenche o intervalo.
     */
    function estadoPausa(p) {
      const decorrido = (p.tempoSegundos || 0) + Math.floor((Date.now() - (state.lidoEm || Date.now())) / 1000);
      const carencia = p.carenciaSegundos || 0;
      const limite = p.limiteSegundos || 0;
      const contado = Math.max(0, decorrido - carencia);
      const emCarencia = decorrido < carencia;
      const excedeu = limite > 0 && decorrido > carencia + limite;
      const pct = limite > 0 ? Math.min(100, Math.round((contado / limite) * 100)) : 0;
      // Ambar antes de estourar: avisar aos oitenta por cento da ainda tempo de
      // alguem ir buscar a pessoa; avisar depois so serve para registar a falta.
      //
      // As classes vao inteiras e nao montadas por concatenacao: o Tailwind
      // reconhece nomes literais, e um 'text-' + cor + '-700' e exactamente o
      // tipo de nome que nenhuma ferramenta consegue ver no codigo.
      const paleta = excedeu
        ? { texto: 'text-red-700', barra: 'bg-red-500' }
        : (pct >= 80 ? { texto: 'text-amber-700', barra: 'bg-amber-500' }
                     : { texto: 'text-emerald-700', barra: 'bg-emerald-500' });
      return { contado, limite, emCarencia, excedeu, pct, paleta };
    }

    /**
     * A lista de quem esta fora, ordenada por urgencia.
     *
     * O endpoint devolve por hora de saida; para quem vigia o balcao o que
     * conta e quem esta mais perto de estourar -- esse tem de estar no topo,
     * seja qual for a hora a que saiu.
     *
     * A ordem so e recalculada aqui, no refresh, e nunca no tick de um segundo:
     * linhas a trocar de lugar enquanto alguem as le tornam a lista inutil.
     */
    // ---- Avisos de saída e retorno ----------------------------------------
    //
    // O app Android do Supervisor ja avisa (SupervisorLiveAlerts: SAIDA,
    // RETORNO, MISTO). O painel web nao avisava nada -- quem o deixa aberto no
    // balcao so descobria a movimentacao se estivesse a olhar.
    //
    // A deteccao e por diferenca entre leituras: quem nao estava em pausa e
    // agora esta, saiu; quem estava e ja nao esta, voltou.

    const AVISOS_CHAVE = 'ponto_avisos';
    // Null enquanto nao houver leitura anterior. E o que impede a primeira
    // carga de anunciar como "saida" toda a gente que ja estava fora.
    let pausasConhecidas = null;

    function avisosLigados() {
      return localStorage.getItem(AVISOS_CHAVE) === '1';
    }

    function pintarBotaoAvisos() {
      const botao = document.getElementById('avisos-toggle');
      if (!botao) return;
      const ligado = avisosLigados();
      botao.setAttribute('aria-pressed', String(ligado));
      botao.className = 'flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg border text-xs font-semibold transition-colors ' +
        (ligado ? 'border-amber-200 bg-amber-50 text-amber-700' : 'border-stone-200 text-stone-500 hover:bg-stone-50');
      botao.innerHTML = '<i data-lucide="' + (ligado ? 'bell' : 'bell-off') + '" class="w-3.5 h-3.5"></i><span>Avisos</span>';
      lucide.createIcons();
    }

    async function alternarAvisos() {
      if (avisosLigados()) {
        localStorage.setItem(AVISOS_CHAVE, '0');
        pintarBotaoAvisos();
        showToast('Avisos desligados', 'Deixa de haver notificação de saída e retorno.', 'info');
        return;
      }

      if (!('Notification' in window)) {
        showToast('Sem suporte', 'Este navegador não faz notificações.', 'error');
        return;
      }
      // A permissao so se pede a clique: pedi-la ao carregar a pagina e o que
      // leva as pessoas a negar para sempre, e ai nao ha volta.
      let permissao = Notification.permission;
      if (permissao === 'default') permissao = await Notification.requestPermission();
      if (permissao !== 'granted') {
        showToast('Permissão negada', 'O navegador bloqueou as notificações deste site.', 'error');
        return;
      }

      localStorage.setItem(AVISOS_CHAVE, '1');
      pintarBotaoAvisos();
      showToast('Avisos ligados', 'Aviso de cada saída e cada retorno do café.', 'success');
    }

    function avisar(titulo, mensagem) {
      // O toast aparece sempre: quem esta a olhar para o painel ve a
      // movimentacao mesmo com as notificacoes do sistema desligadas.
      showToast(titulo, mensagem, 'info');
      if (!avisosLigados() || !('Notification' in window) || Notification.permission !== 'granted') return;
      try {
        new Notification(titulo, { body: mensagem, tag: 'ponto-cafe-' + titulo });
      } catch (_) { /* alguns navegadores exigem service worker; o toast fica */ }
    }

    /** Mesma redacao do app Android, para as duas telas falarem igual. */
    function nomesParaAviso(nomes) {
      if (nomes.length === 1) return nomes[0];
      if (nomes.length === 2) return nomes[0] + ' e ' + nomes[1];
      return nomes.slice(0, 2).join(', ') + ' e mais ' + (nomes.length - 2);
    }

    function detectarMovimentacao() {
      const agora = {};
      (state.collaborators || []).forEach(c => { if (c.pausa) agora[c.id] = c.name; });

      if (pausasConhecidas === null) {
        pausasConhecidas = agora;
        return;
      }

      const saidas = Object.keys(agora).filter(id => !(id in pausasConhecidas)).map(id => agora[id]);
      const retornos = Object.keys(pausasConhecidas).filter(id => !(id in agora)).map(id => pausasConhecidas[id]);
      pausasConhecidas = agora;

      if (saidas.length) {
        avisar(
          saidas.length === 1 ? 'Saída para o café' : saidas.length + ' saídas para o café',
          nomesParaAviso(saidas) + (saidas.length === 1 ? ' saiu para o café.' : ' saíram para o café.'),
        );
      }
      if (retornos.length) {
        avisar(
          retornos.length === 1 ? 'Retorno do café' : retornos.length + ' retornos do café',
          nomesParaAviso(retornos) + (retornos.length === 1 ? ' voltou do café.' : ' voltaram do café.'),
        );
      }
    }

    // ---- Pausa manual ------------------------------------------------------
    //
    // A via normal e o codigo de cafe. Esta existe para quando o codigo falhou:
    // expirou com a pessoa ja fora, o totem ficou sem rede, alguem saiu sem
    // registar ou voltou sem marcar. Usa /supervisor/*, aberta a ADMIN e a
    // SUPERVISOR -- so a variante /admin/* e exclusiva do Administrador.

    const MOTIVO_MINIMO = 20;
    const pausaManual = { id: null, acao: null };

    function contarMotivo() {
      const campo = document.getElementById('manual-pause-motivo');
      const contador = document.getElementById('manual-pause-contador');
      const botao = document.getElementById('manual-pause-confirmar');
      if (!campo || !contador || !botao) return;
      const faltam = MOTIVO_MINIMO - campo.value.trim().length;
      botao.disabled = faltam > 0;
      contador.textContent = faltam > 0
        ? 'Faltam ' + faltam + (faltam === 1 ? ' caractere.' : ' caracteres.')
        : campo.value.trim().length + ' caracteres.';
      contador.className = faltam > 0 ? 'text-[11px] text-stone-400 mt-1' : 'text-[11px] text-emerald-600 mt-1';
    }

    function abrirPausaManual(id, acao) {
      const c = (state.collaborators || []).find(x => x.id === id);
      if (!c) return;
      pausaManual.id = id;
      pausaManual.acao = acao;

      const encerrar = acao === 'finalizar';
      document.getElementById('manual-pause-titulo').textContent = encerrar ? 'Encerrar pausa' : 'Iniciar pausa manual';
      document.getElementById('manual-pause-pessoa').textContent = encerrar
        ? c.name + ' · saiu às ' + (c.pausa ? c.pausa.inicioLocal : '--')
        : c.name + ' · será registada como fora a partir de agora';
      document.getElementById('manual-pause-motivo').value = '';
      contarMotivo();

      const modal = document.getElementById('manual-pause-modal');
      modal.classList.remove('hidden');
      modal.classList.add('flex');
      document.getElementById('manual-pause-motivo').focus();
    }

    function fecharPausaManual() {
      const modal = document.getElementById('manual-pause-modal');
      modal.classList.add('hidden');
      modal.classList.remove('flex');
      pausaManual.id = null;
      pausaManual.acao = null;
    }

    async function confirmarPausaManual(e) {
      e.preventDefault();
      const motivo = document.getElementById('manual-pause-motivo').value.trim();
      if (!pausaManual.id || motivo.length < MOTIVO_MINIMO) return;

      const botao = document.getElementById('manual-pause-confirmar');
      botao.disabled = true;
      botao.textContent = 'Confirmando...';
      try {
        const res = await fetch(API_BASE + '/supervisor/pausas/manual/' + pausaManual.acao, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + getToken() },
          body: JSON.stringify({ colaboradorId: pausaManual.id, motivo: motivo })
        });
        const data = await res.json().catch(() => ({}));
        if (res.ok) {
          showToast('Registado', pausaManual.acao === 'finalizar' ? 'Pausa encerrada manualmente.' : 'Pausa iniciada manualmente.', 'success');
          fecharPausaManual();
          await refreshData();
        } else {
          showToast('Erro', data.erro || 'Não foi possível registar.', 'error');
        }
      } finally {
        botao.textContent = 'Confirmar';
        botao.disabled = false;
      }
    }

    function renderForaAgora() {
      const lista = document.getElementById('fora-agora-lista');
      const contador = document.getElementById('fora-agora-count');
      if (!lista) return;

      const fora = (state.collaborators || [])
        .filter(c => c.pausa)
        .map(c => ({ c: c, e: estadoPausa(c.pausa) }))
        .sort((a, b) => (a.e.limite - a.e.contado) - (b.e.limite - b.e.contado));

      if (contador) {
        const acima = fora.filter(x => x.e.excedeu).length;
        contador.textContent = fora.length === 0
          ? 'ninguém fora'
          : fora.length + (fora.length === 1 ? ' pessoa' : ' pessoas') +
            (acima > 0 ? ' · ' + acima + ' acima do limite' : '');
        contador.className = acima > 0
          ? 'text-xs font-semibold text-red-600'
          : 'text-xs font-medium text-stone-500';
      }

      if (fora.length === 0) {
        lista.innerHTML = '<p class="px-6 py-6 text-xs text-stone-400 text-center">Ninguém em pausa agora. A equipa está toda no turno.</p>';
        return;
      }

      lista.innerHTML = fora.map(item => {
        const c = item.c;
        const e = item.e;
        const iniciais = c.name.slice(0, 2).toUpperCase();
        const estado = e.emCarencia ? 'tolerância'
          : (e.excedeu ? 'acima do limite' : segundosParaRelogio(e.limite - e.contado) + ' restantes');
        return '<div class="px-4 md:px-6 py-3 flex items-center gap-3">' +
            '<div class="w-9 h-9 rounded-xl bg-amber-50 border border-amber-200 text-amber-700 font-bold flex items-center justify-center text-xs shrink-0">' + iniciais + '</div>' +
            '<div class="min-w-0 flex-1">' +
              '<h4 class="text-sm font-bold text-coffee-950 truncate">' + c.name + '</h4>' +
              '<p class="text-[11px] text-stone-400 font-mono">saiu às ' + c.pausa.inicioLocal + ' · ' + estado + '</p>' +
            '</div>' +
            '<button type="button" data-col-id="' + c.id + '" data-acao="finalizar" onclick="abrirPausaManual(this.dataset.colId, this.dataset.acao)" ' +
              'title="Encerrar esta pausa manualmente" ' +
              'class="shrink-0 px-2.5 py-1.5 rounded-lg border border-stone-200 text-stone-600 text-xs font-semibold hover:bg-stone-50 transition-colors">Encerrar</button>' +
            '<div class="text-right shrink-0 w-28">' +
              '<div id="fora-tempo-' + c.id + '" class="text-sm font-bold font-mono ' + e.paleta.texto + '">' +
                (e.emCarencia ? 'tolerância' : segundosParaRelogio(e.contado) + ' / ' + segundosParaRelogio(e.limite)) +
              '</div>' +
              '<div class="h-1.5 rounded-full bg-stone-200 overflow-hidden mt-1">' +
                '<div id="fora-barra-' + c.id + '" class="h-full rounded-full transition-all ' + e.paleta.barra + '" style="width:' + e.pct + '%"></div>' +
              '</div>' +
            '</div>' +
          '</div>';
      }).join('');
      lucide.createIcons();
    }

    function contadorPausaHtml(c) {
      if (!c.pausa) {
        return '<div class="text-xs font-semibold text-stone-700">' + (c.lastPontoType || 'Sem registro') + '</div>' +
               '<div class="text-[11px] text-stone-400 font-mono">' + (c.lastPontoTime || '--') + '</div>';
      }
      const e = estadoPausa(c.pausa);
      return '<div id="pausa-tempo-' + c.id + '" class="text-xs font-bold font-mono ' + e.paleta.texto + '">' +
               (e.emCarencia ? 'tolerância' : segundosParaRelogio(e.contado) + ' / ' + segundosParaRelogio(e.limite)) +
             '</div>' +
             '<div class="h-1.5 w-24 rounded-full bg-stone-200 overflow-hidden mt-1 ml-auto">' +
               '<div id="pausa-barra-' + c.id + '" class="h-full rounded-full transition-all ' + e.paleta.barra + '" style="width:' + e.pct + '%"></div>' +
             '</div>' +
             '<div class="text-[11px] text-stone-400 font-mono">desde ' + c.pausa.inicioLocal + '</div>';
    }

    // O tick mexe so nos dois nos do contador. Repintar a lista inteira de
    // segundo a segundo fecharia os historicos que alguem tivesse aberto.
    function atualizarContadores() {
      state.collaborators.forEach(c => {
        if (!c.pausa) return;
        const e = estadoPausa(c.pausa);
        const rotulo = e.emCarencia ? 'tolerância' : segundosParaRelogio(e.contado) + ' / ' + segundosParaRelogio(e.limite);

        // A mesma pausa vive em dois sitios -- a tarjeta do topo e a linha da
        // lista grande -- com nos distintos para nao repetir ids.
        [['pausa', 'text-xs'], ['fora', 'text-sm']].forEach(par => {
          const texto = document.getElementById(par[0] + '-tempo-' + c.id);
          const barra = document.getElementById(par[0] + '-barra-' + c.id);
          if (texto) {
            texto.textContent = rotulo;
            texto.className = par[1] + ' font-bold font-mono ' + e.paleta.texto;
          }
          if (barra) {
            barra.style.width = e.pct + '%';
            barra.className = 'h-full rounded-full transition-all ' + e.paleta.barra;
          }
        });
      });
    }

    function buscarEquipe(valor) {
      filtrosEquipe.busca = valor;
      renderTeamList();
    }

    function filtrarSetorEquipe(valor) {
      filtrosEquipe.setor = valor;
      renderTeamList();
    }

    function aplicarFiltroEquipe(status) {
      filtrosEquipe.status = status;
      document.querySelectorAll('[data-filtro-equipe]').forEach(b => {
        const activo = b.dataset.filtroEquipe === status;
        b.className = 'px-3 py-1.5 rounded-lg text-xs font-semibold transition-all ' +
          (activo ? 'bg-coffee-900 text-white shadow-sm' : 'text-stone-600 hover:text-stone-900');
        b.setAttribute('aria-pressed', String(activo));
      });
      renderTeamList();
    }

    // Os setores saem dos dados, nao de uma lista fixa: um setor novo aparece
    // no filtro sem ninguem tocar no codigo.
    function renderSetorOptions() {
      const sel = document.getElementById('team-setor');
      if (!sel) return;
      const setores = [...new Set(state.collaborators.map(c => c.department).filter(Boolean))].sort();
      const escapa = (t) => t.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/"/g, '&quot;');
      sel.innerHTML = '<option value="">Todos os setores</option>' +
        setores.map(x => '<option value="' + escapa(x) + '">' + escapa(x) + '</option>').join('');
      // Um setor que deixou de existir nao pode continuar a filtrar em silencio:
      // a lista viria vazia sem que nada no ecra explicasse porque.
      sel.value = setores.includes(filtrosEquipe.setor) ? filtrosEquipe.setor : '';
      filtrosEquipe.setor = sel.value;
    }

    function renderTeamList() {
      const container = document.getElementById('team-list');
      if (!container) return;

      renderSetorOptions();
      const lista = equipeFiltrada();

      const contador = document.getElementById('team-count');
      if (contador) {
        contador.textContent = lista.length === state.collaborators.length
          ? state.collaborators.length + ' colaboradores'
          : lista.length + ' de ' + state.collaborators.length + ' colaboradores';
      }

      if (lista.length === 0) {
        // Com o filtro por omissao, a lista vazia e o estado normal de um dia
        // calmo -- e nao um erro. Vale dizer isso, e lembrar onde esta o resto.
        const calmo = filtrosEquipe.status === 'atividade' && !filtrosEquipe.busca.trim() && !filtrosEquipe.setor;
        container.innerHTML = '<div class="px-6 py-10 text-center">' +
          (calmo
            ? '<p class="text-sm font-semibold text-stone-500">Ninguém em pausa nem com código na mão.</p>' +
              '<p class="text-xs text-stone-400 mt-1">A equipa toda está no turno. Use <span class="font-semibold">Todos</span> para ver o resto, ou a aba Códigos para emitir um passe.</p>'
            : '<p class="text-sm font-semibold text-stone-500">Ninguém corresponde a estes filtros.</p>' +
              '<p class="text-xs text-stone-400 mt-1">Tente outro nome, outro setor ou outro estado.</p>') +
          '</div>';
        return;
      }

      container.innerHTML = lista.map(c => {
        // Só há dois estados: a API devolve emPausa, e o painel deriva daí. Não
        // existe "fora de turno" -- o sistema não controla turnos, controla
        // pausas de café.
        const st = c.status === 'coffee_break'
          ? { label: 'Pausa Café', badgeClass: 'bg-amber-50 text-amber-700 border-amber-200' }
          : { label: 'Em Turno', badgeClass: 'bg-emerald-50 text-emerald-700 border-emerald-200' };

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
                <div class="text-right hidden sm:block">\${contadorPausaHtml(c)}</div>
                <span class="px-3 py-1 rounded-full text-xs font-semibold border \${st.badgeClass}">
                  \${st.label}
                </span>
                \${c.status === 'coffee_break' ? '' : '<button type="button" title="Iniciar pausa manual" data-col-id="' + c.id + '" data-acao="iniciar" aria-label="Iniciar pausa manual de ' + c.name.replace(/"/g, '&quot;') + '" onclick="event.stopPropagation(); abrirPausaManual(this.dataset.colId, this.dataset.acao)" class="p-1.5 rounded-lg text-stone-400 hover:text-amber-700 hover:bg-amber-50 transition-colors"><i data-lucide="coffee" class="w-4 h-4"></i></button>'}
                <i data-lucide="chevron-down" class="w-4 h-4 text-stone-400"></i>
              </div>
            </div>
            <div id="hist-\${c.id}" class="hidden"></div>
          </div>
        \`;
      }).join('');
      // O lucide troca <i data-lucide> por <svg> uma vez; o HTML que acabou de
      // entrar ainda tem os <i> por converter.
      lucide.createIcons();
    }

    // Registros vem de GET /supervisor/pausas?data=, que devolve as pausas do
    // dia -- fechadas e abertas. Ate aqui esta aba montava a "historia" a
    // partir de /pausas/ativas, ou seja so quem estava fora naquele instante:
    // chamava-se historico e nunca mostrou passado nenhum.
    const filtroHistorico = { busca: '' };

    function dataHistorico() {
      const campo = document.getElementById('hist-data');
      if (campo && campo.value) return campo.value;
      const hoje = new Date();
      const iso = new Date(hoje.getTime() - hoje.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
      if (campo) campo.value = iso;
      return iso;
    }

    function irParaHoje() {
      const campo = document.getElementById('hist-data');
      if (campo) campo.value = '';
      dataHistorico();
      refreshHistorico();
    }

    function buscarHistorico(valor) {
      filtroHistorico.busca = valor;
      renderHistoryTable();
    }

    async function refreshHistorico() {
      try {
        const data = await apiFetch('/supervisor/pausas?data=' + dataHistorico());
        state.history = data.pausas || [];
      } catch (err) {
        if (err.message !== 'unauthenticated') {
          showToast('Erro', 'Não foi possível carregar os registros: ' + err.message, 'error');
          state.history = [];
        }
      }
      renderHistoryTable();
    }

    function renderHistoryTable() {
      const tbody = document.getElementById('history-table-body');
      const cards = document.getElementById('history-cards');
      if (!tbody) return;

      const busca = semAcento(filtroHistorico.busca.trim());
      const linhas = (state.history || [])
        .filter(p => !busca || semAcento(p.nome || '').includes(busca))
        .slice()
        .reverse();

      const contador = document.getElementById('hist-count');
      if (contador) {
        contador.textContent = linhas.length === (state.history || []).length
          ? linhas.length + (linhas.length === 1 ? ' registro' : ' registros')
          : linhas.length + ' de ' + state.history.length;
      }

      // Aberta, fechada dentro do limite, ou fechada acima dele: sao tres
      // situacoes diferentes e so a ultima pede atencao de alguem.
      function situacao(p) {
        if (!p.fimLocal) return { texto: 'Em pausa', cls: 'text-amber-700 bg-amber-50 border-amber-200' };
        if (p.excedeuLimite) return { texto: 'Acima do limite', cls: 'text-red-700 bg-red-50 border-red-200' };
        return { texto: 'Dentro do limite', cls: 'text-emerald-700 bg-emerald-50 border-emerald-200' };
      }
      const periodoLabel = (p) => p.periodo === 'MANHA' ? 'Manhã' : (p.periodo === 'TARDE' ? 'Tarde' : '—');
      const duracao = (p) => p.duracaoSegundos != null ? segundosParaRelogio(p.duracaoSegundos) : '—';

      if (linhas.length === 0) {
        const vazio = (state.history || []).length === 0
          ? 'Nenhuma pausa registada neste dia.'
          : 'Ninguém com esse nome neste dia.';
        tbody.innerHTML = '<tr><td colspan="6" class="px-6 py-10 text-center text-xs text-stone-400">' + vazio + '</td></tr>';
        if (cards) cards.innerHTML = '<p class="px-5 py-8 text-xs text-stone-400 text-center">' + vazio + '</p>';
        return;
      }

      tbody.innerHTML = linhas.map(p => {
        const st = situacao(p);
        return '<tr class="hover:bg-stone-50/80 transition-colors">' +
          '<td class="px-6 py-4 font-semibold text-coffee-950">' + (p.nome || '—') + '</td>' +
          '<td class="px-6 py-4 text-xs text-stone-500">' + periodoLabel(p) + '</td>' +
          '<td class="px-6 py-4 font-mono text-xs text-stone-600">' + (p.inicioLocal || '—') + '</td>' +
          '<td class="px-6 py-4 font-mono text-xs text-stone-600">' + (p.fimLocal || '—') + '</td>' +
          '<td class="px-6 py-4 font-mono text-xs text-stone-600">' + duracao(p) + '</td>' +
          '<td class="px-6 py-4"><span class="px-2.5 py-1 rounded-full text-xs font-semibold border ' + st.cls + '">' + st.texto + '</span></td>' +
        '</tr>';
      }).join('');

      if (cards) {
        cards.innerHTML = linhas.map(p => {
          const st = situacao(p);
          return '<div class="px-5 py-4">' +
            '<div class="flex items-start justify-between gap-3">' +
              '<h4 class="text-sm font-bold text-coffee-950 min-w-0 truncate">' + (p.nome || '—') + '</h4>' +
              '<span class="shrink-0 font-mono text-xs text-stone-500">' + (p.inicioLocal || '—') +
                (p.fimLocal ? ' → ' + p.fimLocal : '') + '</span>' +
            '</div>' +
            '<div class="flex items-center flex-wrap gap-2 mt-2">' +
              '<span class="px-2.5 py-1 rounded-full text-xs font-semibold border ' + st.cls + '">' + st.texto + '</span>' +
              '<span class="text-[11px] uppercase font-semibold text-stone-400">' + periodoLabel(p) + '</span>' +
              '<span class="text-[11px] font-mono text-stone-400">' + duracao(p) + '</span>' +
            '</div>' +
          '</div>';
        }).join('');
      }
    }

    const filtroAdmin = { busca: '' };

    function buscarAdmin(valor) {
      filtroAdmin.busca = valor;
      renderAdminList();
    }

    function renderAdminList() {
      const container = document.getElementById('admin-collaborator-list');
      if (!container) return;

      const busca = semAcento(filtroAdmin.busca.trim());
      const lista = (state.collaborators || []).filter(c => !busca || semAcento(c.name).includes(busca));

      const contador = document.getElementById('admin-count');
      if (contador) {
        contador.textContent = lista.length === (state.collaborators || []).length
          ? lista.length + ' cadastrados'
          : lista.length + ' de ' + state.collaborators.length;
      }

      if (lista.length === 0) {
        container.innerHTML = '<p class="py-8 text-xs text-stone-400 text-center">Ninguém com esse nome.</p>';
        return;
      }

      // Colaborador não tem PIN neste modelo -- o que aparece é o estado da
      // pausa. O acesso ao café vem do código de 6 caracteres do Supervisor.
      container.innerHTML = lista.map(c => \`
        <div class="py-3 flex items-center justify-between gap-3">
          <div class="min-w-0">
            <h4 class="text-sm font-bold text-coffee-950 truncate">\${c.name}</h4>
            <p class="text-xs text-stone-500 truncate">\${[c.turnoCru ? 'Turno ' + c.turnoCru : '', c.setorCru || ''].filter(Boolean).join(' • ') || 'Sem turno nem setor'}</p>
          </div>
          \${ehAdmin() ? '<button type="button" data-col-id="' + c.id + '" onclick="abrirEdicaoColaborador(this.dataset.colId)" class="shrink-0 px-2.5 py-1.5 rounded-lg border border-stone-200 text-stone-600 text-xs font-semibold hover:bg-stone-50 transition-colors">Editar</button>' : ''}
        </div>
      \`).join('');
      lucide.createIcons();
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
        state.dispositivos = data.dispositivos || [];
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
          ? '<div class="px-6 py-12 text-center">' +
              '<div class="w-12 h-12 rounded-2xl bg-stone-100 border border-stone-200 flex items-center justify-center mx-auto mb-3">' +
                '<i data-lucide="smartphone" class="w-6 h-6 text-stone-400"></i>' +
              '</div>' +
              '<p class="text-sm font-semibold text-stone-600">Nenhum aparelho cadastrado</p>' +
              '<p class="text-xs text-stone-400 mt-1 max-w-sm mx-auto">Um totem precisa de ser cadastrado aqui para receber o código de ativação. Só depois disso o aparelho consegue registar pausas.</p>' +
              '<button type="button" onclick="abrirCadastroDispositivo()" class="mt-4 px-4 py-2 rounded-xl bg-coffee-900 text-white text-xs font-semibold shadow-sm hover:bg-coffee-800">Cadastrar o primeiro</button>' +
            '</div>'
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
                <button onclick="alternarQrDispositivo('\${d.id}', \${d.qrHabilitado ? 'true' : 'false'})"
                  class="px-3 py-1.5 rounded-lg border text-xs font-semibold \${d.qrHabilitado
                    ? 'border-emerald-300 text-emerald-700 hover:bg-emerald-50'
                    : 'border-stone-300 text-stone-600 hover:bg-stone-50'}">\${d.qrHabilitado ? 'QR liberado' : 'Liberar QR'}</button>
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

    // A porta da câmara, aparelho a aparelho.
    //
    // Vai por /gestao e não por /admin de propósito: liberar a leitura é uma
    // decisão do turno, e o Supervisor que está no chão precisa de a poder
    // tomar. Todo o /admin/* está por baixo do requireRole(ADMIN) do backend.
    async function alternarQrDispositivo(id, ligadoAgora) {
      const ligar = !ligadoAgora;
      if (ligar && !confirm(
        'Liberar a leitura por QR neste aparelho?\\n\\n' +
        'O QR contém o mesmo código de 6 caracteres. Quem tiver a imagem pode registrar a pausa daquela pessoa. ' +
        'Cada batida por câmara fica marcada na auditoria.'
      )) return;

      const res = await fetch(API_BASE + '/gestao/devices/' + id + '/qr', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + getToken() },
        body: JSON.stringify({ habilitado: ligar })
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok) {
        showToast('Erro', data.erro || 'O servidor não aceitou a mudança.', 'error');
        return;
      }
      showToast(ligar ? 'QR liberado' : 'QR bloqueado', data.aviso || '', 'success');
      await refreshDevices();
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
    /** Atalhos de periodo. dias=0 e hoje; 6 e a semana; 29 o mes. */
    function periodoRapido(dias) {
      const local = (d) => new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
      const fim = new Date();
      const inicio = new Date(fim.getTime() - dias * 86400000);
      const ei = document.getElementById('rep-inicio');
      const ef = document.getElementById('rep-fim');
      if (ei) ei.value = local(inicio);
      if (ef) ef.value = local(fim);
      refreshReports();
    }

    const filtroAuditoria = { busca: '' };

    function buscarAuditoria(valor) {
      filtroAuditoria.busca = valor;
      renderAuditoria();
    }

    // As accoes saem dos proprios eventos: nao ha lista fixa no backend, e uma
    // accao nova passa a aparecer no filtro sem ninguem tocar no codigo.
    function renderOpcoesAcao() {
      const sel = document.getElementById('audit-acao');
      if (!sel) return;
      const accoes = [...new Set((state.auditoria || []).map(e => e.acao).filter(Boolean))].sort();
      const atual = sel.value;
      sel.innerHTML = '<option value="">Todas as ações</option>' +
        accoes.map(a => '<option value="' + a + '">' + a.replace(/_/g, ' ').toLowerCase() + '</option>').join('');
      sel.value = accoes.includes(atual) ? atual : '';
    }

    function renderAuditoria() {
      const lista = document.getElementById('audit-list');
      if (!lista) return;
      const busca = semAcento(filtroAuditoria.busca.trim());
      const eventos = (state.auditoria || []).filter(e => {
        if (!busca) return true;
        const alvo = (e.detalhes && e.detalhes.nome) ? e.detalhes.nome : (e.entidade || '');
        return semAcento(e.atorNome || '').includes(busca) || semAcento(alvo).includes(busca);
      });

      const contador = document.getElementById('audit-count');
      if (contador) {
        contador.textContent = eventos.length === (state.auditoria || []).length
          ? eventos.length + (eventos.length === 1 ? ' evento' : ' eventos')
          : eventos.length + ' de ' + state.auditoria.length;
      }

      lista.innerHTML = eventos.length === 0
          ? '<p class="px-6 py-8 text-xs text-stone-400 text-center">Nenhum evento com estes filtros.</p>'
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
      lucide.createIcons();
    }

    async function refreshAudit() {
      const lista = document.getElementById('audit-list');
      if (!lista) return;
      try {
        const acao = (document.getElementById('audit-acao') || {}).value || '';
        const limite = (document.getElementById('audit-limite') || {}).value || '100';
        const data = await apiFetch('/admin/auditoria?limite=' + limite + (acao ? '&acao=' + encodeURIComponent(acao) : ''));
        state.auditoria = data.eventos || [];
        // As opcoes so se recalculam sem filtro de accao: com filtro, a lista
        // ficaria reduzida a uma unica opcao -- a que ja esta escolhida.
        if (!acao) renderOpcoesAcao();
        renderAuditoria();
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

    const filtroCodigos = { busca: '' };

    /**
     * Quem ja tem codigo vivo nao aparece aqui: dois codigos ao mesmo tempo
     * deixariam em aberto qual deles fecha a pausa.
     */
    function renderPessoasSemCodigo() {
      const pessoas = document.getElementById('codes-people-list');
      if (!pessoas) return;

      const comCodigo = {};
      (state.codes || []).filter(c => c.estado !== 'EXPIRADO')
        .forEach(c => { comCodigo[c.colaboradorId] = true; });
      const livres = (state.collaborators || []).filter(p => !comCodigo[p.id]);

      const busca = semAcento(filtroCodigos.busca.trim());
      const lista = busca ? livres.filter(p => semAcento(p.name).includes(busca)) : livres;

      const contador = document.getElementById('codes-people-count');
      if (contador) {
        contador.textContent = lista.length === livres.length
          ? livres.length + ' sem código'
          : lista.length + ' de ' + livres.length;
      }

      if (livres.length === 0) {
        pessoas.innerHTML = '<p class="px-6 py-8 text-xs text-stone-400 text-center">Todo mundo já tem código vivo.</p>';
        return;
      }
      if (lista.length === 0) {
        pessoas.innerHTML = '<p class="px-6 py-8 text-xs text-stone-400 text-center">Ninguém com esse nome está sem código.</p>';
        return;
      }

      pessoas.innerHTML = lista.map(p => {
        // "Sem turno • Sem setor" nas cem linhas e ruido: a segunda linha so
        // aparece quando ha mesmo alguma coisa para dizer.
        const detalhe = [p.turnoCru ? 'Turno ' + p.turnoCru : '', p.setorCru || ''].filter(Boolean).join(' • ');
        return '<div class="px-4 md:px-6 py-3 flex items-center justify-between gap-4">' +
            '<div class="min-w-0">' +
              '<h4 class="text-sm font-bold text-coffee-950 truncate">' + p.name + '</h4>' +
              (detalhe ? '<p class="text-xs text-stone-500 truncate">' + detalhe + '</p>' : '') +
            '</div>' +
            '<button type="button" data-pessoa-id="' + p.id + '" onclick="emitirCodigo(this.dataset.pessoaId)" ' +
              'class="shrink-0 flex items-center space-x-1.5 px-3 py-2 rounded-xl bg-amberAccent text-white text-xs font-semibold shadow hover:opacity-95">' +
              '<i data-lucide="coffee" class="w-4 h-4"></i><span>Gerar</span>' +
            '</button>' +
          '</div>';
      }).join('');
      lucide.createIcons();
    }

    function buscarPessoaCodigo(valor) {
      filtroCodigos.busca = valor;
      renderPessoasSemCodigo();
    }

    /**
     * O prazo do codigo sao dois minutos. Mostrado uma vez e nunca mais mexido,
     * o numero ja esta errado quando o Supervisor acaba de o ditar -- e o
     * refresh de vinte segundos so o corrigiria aos saltos, num orcamento de
     * cento e vinte. Conta-se no navegador a partir da ultima leitura.
     */
    function segundosRestantesCodigo(c) {
      if (c.estado === 'EM_PAUSA') return null;
      const decorrido = Math.floor((Date.now() - (state.codigosLidoEm || Date.now())) / 1000);
      return Math.max(0, (c.expiraEmSegundos || 0) - decorrido);
    }

    function atualizarPrazosCodigos() {
      let algumExpirou = false;
      (state.codes || []).forEach(c => {
        const no = document.getElementById('codigo-prazo-' + c.colaboradorId);
        if (!no) return;
        const resta = segundosRestantesCodigo(c);
        if (resta === null) return;
        no.textContent = resta === 0 ? 'expirado' : 'expira em ' + segundosParaRelogio(resta);
        if (resta === 0) algumExpirou = true;
        no.className = resta <= 30 ? 'text-red-600 font-semibold' : 'text-stone-500';
      });
      // Um codigo que chegou a zero deixou de servir: vale ir buscar a lista
      // real em vez de deixar uma linha morta no ecra.
      if (algumExpirou) refreshCodes();
    }

    async function copiarCodigo(texto) {
      try {
        await navigator.clipboard.writeText(texto);
        showToast('Copiado', 'Código ' + texto + ' na área de transferência.', 'success');
      } catch (_) {
        // Sem HTTPS (por IP, por exemplo) o navegador recusa a area de
        // transferencia. Dizer isso e melhor do que falhar em silencio.
        showToast('Não deu para copiar', 'Anote o código: ' + texto, 'info');
      }
    }

    async function refreshCodes() {
      const lista = document.getElementById('codes-list');
      const pessoas = document.getElementById('codes-people-list');
      if (!lista) return;
      try {
        const data = await apiFetch('/supervisor/codigos');
        state.codes = data.codigos || [];
        state.codigosLidoEm = Date.now();

        const vivos = state.codes.filter(c => c.estado !== 'EXPIRADO');
        document.getElementById('codes-count').textContent =
          vivos.length === 0 ? 'nenhum ativo' : vivos.length + ' ativo(s)';

        lista.innerHTML = vivos.length === 0
          ? '<p class="px-6 py-8 text-xs text-stone-400 text-center">Nenhum código vivo agora.</p>'
          : vivos.map(c => {
              const st = ESTADO_CODIGO[c.estado] || ESTADO_CODIGO.EXPIRADO;
              const resta = segundosRestantesCodigo(c);
              const prazo = resta === null
                ? 'válido para o retorno, sem prazo'
                : (resta === 0 ? 'expirado' : 'expira em ' + segundosParaRelogio(resta));
              const periodo = c.periodo === 'MANHA' ? 'Manhã' : (c.periodo === 'TARDE' ? 'Tarde' : null);
              return \`
                <div class="px-4 md:px-6 py-4 flex flex-wrap items-center justify-between gap-3">
                  <div class="min-w-0 flex-1">
                    <div class="flex items-center flex-wrap gap-2">
                      <button type="button" data-codigo="\${c.codigo || c.codigoFormatado}" onclick="copiarCodigo(this.dataset.codigo)"
                        title="Copiar código" aria-label="Copiar código \${c.codigoFormatado}"
                        class="font-mono text-lg font-extrabold text-coffee-900 tracking-widest hover:bg-stone-100 rounded-lg px-1.5 -mx-1.5 transition-colors cursor-pointer">\${c.codigoFormatado}</button>
                      <span class="px-2.5 py-1 rounded-full text-xs font-semibold border \${st.cls}">\${st.label}</span>
                      \${periodo ? \`<span class="px-2.5 py-1 rounded-full text-xs font-semibold bg-stone-100 text-stone-600 border border-stone-200">\${periodo}</span>\` : ''}
                    </div>
                    <p class="text-xs mt-0.5 truncate"><span class="text-stone-500">\${c.nome}</span> · <span id="codigo-prazo-\${c.colaboradorId}" class="text-stone-500">\${prazo}</span></p>
                  </div>
                  <div class="flex items-center gap-2 shrink-0">
                    \${c.estado === 'AGUARDANDO_SAIDA' ? \`
                      <button onclick="cancelarCodigo('\${c.colaboradorId}')"
                        class="px-3 py-2 rounded-xl border border-stone-300 text-stone-600 text-xs font-semibold hover:bg-stone-50">
                        Cancelar
                      </button>\` : ''}
                  </div>
                </div>
              \`;
            }).join('');

        renderPessoasSemCodigo();
        lucide.createIcons();
      } catch (err) {
        if (err.message !== 'unauthenticated') {
          lista.innerHTML = '<p class="px-6 py-8 text-xs text-red-600 text-center">Não foi possível carregar: ' + err.message + '</p>';
        }
      }
    }

    // Os códigos do dia inteiro, de uma vez.
    //
    // É a ação da manhã: emite, para cada pessoa, o código da MANHÃ e o da
    // TARDE. Cada um serve para sair e para voltar da sua pausa e vale até ao
    // fim da janela do seu período -- é o que permite entregar o QR de manhã.
    async function gerarCodigosDoDia() {
      if (!confirm('Gerar os códigos de hoje para toda a equipe?\\n\\nQuem já tem código vivo de um período mantém o dele. Quem está em pausa não é tocado.')) return;
      const res = await fetch(API_BASE + '/supervisor/codigos/dia', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + getToken() },
        body: JSON.stringify({})
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok) {
        showToast('Erro', data.erro || 'Não foi possível gerar os códigos.', 'error');
        return;
      }
      const feitos = (data.emitidos || []).length;
      const fora = (data.ignorados || []).length;
      showToast(
        'Códigos do dia',
        feitos + ' código(s) emitido(s)' + (fora ? ' · ' + fora + ' fora' : '') + '.',
        'success'
      );
      await refreshCodes();
    }

    // ---- QR do código ------------------------------------------------------
    //
    // O QR é o mesmo código de 6 caracteres, em forma legível por câmara. Quem
    // o mostra aqui está a entregá-lo à pessoa -- por captura de ecrã, por
    // mensagem ou impresso. Por isso o aviso vem junto: reencaminhá-lo é
    // reencaminhar o código.
    const nomePeriodo = (p) => p === 'MANHA' ? 'manhã' : (p === 'TARDE' ? 'tarde' : 'avulso');

    /** Desenha um payload qualquer. Serve o código de outra pessoa e o próprio. */
    function mostrarQrPayload(nome, detalhe, payload, codigoFormatado) {
      if (!payload) {
        showToast('Sem QR', 'Este código não tem QR. Atualize a lista.', 'error');
        return;
      }
      document.getElementById('qr-nome').textContent = nome;
      document.getElementById('qr-detalhe').textContent = detalhe;
      const noCodigo = document.getElementById('qr-codigo');
      if (noCodigo) noCodigo.textContent = codigoFormatado || '';

      // 720 px de lado: o canvas e desenhado grande e depois encolhido por CSS,
      // por isso mantem-se nitido em ecras densos em vez de ficar serrilhado.
      new QRious({
        element: document.getElementById('qr-canvas'),
        value: payload,
        size: 720,
        level: 'M',
        background: '#ffffff',
        foreground: '#1c1917'
      });

      const modal = document.getElementById('qr-modal');
      modal.classList.remove('hidden');
      modal.classList.add('flex');
      lucide.createIcons();
    }


    function fecharQr() {
      const modal = document.getElementById('qr-modal');
      modal.classList.add('hidden');
      modal.classList.remove('flex');
    }

    // Baixar em vez de imprimir: o telemóvel de quem vai usar o QR é o destino,
    // e uma imagem viaja por qualquer mensageiro.
    function baixarQr() {
      const canvas = document.getElementById('qr-canvas');
      const nome = (document.getElementById('qr-nome').textContent || 'codigo')
        .normalize('NFD').replace(/[^a-zA-Z0-9]+/g, '-').toLowerCase();
      const link = document.createElement('a');
      link.download = 'qr-cafe-' + nome + '.png';
      link.href = canvas.toDataURL('image/png');
      link.click();
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
        // O dialogo de vinculo precisa da lista para saber que colaboradores ja
        // estao ocupados por outra conta.
        state.usuarios = users;
        if (!users.length) {
          container.innerHTML = '<p class="text-xs text-stone-400 py-3">Nenhuma conta de acesso cadastrada.</p>';
          return;
        }
        container.innerHTML = users.map(u => {
          const admin = u.perfil === 'ADMIN';
          const bloqueada = u.ativo === false;
          const senhaProvisoria = !!u.trocaSenhaPendente;
          const perfil = admin ? 'Administrador' : 'Supervisor' + (u.turno ? ' · Turno ' + u.turno : '');
          const nomeEsc = (u.nome || '').replace(/'/g, "\\\\'");
          return \`
            <div class="py-3">
              <div class="flex items-center justify-between gap-3 flex-wrap">
                <div class="min-w-0">
                  <h4 class="text-sm font-bold text-coffee-950 truncate">\${u.nome}</h4>
                  <p class="text-xs text-stone-500 truncate">\${u.email}</p>
                  <p class="text-[11px] mt-0.5 truncate \${u.colaboradorNome ? 'text-emerald-700' : 'text-stone-400'}">\${u.colaboradorNome ? 'Colaborador: ' + u.colaboradorNome : 'Sem colaborador vinculado'}</p>
                </div>
                <div class="flex items-center space-x-2 shrink-0">
                  \${bloqueada ? '<span class="text-xs px-2 py-1 rounded-full border bg-red-50 text-red-700 border-red-200 font-semibold">Bloqueada</span>' : ''}
                  \${senhaProvisoria ? '<span class="text-xs px-2 py-1 rounded-full border bg-amber-50 text-amber-700 border-amber-200 font-semibold">Senha provisória</span>' : ''}
                  <span class="text-xs px-2 py-1 rounded-full border font-semibold \${admin ? 'bg-coffee-100 text-coffee-800 border-coffee-200' : 'bg-stone-100 text-stone-600 border-stone-200'}">\${perfil}</span>
                </div>
              </div>
              <div class="flex flex-wrap gap-2 mt-2">
                <button onclick="mudarPerfil('\${u.id}', \${admin}, '\${u.turno || ''}')"
                  class="px-2.5 py-1.5 rounded-lg border border-stone-300 text-stone-600 text-xs font-semibold hover:bg-stone-50">\${admin ? 'Tornar Supervisor' : 'Tornar Admin'}</button>
                <button type="button" data-conta-id="\${u.id}" onclick="abrirVinculo(this.dataset.contaId)"
                  class="px-2.5 py-1.5 rounded-lg border border-stone-300 text-stone-600 text-xs font-semibold hover:bg-stone-50">\${u.colaboradorId ? 'Trocar colaborador' : 'Vincular colaborador'}</button>
                <button onclick="redefinirSenha('\${u.id}')"
                  class="px-2.5 py-1.5 rounded-lg border border-stone-300 text-stone-600 text-xs font-semibold hover:bg-stone-50">Redefinir senha</button>
                <button onclick="bloquearUsuario('\${u.id}', \${bloqueada})"
                  class="px-2.5 py-1.5 rounded-lg border border-amber-300 text-amber-700 text-xs font-semibold hover:bg-amber-50">\${bloqueada ? 'Reativar' : 'Bloquear'}</button>
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
      const form = document.getElementById('new-col-form');
      form.reset();
      // Sem isto, abrir "Novo" depois de editar alguem gravaria por cima dessa
      // pessoa em vez de criar.
      delete form.dataset.colaboradorId;
      document.getElementById('col-modal-titulo').textContent = 'Cadastrar Colaborador';
      document.getElementById('col-modal-texto').textContent =
        'O colaborador não tem senha nem PIN: quem libera a pausa é o código de café de 6 caracteres que o Supervisor emite na hora.';
      document.getElementById('col-modal-salvar').textContent = 'Salvar Colaborador';
      const modal = document.getElementById('new-collaborator-modal');
      modal.classList.remove('hidden');
      modal.classList.add('flex');
    }


    function closeNewCollaboratorModal() {
      document.getElementById('new-collaborator-modal').classList.add('hidden');
      document.getElementById('new-collaborator-modal').classList.remove('flex');
    }

    /**
     * Cria e edita pelo mesmo formulario. Os campos sao os mesmos -- nome,
     * setor e turno sao tudo o que a tabela colaboradores guarda de negocio --
     * e duplicar o modal so daria duas validacoes para manter em dia.
     *
     * O PIN nao existe neste modelo: quem libera a pausa e o codigo de cafe
     * emitido pelo Supervisor, com prazo e uso unico.
     */
    async function handleSalvarColaborador(e) {
      e.preventDefault();
      const botao = document.getElementById('col-modal-salvar');
      const id = document.getElementById('new-col-form').dataset.colaboradorId || '';
      const nome = document.getElementById('col-name').value;
      const turno = document.getElementById('col-role').value;
      const setor = document.getElementById('col-setor').value.trim();
      // Vai sempre, mesmo vazia: o PUT grava a matrícula, e omiti-la apagaria o
      // número de quem for editado.
      const matricula = document.getElementById('col-matricula').value.trim();

      const editando = !!id;
      botao.disabled = true;
      botao.textContent = 'Salvando...';
      try {
        const res = await fetch(API_BASE + '/gestao/colaboradores' + (editando ? '/' + id : ''), {
          method: editando ? 'PUT' : 'POST',
          headers: {
            'Content-Type': 'application/json',
            'Authorization': 'Bearer ' + getToken()
          },
          body: JSON.stringify({ nome: nome, turno: turno || null, setor: setor || null, matricula: matricula || null })
        });

        const data = await res.json().catch(() => ({}));
        if (res.ok) {
          showToast('Sucesso', editando ? 'Dados atualizados.' : 'Colaborador adicionado!', 'success');
          closeNewCollaboratorModal();
          await refreshData();
        } else {
          showToast('Erro', data.erro || 'Falha ao salvar.', 'error');
        }
      } finally {
        botao.disabled = false;
        botao.textContent = editando ? 'Salvar Alterações' : 'Salvar Colaborador';
      }
    }

    function abrirEdicaoColaborador(id) {
      const c = state.collaborators.find(x => x.id === id);
      if (!c) return;
      const form = document.getElementById('new-col-form');
      form.reset();
      form.dataset.colaboradorId = id;
      document.getElementById('col-modal-titulo').textContent = 'Editar Colaborador';
      document.getElementById('col-modal-texto').textContent =
        'Alterar o nome nao apaga o historico: as pausas continuam ligadas a mesma pessoa.';
      document.getElementById('col-modal-salvar').textContent = 'Salvar Alterações';
      document.getElementById('col-name').value = c.name || '';
      // setor e turno chegam ja com o texto de apresentacao; o formulario quer
      // o valor cru, e "Sem setor" nao e um setor.
      document.getElementById('col-matricula').value = c.matricula || '';
      document.getElementById('col-setor').value = c.setorCru || '';
      document.getElementById('col-role').value = c.turnoCru || '';
      const modal = document.getElementById('new-collaborator-modal');
      modal.classList.remove('hidden');
      modal.classList.add('flex');
      document.getElementById('col-name').focus();
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

    /**
     * O olho da senha. Mexe só no atributo type e na visibilidade dos dois
     * ícones: o input é sempre o mesmo elemento, por isso nada do que já foi
     * digitado se perde. A posição do cursor é guardada e reposta porque trocá-lo
     * manda o caret para o fim em alguns navegadores, e quem está a meio de uma
     * palavra perderia o sítio.
     */
    function alternarSenhaLogin() {
      const campo = document.getElementById('login-password');
      const botao = document.getElementById('login-password-toggle');
      const mostrando = campo.type === 'text';
      const inicio = campo.selectionStart;
      const fim = campo.selectionEnd;

      campo.type = mostrando ? 'password' : 'text';
      document.getElementById('login-eye').classList.toggle('hidden', !mostrando);
      document.getElementById('login-eye-off').classList.toggle('hidden', mostrando);

      const rotulo = mostrando ? 'Mostrar senha' : 'Ocultar senha';
      botao.setAttribute('aria-label', rotulo);
      botao.setAttribute('title', rotulo);
      botao.setAttribute('aria-pressed', String(!mostrando));

      campo.focus();
      if (inicio !== null) {
        try { campo.setSelectionRange(inicio, fim); } catch (_) { /* nem todo o navegador deixa */ }
      }
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
        state.usuario = user;
        pintarPerfil();
        hideLogin();
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

    // ---- Perfil -------------------------------------------------------------

    function fecharMenuPerfil() {
      const menu = document.getElementById('perfil-menu');
      const botao = document.getElementById('perfil-botao');
      if (menu) menu.classList.add('hidden');
      if (botao) botao.setAttribute('aria-expanded', 'false');
    }

    function alternarMenuPerfil(e) {
      if (e) e.stopPropagation();
      const menu = document.getElementById('perfil-menu');
      const botao = document.getElementById('perfil-botao');
      if (!menu) return;
      const abrir = menu.classList.contains('hidden');
      menu.classList.toggle('hidden', !abrir);
      // O prazo do código corre: ao abrir, relê em vez de mostrar o de antes.
      if (abrir) carregarMeuCodigo();
      if (botao) botao.setAttribute('aria-expanded', String(abrir));
    }

    // Clicar fora fecha, e Escape tambem: um menu que so fecha no mesmo botao
    // que o abriu fica preso no ecra de quem clicou ao lado.
    document.addEventListener('click', (e) => {
      const wrap = document.getElementById('perfil-wrap');
      if (wrap && !wrap.contains(e.target)) fecharMenuPerfil();
    });
    document.addEventListener('keydown', (e) => {
      if (e.key === 'Escape') fecharMenuPerfil();
    });

    /** Iniciais do primeiro nome e do ultimo apelido: "Ana Manuela Santos" da AS. */
    function iniciaisDe(nome) {
      const partes = (nome || '').trim().split(/\s+/).filter(Boolean);
      if (partes.length === 0) return '--';
      if (partes.length === 1) return partes[0].slice(0, 2).toUpperCase();
      return (partes[0][0] + partes[partes.length - 1][0]).toUpperCase();
    }

    function pintarPerfil() {
      const u = state.usuario;
      const papel = ((u && u.role) || '').toLowerCase() === 'admin' ? 'Administrador' : 'Supervisor';
      const nome = (u && (u.name || u.email)) || '—';
      const def = (id, valor) => { const n = document.getElementById(id); if (n) n.textContent = valor; };
      def('session-user', nome);
      def('perfil-papel', u ? papel : '—');
      def('perfil-iniciais', u ? iniciaisDe(u.name || u.email) : '--');
      def('perfil-nome', nome);
      def('perfil-email', (u && u.email) || '—');
      const wrap = document.getElementById('perfil-wrap');
      if (wrap) wrap.classList.toggle('hidden', !u);
    }

    // ---- Vincular conta a colaborador ---------------------------------------

    let contaAVincular = null;

    function abrirVinculo(contaId) {
      const conta = (state.usuarios || []).find(u => u.id === contaId);
      contaAVincular = contaId;
      document.getElementById('vinculo-conta').textContent = conta ? conta.nome + ' · ' + conta.email : '';
      document.getElementById('vinculo-busca').value = '';
      renderOpcoesVinculo();
      const modal = document.getElementById('vinculo-modal');
      modal.classList.remove('hidden');
      modal.classList.add('flex');
      document.getElementById('vinculo-busca').focus();
    }

    function fecharVinculo() {
      const modal = document.getElementById('vinculo-modal');
      modal.classList.add('hidden');
      modal.classList.remove('flex');
      contaAVincular = null;
    }

    function renderOpcoesVinculo() {
      const lista = document.getElementById('vinculo-lista');
      if (!lista) return;
      const busca = semAcento((document.getElementById('vinculo-busca') || {}).value || '');
      // Um colaborador ja ligado a outra conta nao entra: o indice unico
      // recusaria, e oferece-lo seria prometer o que vai falhar.
      const ocupados = {};
      (state.usuarios || []).forEach(u => { if (u.colaboradorId && u.id !== contaAVincular) ocupados[u.colaboradorId] = true; });
      const opcoes = (state.collaborators || [])
        .filter(c => !ocupados[c.id])
        .filter(c => !busca || semAcento(c.name).includes(busca))
        .slice(0, 60);

      lista.innerHTML = opcoes.length === 0
        ? '<p class="px-4 py-6 text-xs text-stone-400 text-center">Ninguém disponível com esse nome.</p>'
        : opcoes.map(c =>
            '<button type="button" data-col-id="' + c.id + '" onclick="salvarVinculo(this.dataset.colId)" ' +
              'class="w-full text-left px-4 py-2.5 hover:bg-stone-50 transition-colors">' +
              '<span class="block text-sm font-semibold text-coffee-950 truncate">' + c.name + '</span>' +
            '</button>').join('');
    }

    async function salvarVinculo(colaboradorId) {
      if (!contaAVincular) return;
      const res = await fetch(API_BASE + '/admin/usuarios/' + contaAVincular + '/colaborador', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + getToken() },
        body: JSON.stringify({ colaboradorId: colaboradorId })
      });
      const data = await res.json().catch(() => ({}));
      if (res.ok) {
        showToast(colaboradorId ? 'Vinculado' : 'Desvinculado', colaboradorId ? 'A conta passa a ter código próprio.' : 'A conta deixa de bater ponto.', 'success');
        fecharVinculo();
        await refreshUsers();
        await carregarMeuCodigo();
      } else {
        showToast('Erro', data.erro || 'Não foi possível vincular.', 'error');
      }
    }

    // ---- Código próprio -----------------------------------------------------

    async function carregarMeuCodigo() {
      const alvo = document.getElementById('perfil-codigo');
      if (!alvo || !getToken()) return;
      try {
        const d = await apiFetch('/supervisor/meu-codigo');
        state.meuCodigo = d;
        pintarMeuCodigo();
      } catch (err) {
        if (err.message !== 'unauthenticated') alvo.innerHTML = '';
      }
    }

    function pintarMeuCodigo() {
      const alvo = document.getElementById('perfil-codigo');
      const d = state.meuCodigo;
      if (!alvo || !d) return;

      // Conta sem vínculo administra e não bate ponto. Não é um erro, e por
      // isso explica-se em vez de se oferecer um botão que ia falhar.
      if (!d.vinculado) {
        alvo.innerHTML = '<p class="text-[11px] text-stone-400 leading-snug">' +
          'Esta conta não está vinculada a um colaborador, por isso não tem código de café próprio. ' +
          'Um Administrador pode vinculá-la em Gestão &rsaquo; Contas de Acesso.</p>';
        return;
      }

      // O crachá é a cadeia fixa. Enquanto não existir, ainda não foi gerado --
      // e gerá-lo é o mesmo gesto de pedir o código do período.
      if (!d.cracha) {
        alvo.innerHTML = '<p class="text-[11px] font-semibold text-stone-500 mb-2">Meu QR de café</p>' +
          '<button type="button" onclick="emitirMeuCodigo()" class="w-full flex items-center justify-center gap-2 px-3 py-2 rounded-xl bg-amberAccent text-white text-xs font-semibold shadow hover:opacity-95">' +
            '<i data-lucide="qr-code" class="w-4 h-4"></i><span>Criar meu QR</span>' +
          '</button>';
        lucide.createIcons();
        return;
      }

      // O QR mostra-se sempre, porque é sempre o mesmo. O que muda por baixo é
      // se ele vale agora: sem código vivo do período, ainda não regista nada.
      const estado = !d.codigo
        ? { texto: 'sem código deste período', cls: 'text-stone-400' }
        : (d.codigo.emPausa
            ? { texto: 'em pausa · válido para o retorno', cls: 'text-amber-700' }
            : { texto: 'activo · expira em ' + segundosParaRelogio(d.codigo.expiraEmSegundos), cls: 'text-emerald-700' });

      alvo.innerHTML = '<p class="text-[11px] font-semibold text-stone-500 mb-1">Meu QR de café</p>' +
        '<button type="button" onclick="mostrarMeuQr()" class="w-full text-left group">' +
          '<span class="block font-mono text-lg font-extrabold text-coffee-900 tracking-widest leading-none group-hover:opacity-70 transition-opacity">' + d.cracha.codigoFormatado + '</span>' +
          '<span class="block text-[11px] mt-0.5 ' + estado.cls + '">' + estado.texto + '</span>' +
        '</button>' +
        '<button type="button" onclick="mostrarMeuQr()" class="mt-2 w-full flex items-center justify-center gap-2 px-3 py-2 rounded-xl border border-stone-200 text-stone-700 text-xs font-semibold hover:bg-stone-50">' +
          '<i data-lucide="qr-code" class="w-4 h-4"></i><span>Mostrar em ecrã cheio</span>' +
        '</button>' +
        (!d.codigo
          ? '<button type="button" onclick="emitirMeuCodigo()" class="mt-1.5 w-full px-3 py-2 rounded-xl bg-amberAccent text-white text-xs font-semibold shadow hover:opacity-95">Activar para este período</button>'
          : '');
      lucide.createIcons();
    }

    function mostrarMeuQr() {
      const d = state.meuCodigo;
      if (!d || !d.cracha) return;
      fecharMenuPerfil();
      const detalhe = !d.codigo
        ? 'Sem código deste período — active antes de mostrar ao totem'
        : (d.codigo.emPausa ? 'Em pausa · válido para o retorno' : 'Pausa da ' + nomePeriodo(d.codigo.periodo));
      mostrarQrPayload(d.colaborador.nome, detalhe, d.cracha.qrPayload, d.cracha.codigoFormatado);
    }

    async function emitirMeuCodigo() {
      const res = await fetch(API_BASE + '/supervisor/meu-codigo', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + getToken() }
      });
      const data = await res.json().catch(() => ({}));
      if (res.ok) {
        showToast('Código gerado', 'Código ' + (data.codigoFormatado || '') + ' — mostre o QR ao totem.', 'success');
        await carregarMeuCodigo();
      } else {
        showToast('Erro', data.erro || 'Não foi possível gerar o código.', 'error');
      }
    }

    function handleLogout() {
      fecharMenuPerfil();
      clearToken();
      state.usuario = null;
      state.meuCodigo = null;
      pintarPerfil();
      state.collaborators = [];
      state.history = [];

      renderTeamList(); renderHistoryTable(); renderAdminList();
      showLogin();
    }

    // Initialize on load
    window.addEventListener('DOMContentLoaded', () => {
      // O totem tem credencial propria. Um aparelho ja vinculado bate ponto
      // sem ninguem da gestao ter sessao aberta neste navegador -- e isso que
      // faz dele um totem, e nao mais um ecra de administrador. As outras abas
      // continuam a pedir login: quem toca nelas cai no overlay.
      totemBoot();
      pintarPerfil();
      pintarBotaoAvisos();
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
