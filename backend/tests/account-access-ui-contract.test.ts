import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const screen = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/AdminNewAccountScreen.kt', import.meta.url),
  'utf8',
)
const form = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/AdminAccountForm.kt', import.meta.url),
  'utf8',
)
const materialDesignSystem = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/MaterialDesignSystem.kt', import.meta.url),
  'utf8',
)

test('nova conta mantém o fluxo real do AdminViewModel', () => {
  assert.match(screen, /viewModel\.criarConta\(input\)/)
  assert.match(screen, /trackAccountDraftSubmission\(draftState\)/)
  assert.match(screen, /draftState\.reset\(\)/)
})

test('criação de acesso usa o design system Material 3 e contexto operacional', () => {
  assert.match(screen, /AccountAccessContextCard/)
  assert.match(screen, /Supervisor recomendado/)
  assert.match(form, /PcSectionSurface/)
  assert.match(materialDesignSystem, /fun PcSectionSurface\(/)
  assert.match(materialDesignSystem, /surfaceContainerLow/)
  assert.match(form, /Perfil de acesso/)
  assert.match(form, /Informações da conta/)
  assert.match(form, /Segurança/)
})

test('formulário guia senha e bloqueia submissão incompleta', () => {
  assert.match(form, /passwordLongEnough/)
  assert.match(form, /passwordHasLetter/)
  assert.match(form, /passwordHasDigit/)
  assert.match(form, /passwordsMatch/)
  assert.match(form, /Conter letras e números/)
  assert.match(form, /enabled = !carregando && validationError == null/)
})
