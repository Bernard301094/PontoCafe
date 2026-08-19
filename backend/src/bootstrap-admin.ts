import { auth } from './auth-runtime.js'
import { query } from './db.js'

const nome = process.env.BOOTSTRAP_ADMIN_NAME?.trim()
const email = process.env.BOOTSTRAP_ADMIN_EMAIL?.trim().toLowerCase()
const password = process.env.BOOTSTRAP_ADMIN_PASSWORD

if (!nome || !email || !password) throw new Error('Defina BOOTSTRAP_ADMIN_NAME, BOOTSTRAP_ADMIN_EMAIL e BOOTSTRAP_ADMIN_PASSWORD.')
if (password.length < 10) throw new Error('A senha inicial deve ter pelo menos 10 caracteres.')

const count = await query<{ total: string }>('select count(*)::text as total from "user"')
if (Number(count.rows[0]?.total ?? 0) > 0) throw new Error('Já existe usuário cadastrado. Bootstrap cancelado.')

const created = await auth.api.createUser({ body: { name: nome, email, password, role: 'admin' } })
console.log(`Administrador criado: ${created.user.email}`)
