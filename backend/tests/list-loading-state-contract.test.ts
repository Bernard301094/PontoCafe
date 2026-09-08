import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const read = (p: string) => readFileSync(new URL(`../../${p}`, import.meta.url), 'utf8')

const adminViewModel = read('app/src/main/java/com/pontocafe/app/AdminViewModel.kt')
const supervisorViewModel = read('app/src/main/java/com/pontocafe/app/SupervisorViewModel.kt')
const adminPeople = read('app/src/main/java/com/pontocafe/app/ui/AdminPeopleScreenV4.kt')
const supervisorPeople = read('app/src/main/java/com/pontocafe/app/ui/SupervisorPeopleScreenV3.kt')

/** Cada chamada de [nome] com a janela de texto que cobre os seus argumentos. */
function chamadas(fonte: string, nome: string, janela = 600): string[] {
  const encontradas: string[] = []
  let from = 0
  for (;;) {
    const at = fonte.indexOf(`${nome}(`, from)
    if (at < 0) return encontradas
    encontradas.push(fonte.slice(at, at + janela))
    from = at + nome.length
  }
}

test('emitir um código acende o botão de uma pessoa, não o da lista inteira', () => {
  // `carregando` é um sinalizador de tela inteira. Passá-lo a cada linha fazia
  // os noventa e seis botões "Gerar" acenderem juntos quando se emitia um
  // código para uma pessoa só, e parecia que todos tinham sido premidos.
  for (const [label, vm] of [
    ['admin', adminViewModel],
    ['supervisor', supervisorViewModel],
  ] as const) {
    assert.ok(
      vm.includes('val colaboradorOcupadoId: String? = null'),
      `${label} precisa saber QUEM está ocupado`,
    )
    assert.ok(
      vm.includes('colaboradorOcupadoId = colaborador.id'),
      `${label} precisa marcar a pessoa ao emitir`,
    )
    assert.ok(
      vm.includes('colaboradorOcupadoId = null'),
      `${label} precisa desmarcar quando a ação termina`,
    )
  }

  // Nenhuma linha de lista pode voltar a ler o sinalizador global.
  for (const [label, tela] of [
    ['admin', adminPeople],
    ['supervisor', supervisorPeople],
  ] as const) {
    const cartoes = chamadas(tela, 'PeoplePersonCard')
    assert.ok(cartoes.length > 0, `${label} deveria renderizar PeoplePersonCard`)
    for (const cartao of cartoes) {
      assert.ok(
        cartao.includes('loading = state.colaboradorOcupadoId == '),
        `${label}: o cartão precisa comparar o id em vez de ler state.carregando`,
      )
    }
  }
})

test('trocar de aba não recarrega do zero o que já está em memória', () => {
  // As abas trocavam de destination dentro do onSuccess: cada toque era uma ida
  // à rede inteira olhando para a tela anterior, e a lista reaparecia "do zero"
  // a cada visita mesmo já estando carregada.
  assert.ok(adminViewModel.includes('private fun <T> navegarEAtualizar('))
  assert.ok(adminViewModel.includes('carregando = !temDados'))
  for (const aba of ['abrirColaboradores', 'abrirCodigos', 'abrirConfiguracoes', 'abrirAuditoria']) {
    assert.ok(
      adminViewModel.includes(`fun ${aba}() = navegarEAtualizar(`),
      `${aba} precisa navegar antes de buscar`,
    )
  }

  // O Supervisor tem as mesmas abas e o mesmo padrão, sem o helper.
  for (const aba of ['abrirCodigos', 'abrirColaboradores']) {
    const corpo = supervisorViewModel.slice(supervisorViewModel.indexOf(`fun ${aba}(`))
    const navega = corpo.indexOf('destination = SupervisorDestination.')
    const busca = corpo.indexOf('viewModelScope.launch')
    assert.ok(navega >= 0 && busca >= 0, `${aba} precisa navegar e buscar`)
    assert.ok(navega < busca, `${aba} precisa trocar de tela antes de ir à rede`)
    assert.ok(
      corpo.slice(0, busca).includes('carregando = !temDados'),
      `${aba} não pode esvaziar uma tela que já tem dados`,
    )
  }
})
