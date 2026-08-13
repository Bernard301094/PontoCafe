import { Hono } from 'hono'
import { cors } from 'hono/cors'
import { secureHeaders } from 'hono/secure-headers'
import type { AppEnv } from './auth.js'
import { query } from './db.js'
import { adminRoutes } from './routes/admin-routes.js'
import { authRoutes } from './routes/auth-routes.js'
import { pontoRoutes } from './routes/ponto-routes.js'
import { supervisorRoutes } from './routes/supervisor-routes.js'

const app = new Hono<AppEnv>()
app.use('*', secureHeaders())
app.use('*', cors({ origin: '*', allowHeaders: ['Content-Type','Authorization','X-Device-Token','X-Bootstrap-Token'], allowMethods: ['GET','POST','PUT','OPTIONS'] }))
app.get('/', (c) => c.json({ app: 'Ponto Café API', status: 'ok', versao: '0.1.0' }))
app.get('/health', async (c) => {
  const result = await query<{ agora: string }>('select now()::text as agora')
  return c.json({ status: 'ok', banco: 'ok', servidor: result.rows[0]?.agora })
})
app.route('/', authRoutes)
app.route('/admin', adminRoutes)
app.route('/ponto', pontoRoutes)
app.route('/supervisor', supervisorRoutes)
app.notFound((c) => c.json({ erro: 'Rota não encontrada.' }, 404))
app.onError((error, c) => { console.error(error); return c.json({ erro: 'Erro interno do servidor.' }, 500) })
export default app
