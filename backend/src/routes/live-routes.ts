import { Hono } from 'hono'
import { requireRole, requireUser, type AppEnv } from '../auth-runtime.js'
import { query } from '../db.js'

export const liveRoutes = new Hono<AppEnv>()
liveRoutes.use('*', requireUser, requireRole('ADMIN', 'SUPERVISOR'))
liveRoutes.get('/pausas/ativas', async (c) => {
  const result = await query(`select p.id,p.periodo,p.inicio_em,p.limite_segundos,p.fora_horario,floor(extract(epoch from (now()-p.inicio_em)))::int as tempo_segundos,col.id as colaborador_id,col.nome,col.matricula,col.setor from pausas_cafe p join colaboradores col on col.id=p.colaborador_id where p.fim_em is null order by p.inicio_em`)
  return c.json({ agora: new Date().toISOString(), pausas: result.rows })
})
