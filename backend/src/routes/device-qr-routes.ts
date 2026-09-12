import { Hono } from 'hono'
import { z } from 'zod'
import { requireRole, requireUser, type AppEnv } from '../auth-runtime.js'
import { query, transaction } from '../db.js'
import { parseJson, uuidSchema } from './shared.js'

/**
 * A porta do QR, aparelho a aparelho.
 *
 * Fica fora de `device-management-routes` de propósito: aquele módulo inteiro é
 * ADMIN, porque cria, renomeia e apaga aparelhos. Liberar a leitura por câmara
 * não é uma dessas coisas — é uma decisão de operação, do turno, e o Supervisor
 * que está no chão é quem sabe se hoje a fila do café comporta a câmara. Por
 * isso este router aceita os dois papéis, e nada mais faz.
 *
 * O que a liberação é, e o que não é: um QR carrega o mesmo código de 6
 * caracteres que a pessoa poderia digitar à mão, portanto isto não fecha uma
 * fronteira de segurança. Fecha uma porta operacional -- decide quando a
 * operação passa a aceitar leitura por câmara -- e deixa em auditoria quem a
 * abriu e quando.
 */
export const deviceQrRoutes = new Hono<AppEnv>()
deviceQrRoutes.use('*', requireUser, requireRole('ADMIN', 'SUPERVISOR'))

deviceQrRoutes.get('/devices/qr', async (c) => {
  const result = await query<{
    id: string
    nome: string
    ativo: boolean
    qrHabilitado: boolean
    qrAtualizadoEm: string | null
  }>(
    `select id,nome,ativo,
            qr_habilitado as "qrHabilitado",
            qr_atualizado_em::text as "qrAtualizadoEm"
       from dispositivos
      order by nome`,
  )
  return c.json({ dispositivos: result.rows })
})

deviceQrRoutes.put('/devices/:id/qr', async (c) => {
  const id = uuidSchema.safeParse(c.req.param('id'))
  if (!id.success) return c.json({ erro: 'Dispositivo inválido.' }, 400)

  const body = await parseJson(c, z.object({ habilitado: z.boolean() }))
  if (!body.ok) return body.response

  const actor = c.get('user')

  const updated = await transaction(async (client) => {
    const row = (await client.query<{ id: string; nome: string; qr_habilitado: boolean }>(
      'select id,nome,qr_habilitado from dispositivos where id=$1 for update',
      [id.data],
    )).rows[0]
    if (!row) return null

    // Sem mudança não se grava nem se audita: um botão carregado duas vezes
    // não é um facto operacional, e enchia a trilha de linhas iguais.
    if (row.qr_habilitado === body.data.habilitado) {
      return { id: row.id, nome: row.nome, qrHabilitado: row.qr_habilitado, mudou: false }
    }

    await client.query(
      'update dispositivos set qr_habilitado=$2, qr_atualizado_em=now(), atualizado_em=now() where id=$1',
      [id.data, body.data.habilitado],
    )
    await client.query(
      `insert into auditoria (ator_auth_id,ator_tipo,acao,entidade,entidade_id,detalhes)
       values ($1,$2,$3,'DISPOSITIVO',$4,$5::jsonb)`,
      [
        actor.id,
        actor.papel,
        body.data.habilitado ? 'LIBERAR_QR_DISPOSITIVO' : 'BLOQUEAR_QR_DISPOSITIVO',
        row.id,
        JSON.stringify({ nome: row.nome, habilitado: body.data.habilitado }),
      ],
    )
    return { id: row.id, nome: row.nome, qrHabilitado: body.data.habilitado, mudou: true }
  })

  if (!updated) return c.json({ erro: 'Dispositivo não encontrado.' }, 404)

  return c.json({
    ...updated,
    aviso: updated.qrHabilitado
      ? 'Este aparelho passa a aceitar o código lido pela câmara. O aparelho aplica a mudança no próximo arranque ou atualização.'
      : 'Este aparelho volta a aceitar apenas o código digitado.',
  })
})
