import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const viewModel = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/PontoCafeViewModel.kt', import.meta.url),
  'utf8',
)
const catalogStore = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/data/SecureFaceCatalogStore.kt', import.meta.url),
  'utf8',
)
const kiosk = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/FaceKioskScreen.kt', import.meta.url),
  'utf8',
)
const enrollmentRoute = readFileSync(
  new URL('../src/routes/collaborator-management-routes.ts', import.meta.url),
  'utf8',
)
const catalogRoute = readFileSync(
  new URL('../src/routes/local-biometric-routes.ts', import.meta.url),
  'utf8',
)

test('cadastro persiste templates com os mesmos identificadores usados pelo catálogo do Ponto', () => {
  assert.match(enrollmentRoute, /insert into templates_faciais/)
  assert.match(enrollmentRoute, /colaboradorId/)
  assert.match(enrollmentRoute, /body\.data\.modelo/)
  assert.match(enrollmentRoute, /body\.data\.versaoModelo/)
  assert.match(catalogRoute, /join colaboradores col on col\.id=t\.colaborador_id/)
  assert.match(catalogRoute, /where col\.ativo=true and t\.modelo=\$1 and t\.versao_modelo=\$2/)
  assert.match(catalogRoute, /colaboradorId: row\.colaborador_id/)
})

test('versão do catálogo muda quando um rosto ativo é incluído ou atualizado', () => {
  assert.match(catalogRoute, /md5\(coalesce\(string_agg/)
  assert.match(catalogRoute, /t\.id::text/)
  assert.match(catalogRoute, /t\.atualizado_em::text/)
  assert.match(catalogRoute, /if \(versaoAtual && versaoAtual === versao\)/)
  assert.match(catalogRoute, /atualizado: false/)
})

test('leitura do catálogo aceita bancos anteriores à coluna opcional tipo', () => {
  assert.match(catalogRoute, /coalesce\(to_jsonb\(t\)->>'tipo','LEGADO'\)/)
  assert.doesNotMatch(catalogRoute, /\bt\.tipo\b/)
})

test('refresh forçado nunca é descartado por uma sincronização que já está ativa', () => {
  assert.match(viewModel, /private val catalogSyncMutex = Mutex\(\)/)
  assert.match(viewModel, /catalogSyncMutex\.withLock/)
  assert.match(viewModel, /sincronizarBiometriasInterno\(force\)/)
  assert.doesNotMatch(
    viewModel,
    /if \(!state\.deviceConfigured \|\| !embeddingEngine\.isReady \|\| state\.sincronizandoBiometrias\) return/,
  )
})

test('catálogo local compatível fica utilizável enquanto a rede atualiza em segundo plano', () => {
  assert.match(
    viewModel,
    /val cached = withContext\(Dispatchers\.IO\) \{ faceCatalogStore\.read\(\) \}[\s\S]*publishCatalogState\([\s\S]*obterCatalogoAtual/,
  )
  assert.match(viewModel, /catalogoBiometricoPronto = compatible\?\.templates\?\.isNotEmpty\(\) == true/)
  assert.match(kiosk, /analysisEnabled = viewModel\.faceModelReady && state\.scanning/)
  assert.match(kiosk, /state\.catalogoBiometricoPronto && !state\.carregando/)
  assert.match(kiosk, /Catálogo local ativo · atualização pendente/)
})

test('UI distingue catálogo sincronizado vazio de catálogo ainda não carregado', () => {
  assert.match(viewModel, /catalogoBiometricoCarregado = compatible != null/)
  assert.match(kiosk, /state\.catalogoBiometricoCarregado && !state\.catalogoBiometricoPronto/)
  assert.match(kiosk, /Nenhum rosto disponível/)
  assert.match(kiosk, /Rostos ainda não sincronizados/)
})

test('cache de outro modelo não é aceito nem usado como versão condicional', () => {
  assert.match(viewModel, /private fun catalogCompatible/)
  assert.match(viewModel, /catalog\.modelo == embeddingEngine\.modelName/)
  assert.match(viewModel, /catalog\.versaoModelo == embeddingEngine\.modelVersion/)
  assert.match(viewModel, /val compatibleCache = cache\?\.takeIf\(::catalogCompatible\)/)
  assert.match(viewModel, /versaoAtual = if \(fullRefresh\) null else conditionalVersion/)
  assert.match(viewModel, /servidor informou catálogo inalterado sem existir cache local compatível/)
})

test('cache compatível vazio solicita payload completo na próxima atualização', () => {
  assert.match(viewModel, /val conditionalVersion = compatibleCache/)
  assert.match(viewModel, /takeIf \{ it\.templates\.isNotEmpty\(\) \}/)
  assert.match(viewModel, /versaoAtual = if \(fullRefresh\) null else conditionalVersion/)
})

test('catálogo local corrompido é rejeitado e removido com segurança', () => {
  assert.match(catalogStore, /validateCatalog\(gson\.fromJson/)
  assert.match(catalogStore, /require\(embedding\.size in MIN_EMBEDDING_SIZE\.\.MAX_EMBEDDING_SIZE\)/)
  assert.match(catalogStore, /require\(value\.isFinite\(\)\)/)
  assert.match(catalogStore, /if \(catalog == null\) \{[\s\S]*clear\(\)/)
  assert.match(catalogStore, /LocalFaceMatcher\.clearPreparedCatalog\(\)/)
})

test('falha temporária preserva disponibilidade local sem fingir sincronização', () => {
  assert.match(viewModel, /allowStaleOnFailure = false/)
  assert.match(viewModel, /erroSincronizacaoBiometrica = syncError/)
  assert.match(viewModel, /catalog = fallback/)
  assert.match(kiosk, /state\.erroSincronizacaoBiometrica\.takeIf \{ !state\.catalogoBiometricoPronto \}/)
})
