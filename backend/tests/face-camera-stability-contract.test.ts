import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const camera = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/camera/FaceCamera.kt', import.meta.url),
  'utf8',
)
const engine = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/camera/LiteRtFaceEmbeddingEngine.kt', import.meta.url),
  'utf8',
)

test('analisador facial serializa ML Kit e sempre libera o ImageProxy', () => {
  assert.match(camera, /Tasks\.await\(detector\.process\(image\)\)/)
  assert.match(camera, /finally\s*\{[\s\S]*imageProxy\.close\(\)/)
  assert.match(camera, /captureController\.retry\(\)/)
  assert.match(camera, /executor\.shutdownNow\(\)/)
  assert.match(camera, /cameraProvider\?\.unbindAll\(\)/)
})

test('camera usa detecção barata antes de landmarks e liveness', () => {
  assert.match(camera, /val presenceDetector = remember/)
  assert.match(camera, /LANDMARK_MODE_NONE/)
  assert.match(camera, /CLASSIFICATION_MODE_NONE/)
  assert.match(camera, /val detailedDetector = remember/)
  assert.match(camera, /LANDMARK_MODE_ALL/)
  assert.match(camera, /CLASSIFICATION_MODE_ALL/)
  assert.match(camera, /PRESENCE_STABLE_FRAMES = 2/)
  assert.match(camera, /detailedFeatures && isGeometryReady/)
  assert.match(camera, /if \(!detailedThisFrame\)[\s\S]*captureController\.retry\(\)/)
  assert.match(camera, /STRATEGY_KEEP_ONLY_LATEST/)
})

test('embedding facial serializa inferência, mantém CPU seguro e libera bitmaps', () => {
  assert.match(engine, /private val inferenceMutex = Mutex\(\)/)
  assert.match(engine, /inferenceMutex\.withLock/)
  assert.match(engine, /PontoCafe-FaceNet/)
  assert.match(engine, /\.setNumThreads\(CPU_THREADS\)/)
  assert.match(engine, /if \(!source\.isRecycled\)/)
  assert.match(engine, /source\.recycle\(\)/)
  assert.match(engine, /switchToCpu/)
})
