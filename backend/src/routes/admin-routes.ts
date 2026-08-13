import { Hono } from 'hono'
import { z } from 'zod'
import { requireRole, requireUser, type AppEnv } from '../auth-runtime.js'
import { query } from '../db.js'
import { encryptEmbedding, hashToken, newId, newToken } from '../security.js'
import { embeddingSchema, parseJson, uuidSchema } from './shared.js'

export const adminRoutes = new Hono<AppEnv>()
adminRoutes.use('*', requireUser, requireRole('ADMIN'))

adminRoutes.post('/dispositivos', async (c) => {
  const body = await parseJson(c, z.object({ nome: z.string().trim().min(2).max(120) }))
  if (!body.ok) return body.response
  const id = newId()
  const token = newToken()
  await query('insert into dispositivos (id,nome,token_hash) values ($1,$2,$3)', [id, body.data.nome, hashToken(token)])
  return c.json({ id, nome: body.data.nome, token, aviso: 'Este token é exibido uma única vez.' }, 201)
})

adminRoutes.get('/colaboradores', async (c) => {
  const result = await query('select id,matricula,nome,setor,turno,ativo,criado_em from colaboradores order by nome')
  return c.json({ colaboradores: result.rows })
})

adminRoutes.post('/colaboradores', async (c) => {
  const body = await parseJson(c, z.object({
    matricula: z.string().trim().max(50).optional().nullable(),
    nome: z.string().trim().min(2).max(160),
    setor: z.string().trim().max(120).optional().nullable(),
    turno: z.string().trim().max(80).optional().nullable(),
  }))
  if (!body.ok) return body.response
  const id = newId()
  await query('insert into colaboradores (id,matricula,nome,setor,turno) values ($1,$2,$3,$4,$5)', [id, body.data.matricula ?? null, body.data.nome, body.data.setor ?? null, body.data.turno ?? null])
  return c.json({ id, ...body.data, ativo: true }, 201)
})

adminRoutes.put('/colaboradores/:id/biometria', async (c) => {
  const colaboradorId = c.req.param('id')
  if (!uuidSchema.safeParse(colaboradorId).success) return c.json({ erro: 'Colaborador inválido.' }, 400)
  const body = await parseJson(c, z.object({ embedding: embeddingSchema, modelo: z.string().trim().min(2).max(100), versaoModelo: z.string().trim().min(1).max(50) }))
  if (!body.ok) return body.response
  const encrypted = encryptEmbedding(body.data.embedding)
  await query(`insert into templates_faciais (id,colaborador_id,template_cifrado,iv,auth_tag,dimensao,modelo,versao_modelo)
    values ($1,$2,$3,$4,$5,$6,$7,$8)
    on conflict (colaborador_id) do update set template_cifrado=excluded.template_cifrado,iv=excluded.iv,auth_tag=excluded.auth_tag,dimensao=excluded.dimensao,modelo=excluded.modelo,versao_modelo=excluded.versao_modelo,atualizado_em=now()`,
    [newId(), colaboradorId, encrypted.ciphertext, encrypted.iv, encrypted.authTag, body.data.embedding.length, body.data.modelo, body.data.versaoModelo])
  return c.json({ ok: true, colaboradorId, dimensao: body.data.embedding.length })
})
