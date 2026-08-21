import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const gradle = readFileSync(new URL('../../app/build.gradle.kts', import.meta.url), 'utf8')
const contract = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/camera/FaceEmbeddingEngine.kt', import.meta.url),
  'utf8',
)
const engine = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/camera/LiteRtFaceEmbeddingEngine.kt', import.meta.url),
  'utf8',
)
const viewModel = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/PontoCafeViewModel.kt', import.meta.url),
  'utf8',
)
const catalog = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/data/SecureFaceCatalogStore.kt', import.meta.url),
  'utf8',
)

test('runtime facial usa LiteRT CPU compatível com templates existentes', () => {
  assert.match(gradle, /play-services-tflite-java:16\.5\.0/)
  assert.doesNotMatch(gradle, /play-services-tflite-gpu/)
  assert.match(engine, /TfLite\.initialize\(context\.applicationContext\)/)
  assert.match(engine, /TfLiteRuntime\.FROM_SYSTEM_ONLY/)
  assert.match(engine, /setNumThreads\(CPU_THREADS\)/)
  assert.match(engine, /CPU_THREADS = 2/)
  assert.doesNotMatch(engine, /GpuDelegateFactory/)
})

test('identificação adaptativa continua progressiva', () => {
  assert.match(contract, /shouldContinue: \(embedding: FloatArray, candidateIndex: Int\) -> Boolean/)
  assert.match(engine, /if \(!shouldContinue\(primary, 0\)\) return@withContext candidates/)
  assert.match(engine, /if \(!shouldContinue\(tight, candidates\.lastIndex\)\) return@withContext candidates/)
  assert.match(viewModel, /embedForIdentification\(frame\) \{ candidate, candidateIndex ->/)
  assert.match(viewModel, /LocalFaceMatcher\.evaluateDetailed\(candidate, currentCatalog\)/)
  assert.match(viewModel, /resolvedDuringInference = LocalFaceResolvedMatch/)
  assert.match(viewModel, /temporalConsensus\.submit/)
  assert.match(viewModel, /TemporalConsensusDecision\.Confirmed/)
})

test('cadastro e primeiro match mantêm embedding canônico histórico', () => {
  assert.match(engine, /override suspend fun embed\(frame: FaceFrame\): FloatArray/)
  assert.match(engine, /canonicalRect\(source, frame\.faceBounds\)/)
  assert.match(engine, /const val FACE_MARGIN = 0\.18f/)
  assert.match(engine, /const val MODEL_VERSION = "facenet-128d-160-v1"/)
  assert.match(engine, /val raw = workspace\.raw/)
  assert.match(engine, /val raw = FloatArray\(pixels\.size \* 3\)/)
  assert.match(engine, /raw\.forEach \{ putFloat\(\(it - mean\) \/ std\) \}/)
})

test('fallbacks só usam o mesmo frame após miss canônico', () => {
  assert.match(engine, /horizontalMargin = 0\.10f/)
  assert.match(engine, /landmarkAnchoredRect\(source, frame\)/)
  assert.match(engine, /MAX_IDENTIFICATION_CANDIDATES = 3/)
})

test('FaceNet reutiliza buffers e aquece a primeira inferência fora do fluxo interativo', () => {
  assert.match(engine, /modelAssetAvailable by lazy/)
  assert.match(engine, /InferenceWorkspace/)
  assert.match(engine, /inferenceWorkspace by lazy/)
  assert.match(engine, /ByteBuffer\.allocateDirect\(raw\.size \* Float\.SIZE_BYTES\)/)
  assert.match(engine, /workspace\.output\[0\]\.copyOf\(\)/)
  assert.match(engine, /withContext\(Dispatchers\.Default\)/)
  assert.match(engine, /runtime\.run\(workspace\.input, workspace\.output\)/)
  assert.match(engine, /inferencePrimed = true/)
})

test('catálogo prepara o índice vetorial fora do match e invalida dados removidos', () => {
  assert.match(catalog, /data class PreparedCatalog/)
  assert.match(catalog, /sourceTemplates === catalog\.templates/)
  assert.match(catalog, /template\.embedding\.toFloatArray\(\)/)
  assert.match(catalog, /FaceEmbeddingIntegrity\.inspect\(stored, FACE_EMBEDDING_DIMENSION\)/)
  assert.match(catalog, /norm = requireNotNull\(integrity\.norm\)/)
  assert.match(catalog, /return dot \/ \(stored\.norm \* currentNorm\)/)
  assert.match(catalog, /LocalFaceMatcher\.prepareCatalog\(catalog\.templates\)/)
  assert.match(catalog, /cachedCatalog\?\.also \{ LocalFaceMatcher\.prepareCatalog\(it\.templates\) \}/)
  assert.match(catalog, /LocalFaceMatcher\.clearPreparedCatalog\(\)/)
})

test('reconhecimento é exclusivo, libera todo frame e evita refresh duplicado', () => {
  assert.match(viewModel, /recognitionInFlight = AtomicBoolean\(false\)/)
  assert.match(viewModel, /recognitionInFlight\.compareAndSet\(false, true\)/)
  assert.match(viewModel, /invokeOnCompletion/)
  assert.match(viewModel, /if \(!frame\.bitmap\.isRecycled\) frame\.bitmap\.recycle\(\)/)
  assert.match(viewModel, /catch \(error: CancellationException\)/)
  assert.match(viewModel, /var catalogRefreshAttempted = false/)
  assert.match(viewModel, /resolvedMatch == null && !catalogRefreshAttempted/)
  assert.match(viewModel, /obterCatalogoAtual\(force = true, fullRefresh = false\)/)
  assert.match(viewModel, /fullRefresh: Boolean = force/)
  assert.match(viewModel, /versaoAtual = if \(fullRefresh\) null else conditionalVersion/)
  assert.match(viewModel, /limiar = response\.limiar/)
  assert.match(viewModel, /margem = response\.margem/)
  assert.match(viewModel, /matchingCatalogChanged\(previousCatalog, refreshed\)/)
  assert.match(viewModel, /previous\.limiar != current\.limiar/)
  assert.match(viewModel, /previous\.margem != current\.margem/)
  assert.match(viewModel, /previous\.templates !== current\.templates/)
})

test('inicialização não descriptografa catálogos biométricos no thread principal', () => {
  assert.doesNotMatch(viewModel, /catalogoBiometricoPronto = faceCatalogStore\.read/)
  assert.doesNotMatch(viewModel, /totalBiometrias = faceCatalogStore\.read/)
  assert.match(viewModel, /withContext\(Dispatchers\.IO\) \{ faceCatalogStore\.read\(\) \}/)
  assert.match(viewModel, /val offlineSnapshot = withContext\(Dispatchers\.IO\)/)
})

test('rede e persistência cifrada do ponto ficam fora do thread principal', () => {
  assert.match(viewModel, /withContext\(Dispatchers\.IO\) \{[\s\S]*repository\.registrarRapido/)
  assert.match(viewModel, /withContext\(Dispatchers\.IO\) \{[\s\S]*repository\.confirmarIdentidadeLocal/)
  assert.match(viewModel, /private suspend fun aplicarInicioOnline/)
  assert.match(viewModel, /private suspend fun aplicarRetornoOnline/)
  assert.match(viewModel, /withContext\(Dispatchers\.IO\) \{[\s\S]*offlineStore\.recordOnlineStart/)
  assert.match(viewModel, /withContext\(Dispatchers\.IO\) \{[\s\S]*offlineStore\.recordOnlineFinish/)
  assert.doesNotMatch(viewModel, /offlineStore\.markServerOk\(\)\s*offlineStore\.recordOnlineStart/)
  assert.doesNotMatch(viewModel, /offlineStore\.markServerOk\(\)\s*offlineStore\.recordOnlineFinish/)
})
