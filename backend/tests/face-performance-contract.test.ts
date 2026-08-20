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

test('runtime facial usa LiteRT CPU compatível com templates existentes', () => {
  assert.match(gradle, /play-services-tflite-java:16\.5\.0/)
  assert.doesNotMatch(gradle, /play-services-tflite-gpu/)
  assert.match(engine, /TfLite\.initialize\(context\.applicationContext\)/)
  assert.match(engine, /TfLiteRuntime\.FROM_SYSTEM_ONLY/)
  assert.match(engine, /setNumThreads\(CPU_THREADS\)/)
  assert.doesNotMatch(engine, /GpuDelegateFactory/)
})

test('identificação adaptativa continua progressiva', () => {
  assert.match(contract, /shouldContinue: \(embedding: FloatArray, candidateIndex: Int\) -> Boolean/)
  assert.match(engine, /if \(!shouldContinue\(primary, 0\)\) return@withContext candidates/)
  assert.match(engine, /if \(!shouldContinue\(tight, candidates\.lastIndex\)\) return@withContext candidates/)
  assert.match(viewModel, /embedForIdentification\(frame\) \{ candidate, candidateIndex ->/)
  assert.match(viewModel, /LocalFaceMatcher\.match\(candidate, currentCatalog\)/)
  assert.match(viewModel, /resolvedDuringInference = LocalFaceResolvedMatch/)
})

test('cadastro e primeiro match mantêm embedding canônico histórico', () => {
  assert.match(engine, /override suspend fun embed\(frame: FaceFrame\): FloatArray/)
  assert.match(engine, /canonicalRect\(source, frame\.faceBounds\)/)
  assert.match(engine, /const val FACE_MARGIN = 0\.18f/)
  assert.match(engine, /const val MODEL_VERSION = "facenet-128d-160-v1"/)
  assert.match(engine, /val raw = FloatArray\(pixels\.size \* 3\)/)
  assert.match(engine, /raw\.forEach \{ putFloat\(\(it - mean\) \/ std\) \}/)
})

test('fallbacks só usam o mesmo frame após miss canônico', () => {
  assert.match(engine, /horizontalMargin = 0\.10f/)
  assert.match(engine, /landmarkAnchoredRect\(source, frame\)/)
  assert.match(engine, /MAX_IDENTIFICATION_CANDIDATES = 3/)
})
