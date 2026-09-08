import { Hono } from 'hono'
import { z } from 'zod'
import { requireRole, requireUser, type AppEnv } from '../auth-runtime.js'
import { config } from '../config.js'
import { query, transaction } from '../db.js'
import { newId } from '../security.js'
import { errorPayload, logServerError } from '../observability.js'
import { parseJson, uuidSchema } from './shared.js'

export const workforceRoutes = new Hono<AppEnv>()
workforceRoutes.use('*', requireUser, requireRole('ADMIN', 'SUPERVISOR'))

const collaboratorInputSchema = z.object({
  nome: z.string().trim().min(2).max(160),
  setor: z.string().trim().max(120).optional().nullable(),
  turno: z.string().trim().max(80).optional().nullable(),
})

workforceRoutes.get('/colaboradores/:id/historico', async (c) => {
  const collaboratorId = c.req.param('id')
  if (!uuidSchema.safeParse(collaboratorId).success) {
    return c.json(errorPayload(c, 'Colaborador inválido.', 'COLLABORATOR_INVALID'), 400)
  }

  const diasRaw = Number(c.req.query('dias') ?? 30)
  const dias = Number.isInteger(diasRaw) ? Math.min(365, Math.max(1, diasRaw)) : 30

  const collaborator = await query<{
    id: string
    nome: string
    setor: string | null
    turno: string | null
    ativo: boolean
    criado_em: string
    atualizado_em: string
  }>(
    `select c.id,c.nome,c.setor,c.turno,c.ativo,c.criado_em::text,c.atualizado_em::text
       from colaboradores c
      where c.id=$1
      limit 1`,
    [collaboratorId],
  )
  const person = collaborator.rows[0]
  if (!person) return c.json(errorPayload(c, 'Colaborador não encontrado.', 'COLLABORATOR_NOT_FOUND'), 404)

  const summary = await query<{
    total: string
    media_segundos: number | null
    acima_limite: string
    fora_horario: string
  }>(
    `select count(*)::text as total,
            round(avg(extract(epoch from (coalesce(fim_em,now())-inicio_em))))::int as media_segundos,
            count(*) filter (
              where fim_em is not null
                and extract(epoch from (fim_em-inicio_em))>(limite_segundos+carencia_segundos)
            )::text as acima_limite,
            count(*) filter (where fora_horario=true)::text as fora_horario
       from pausas_cafe
      where colaborador_id=$1
        and inicio_em >= now() - ($2::text || ' days')::interval`,
    [collaboratorId, dias],
  )

  const pauses = await query<{
    id: string
    periodo: string
    inicio_em: string
    fim_em: string | null
    inicio_local: string
    fim_local: string | null
    duracao_segundos: number | null
    tempo_contado_segundos: number | null
    limite_segundos: number
    carencia_segundos: number
    fora_horario: boolean
    excedeu_limite: boolean
  }>(
    `select id,periodo,inicio_em::text,fim_em::text,
            to_char(inicio_em at time zone $3,'DD/MM/YYYY HH24:MI') as inicio_local,
            case when fim_em is null then null else to_char(fim_em at time zone $3,'DD/MM/YYYY HH24:MI') end as fim_local,
            case when fim_em is null then null else extract(epoch from (fim_em-inicio_em))::int end as duracao_segundos,
            case
              when fim_em is null then null
              else greatest(0,extract(epoch from (fim_em-inicio_em))::int - carencia_segundos)
            end as tempo_contado_segundos,
            limite_segundos,carencia_segundos,fora_horario,
            case
              when fim_em is null then false
              else extract(epoch from (fim_em-inicio_em))>(limite_segundos+carencia_segundos)
            end as excedeu_limite
       from pausas_cafe
      where colaborador_id=$1
        and inicio_em >= now() - ($2::text || ' days')::interval
      order by inicio_em desc
      limit 100`,
    [collaboratorId, dias, config.appTimezone],
  )

  // Últimos códigos emitidos para esta pessoa. É o que substituiu o histórico
  // biométrico: quem liberou, quando, e se o passe chegou a ser usado.
  const accessCodes = await query<{
    id: string
    criado_em: string
    expira_em: string
    saida_em: string | null
    retorno_em: string | null
    cancelado_em: string | null
    motivo: string | null
    emitido_por_nome: string | null
    emitido_por_tipo: string
  }>(
    `select id,criado_em::text,expira_em::text,saida_em::text,retorno_em::text,
            cancelado_em::text,motivo,emitido_por_nome,emitido_por_tipo
       from codigos_acesso
      where colaborador_id=$1
      order by criado_em desc
      limit 20`,
    [collaboratorId],
  )

  const row = summary.rows[0]
  return c.json({
    colaborador: {
      id: person.id,
      nome: person.nome,
      setor: person.setor,
      turno: person.turno,
      ativo: person.ativo,
      criadoEm: person.criado_em,
      atualizadoEm: person.atualizado_em,
    },
    periodoDias: dias,
    resumo: {
      totalPausas: Number(row?.total ?? 0),
      mediaSegundos: row?.media_segundos ?? null,
      acimaLimite: Number(row?.acima_limite ?? 0),
      foraHorario: Number(row?.fora_horario ?? 0),
    },
    pausas: pauses.rows.map((pause) => ({
      id: pause.id,
      periodo: pause.periodo,
      inicioEm: pause.inicio_em,
      fimEm: pause.fim_em,
      inicioLocal: pause.inicio_local,
      fimLocal: pause.fim_local,
      duracaoSegundos: pause.duracao_segundos,
      tempoContadoSegundos: pause.tempo_contado_segundos,
      limiteSegundos: pause.limite_segundos,
      carenciaSegundos: pause.carencia_segundos,
      foraHorario: pause.fora_horario,
      excedeuLimite: pause.excedeu_limite,
    })),
    codigosAcesso: {
      retencaoDias: config.accessCodeRetentionDays,
      // O código em si nunca sai daqui: o histórico mostra o que aconteceu com
      // ele, não o segredo. Para reler um código vivo existe GET /codigos.
      eventos: accessCodes.rows.map((code) => ({
        id: code.id,
        criadoEm: code.criado_em,
        expiraEm: code.expira_em,
        saidaEm: code.saida_em,
        retornoEm: code.retorno_em,
        canceladoEm: code.cancelado_em,
        motivo: code.motivo,
        emitidoPor: code.emitido_por_nome,
        emitidoPorTipo: code.emitido_por_tipo,
      })),
    },
  })
})

workforceRoutes.post('/colaboradores/importar', async (c) => {
  if (c.get('user').papel !== 'ADMIN') {
    return c.json(errorPayload(c, 'Somente o Administrador pode importar colaboradores.', 'ADMIN_ONLY'), 403)
  }

  const body = await parseJson(c, z.object({
    colaboradores: z.array(collaboratorInputSchema).min(1).max(500),
  }))
  if (!body.ok) return body.response

  try {
    const result = await transaction(async (client) => {
      const created: Array<{ id: string; nome: string; setor: string | null; turno: string | null }> = []
      const existing: Array<{ nome: string; motivo: string }> = []

      for (const item of body.data.colaboradores) {
        const setor = item.setor?.trim() || null
        const turno = item.turno?.trim() || null
        const duplicate = await client.query<{ id: string }>(
          `select id from colaboradores
            where ativo=true
              and lower(trim(nome))=lower(trim($1))
              and lower(coalesce(trim(setor),''))=lower(coalesce(trim($2),''))
              and lower(coalesce(trim(turno),''))=lower(coalesce(trim($3),''))
            limit 1`,
          [item.nome, setor, turno],
        )
        if (duplicate.rows[0]) {
          existing.push({ nome: item.nome, motivo: 'Já existe um colaborador ativo com os mesmos dados.' })
          continue
        }

        const id = newId()
        await client.query(
          'insert into colaboradores (id,matricula,nome,setor,turno) values ($1,null,$2,$3,$4)',
          [id, item.nome, setor, turno],
        )
        created.push({ id, nome: item.nome, setor, turno })
      }

      const actor = c.get('user')
      await client.query(
        `insert into auditoria (ator_auth_id,ator_tipo,acao,entidade,detalhes)
         values ($1,'ADMIN','IMPORTAR_COLABORADORES','COLABORADOR',$2::jsonb)`,
        [actor.id, JSON.stringify({ recebidos: body.data.colaboradores.length, criados: created.length, existentes: existing.length })],
      )

      return { created, existing }
    })

    return c.json({
      recebidos: body.data.colaboradores.length,
      criados: result.created.length,
      existentes: result.existing.length,
      colaboradoresCriados: result.created,
      ignorados: result.existing,
    })
  } catch (error) {
    logServerError(c, 'bulk_collaborator_import_failure', error)
    return c.json(errorPayload(c, 'Não foi possível concluir a importação.', 'COLLABORATOR_IMPORT_FAILED'), 500)
  }
})

workforceRoutes.put('/colaboradores/lote', async (c) => {
  if (c.get('user').papel !== 'ADMIN') {
    return c.json(errorPayload(c, 'Somente o Administrador pode alterar colaboradores em lote.', 'ADMIN_ONLY'), 403)
  }

  const body = await parseJson(c, z.object({
    ids: z.array(uuidSchema).min(1).max(200),
    setor: z.string().trim().max(120).optional().nullable(),
    turno: z.string().trim().max(80).optional().nullable(),
    ativo: z.boolean().optional(),
  }).refine((value) => value.setor !== undefined || value.turno !== undefined || value.ativo !== undefined, {
    message: 'Informe ao menos uma alteração.',
  }))
  if (!body.ok) return body.response

  const uniqueIds = [...new Set(body.data.ids)]
  try {
    const result = await transaction(async (client) => {
      if (body.data.ativo === false) {
        const open = await client.query<{ id: string; nome: string }>(
          `select distinct c.id,c.nome
             from colaboradores c
             join pausas_cafe p on p.colaborador_id=c.id and p.fim_em is null
            where c.id=any($1::uuid[])`,
          [uniqueIds],
        )
        if (open.rows.length > 0) {
          return { blocked: open.rows, updated: [] as Array<{ id: string; nome: string }> }
        }
      }

      const updated = await client.query<{ id: string; nome: string }>(
        `update colaboradores
            set setor=case when $2::boolean then $3 else setor end,
                turno=case when $4::boolean then $5 else turno end,
                ativo=coalesce($6,ativo),
                atualizado_em=now()
          where id=any($1::uuid[])
          returning id,nome`,
        [
          uniqueIds,
          body.data.setor !== undefined,
          body.data.setor?.trim() || null,
          body.data.turno !== undefined,
          body.data.turno?.trim() || null,
          body.data.ativo ?? null,
        ],
      )

      const actor = c.get('user')
      await client.query(
        `insert into auditoria (ator_auth_id,ator_tipo,acao,entidade,detalhes)
         values ($1,'ADMIN','EDITAR_COLABORADORES_LOTE','COLABORADOR',$2::jsonb)`,
        [actor.id, JSON.stringify({ ids: uniqueIds, setor: body.data.setor, turno: body.data.turno, ativo: body.data.ativo })],
      )

      return { blocked: [] as Array<{ id: string; nome: string }>, updated: updated.rows }
    })

    if (result.blocked.length > 0) {
      return c.json(errorPayload(c, 'Existem colaboradores com pausa aberta. Finalize as pausas antes de desativá-los.', 'OPEN_PAUSE_BLOCKS_BULK_UPDATE', {
        bloqueados: result.blocked,
      }), 409)
    }

    return c.json({ ok: true, atualizados: result.updated.length, colaboradores: result.updated })
  } catch (error) {
    logServerError(c, 'bulk_collaborator_update_failure', error)
    return c.json(errorPayload(c, 'Não foi possível alterar os colaboradores selecionados.', 'COLLABORATOR_BULK_UPDATE_FAILED'), 500)
  }
})
