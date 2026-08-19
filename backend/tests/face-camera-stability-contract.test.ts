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

test('embedding facial serializa inferência e libera bitmaps temporários', () => {
  assert.match(engine, /private val inferenceMutex = Mutex\(\)/)
  assert.match(engine, /inferenceMutex\.withLock/)
  assert.match(engine, /if \(!source\.isRecycled\)/)
  assert.match(engine, /source\.recycle\(\)/)
  assert.match(engine, /\.setNumThreads\(2\)/)
})
