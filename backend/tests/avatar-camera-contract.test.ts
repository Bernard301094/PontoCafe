import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const dialog = readFileSync(new URL('../../app/src/main/java/com/pontocafe/app/ui/CollaboratorAvatarSourceDialog.kt', import.meta.url), 'utf8')
const optimizer = readFileSync(new URL('../../app/src/main/java/com/pontocafe/app/avatar/AvatarImageOptimizer.kt', import.meta.url), 'utf8')
const adminPeople = readFileSync(new URL('../../app/src/main/java/com/pontocafe/app/ui/AdminPeopleScreenV4.kt', import.meta.url), 'utf8')
const supervisorPeople = readFileSync(new URL('../../app/src/main/java/com/pontocafe/app/ui/SupervisorPeopleScreenV3.kt', import.meta.url), 'utf8')

test('seletor de avatar oferece câmera e galeria', () => {
  assert.match(dialog, /ActivityResultContracts\.TakePicturePreview\(\)/)
  assert.match(dialog, /ActivityResultContracts\.GetContent\(\)/)
  assert.match(dialog, /RequestPermission\(\)/)
  assert.match(dialog, /Manifest\.permission\.CAMERA/)
  assert.match(dialog, /Tirar foto/)
  assert.match(dialog, /Galeria/)
})

test('foto da câmera usa o mesmo otimizador WebP do avatar', () => {
  assert.match(optimizer, /fun optimize\(bitmap: Bitmap\): ByteArray/)
  assert.match(optimizer, /optimizeDecoded/)
  assert.match(optimizer, /WEBP_LOSSY/)
  assert.match(dialog, /AvatarImageOptimizer\.optimize\(bitmap\)/)
})

test('admin e supervisor usam o seletor único de fonte do avatar', () => {
  assert.match(adminPeople, /CollaboratorAvatarSourceDialog/)
  assert.match(supervisorPeople, /CollaboratorAvatarSourceDialog/)
  assert.doesNotMatch(adminPeople, /avatarLauncher\.launch\("image\/\*"\)/)
  assert.doesNotMatch(supervisorPeople, /avatarLauncher\.launch\("image\/\*"\)/)
})

test('avatar continua explicitamente separado da biometria facial sem repetir aviso em cada card', () => {
  assert.match(dialog, /somente como avatar/)
  assert.match(dialog, /separada da biometria facial/)
  assert.doesNotMatch(adminPeople, /A imagem é recortada, reduzida e convertida para WebP/)
  assert.doesNotMatch(supervisorPeople, /O avatar permanece separado da biometria facial/)
})
