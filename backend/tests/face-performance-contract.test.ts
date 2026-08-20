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

test('LiteRT oferece GPU gratuita mas mantém fallback CPU', () => {
  assert.match(gradle, /play-services-tflite-java:16\.5\.0/)
  assert.match(gradle, /play-services-tflite-gpu:16\.5\.0/)
  assert.match(engine, /TfLiteGpu\.isGpuDelegateAvailable/)
  assert.match(engine, /setEnableGpuDelegateSupport\(true\)/)
  assert.match(engine, /GpuDelegateFactory/)
  assert.match(engine, /createCpuInterpreter\(\)/)
  assert.match(engine, /switchToCpu/)
})

test('GPU preserva precisão e é configurada para uso repetido do Ponto', () => {
  assert.match(engine, /setPrecisionLossAllowed\(false\)/)
  assert.match(engine, /INFERENCE_PREFERENCE_SUSTAINED_SPEED/)
  assert.match(engine, /GpuDelegateFactory\(delegateOptions\)/)
})

test('runtime escolhe CPU ou GPU medindo o FaceNet no próprio dispositivo', () => {
  assert.match(engine, /private fun benchmark\(runtime: InterpreterApi\): Long/)
  assert.match(engine, /BENCHMARK_RUNS = 3/)
  assert.match(engine, /val chooseGpu = gpuNs < cpuNs/)
  assert.match(engine, /rememberBackend\(Backend\.GPU\)/)
  assert.match(engine, /rememberBackend\(Backend\.CPU\)/)
  assert.match(engine, /backend_\$\{MODEL_VERSION\}_v3/)
})

test('identificação adaptativa é progressiva e não calcula fallbacks sem necessidade', () => {
  assert.match(contract, /shouldContinue: \(embedding: FloatArray, candidateIndex: Int\) -> Boolean/)
  assert.match(engine, /if \(!shouldContinue\(primary, 0\)\) return@withContext candidates/)
  assert.match(engine, /if \(!shouldContinue\(tight, candidates\.lastIndex\)\) return@withContext candidates/)
  assert.match(viewModel, /embedForIdentification\(frame\) \{ candidate, candidateIndex ->/)
  assert.match(viewModel, /LocalFaceMatcher\.match\(candidate, currentCatalog\)/)
  assert.match(viewModel, /resolvedDuringInference = LocalFaceResolvedMatch/)
})

test('cadastro mantém embedding canônico compatível', () => {
  assert.match(engine, /override suspend fun embed\(frame: FaceFrame\): FloatArray/)
  assert.match(engine, /canonicalRect\(source, frame\.faceBounds\)/)
  assert.match(engine, /const val FACE_MARGIN = 0\.18f/)
  assert.match(engine, /const val MODEL_VERSION = "facenet-128d-160-v1"/)
})

test('preprocessamento evita FloatArray RGB intermediário', () => {
  assert.doesNotMatch(engine, /val raw = FloatArray/)
  assert.match(engine, /ByteBuffer\.allocateDirect\(INPUT_FLOAT_COUNT \* Float\.SIZE_BYTES\)/)
  assert.match(engine, /putFloat\(/)
})
