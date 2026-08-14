import { Hono } from 'hono'
import { cors } from 'hono/cors'
import { secureHeaders } from 'hono/secure-headers'
import { auth, type AppEnv } from './auth-runtime.js'
import { query } from './db.js'
import { adminRoutes } from './routes/admin-routes.js'
import { authorizationRoutes } from './routes/authorization-routes.js'
import { liveRoutes } from './routes/live-routes.js'
import { pontoRoutes } from './routes/ponto-routes.js'
import { pontoStatusRoutes } from './routes/ponto-status-routes.js'
import { reportRoutes } from './routes/report-routes.js'
import { setupRoutes } from './routes/setup-routes.js'

const app = new Hono<AppEnv>()

app.use('*', secureHeaders())
app.use('*', cors({
    origin: '*',
    allowHeaders: ['Content-Type', 'Authorization', 'X-Device-Token'],
    exposeHeaders: ['set-auth-token'],
    allowMethods: ['GET', 'POST', 'PUT', 'OPTIONS'],
}))

app.on(['POST', 'GET'], '/api/auth/*', (c) => auth.handler(c.req.raw))
app.get('/', (c) => c.json({ app: 'Ponto Café API', status: 'ok', versao: '0.3.0' }))
app.get('/health', async (c) => {
    const result = await query<{ agora: string }>('select now()::text as agora')
    return c.json({ status: 'ok', banco: 'ok', servidor: result.rows[0]?.agora })
})

app.route('/setup', setupRoutes)
app.route('/admin', adminRoutes)
app.route('/ponto', pontoRoutes)
app.route('/ponto', pontoStatusRoutes)
app.route('/supervisor', liveRoutes)
app.route('/supervisor', authorizationRoutes)
app.route('/supervisor', reportRoutes)

app.notFound((c) => c.json({ erro: 'Rota não encontrada.' }, 404))
app.onError((error, c) => {
    console.error(error)
    return c.json({ erro: 'Erro interno do servidor.' }, 500)
})

export default app
