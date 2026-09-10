import { getMigrations } from 'better-auth/db/migration'
import { auth } from './auth-runtime.js'

const migrations = await getMigrations(auth.options)
console.log(`Tabelas a criar: ${migrations.toBeCreated.length}`)
console.log(`Campos a adicionar: ${migrations.toBeAdded.length}`)
await migrations.runMigrations()
console.log('Schema de autenticação atualizado com sucesso.')
