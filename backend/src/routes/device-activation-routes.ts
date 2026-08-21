import { Hono } from 'hono'
import { z } from 'zod'
import { requireRole, requireUser, type AppEnv } from '../auth-runtime.js'
import { biometricKey, config } from '../config.js'
import { query } from '../db.js'
import {
  decryptDeviceRegistrationToken,
  deviceRegistrationFingerprint,
  encryptDeviceRegistrationToken,
  isValidDeviceRegistrationIdempotencyKey,
  normalizeDeviceRegistrationName,
} from '../device-registration-idempotency.js'
import { errorPayload, logServerError, safeErrorDescriptor } from '../observability.js'
import { hashDeviceUnlockPin, hashToken, newDeviceToken, newId } from '../security.js'
import { parseJson } from './shared.js'

export const deviceActivationRoutes = new Hono<AppEnv>()
deviceActivationRoutes.use('*', requireUser, requireRole('ADMIN'))

type DeviceRegistrationRow = {
  idempotency_key: string
  ator_auth_id: string
  request_fingerprint: string
  request_nonce: string
  dispositivo_id: string
  token_ciphertext: Buffer
  token_iv: Buffer
  token_auth_tag: Buffer
  http_status: number
  expira_em: string
  criado_agora: boolean
  nome_criado: string | null
}

deviceActivationRoutes.post('/device-activation', async (c) => {
  const body = await parseJson(c, z.object({
    nome: z.string().trim().min(2).max(120),
    pin: z.string().trim().regex(/^\d{4,12}$/),
  }))
  if (!body.ok) return body.response

  const incomingIdempotencyKey = c.req.header('Idempotency-Key')?.trim()
  if (!incomingIdempotencyKey) {
    return c.json(
      errorPayload(
        c,
        'A chave de idempotência é obrigatória para cadastrar um dispositivo.',
        'IDEMPOTENCY_KEY_REQUIRED',
      ),
      400,
    )
  }
  if (!isValidDeviceRegistrationIdempotencyKey(incomingIdempotencyKey)) {
    return c.json(
      errorPayload(c, 'Chave de idempotência inválida.', 'INVALID_IDEMPOTENCY_KEY'),
      400,
    )
  }

  const idempotencyKey = incomingIdempotencyKey
  const actor = c.get('user')
  const deviceName = normalizeDeviceRegistrationName(body.data.nome)
  const fingerprint = deviceRegistrationFingerprint(
    actor.id,
    deviceName,
    body.data.pin,
    config.codePepper,
  )

  for (let attempt = 0; attempt < 3; attempt += 1) {
    const deviceId = newId()
    const requestNonce = newId()
    const token = newDeviceToken(10)
    const unlockPinHash = hashDeviceUnlockPin(deviceId, body.data.pin)
    let stage = 'DEVICE_CRYPTO'

    try {
      const registrationContext = {
        idempotencyKey,
        actorId: actor.id,
        fingerprint,
        deviceId,
      }
      const encryptedToken = encryptDeviceRegistrationToken(token, biometricKey(), registrationContext)

      stage = 'DEVICE_ATOMIC_CREATE'
      const created = await query<DeviceRegistrationRow>(
        `with idempotencia as (
           insert into device_registration_idempotency (
             idempotency_key,
             ator_auth_id,
             request_fingerprint,
             request_nonce,
             dispositivo_id,
             token_ciphertext,
             token_iv,
             token_auth_tag,
             http_status,
             expira_em
           )
           values (
             $1,$2,$3,$4::uuid,$5::uuid,$6,$7,$8,201,
             now() + ($9::text || ' seconds')::interval
           )
           on conflict (idempotency_key) do update
             set idempotency_key=excluded.idempotency_key
           returning
             idempotency_key,
             ator_auth_id,
             request_fingerprint,
             request_nonce,
             dispositivo_id,
             token_ciphertext,
             token_iv,
             token_auth_tag,
             http_status,
             expira_em::text
         ),
         novo_dispositivo as (
           insert into dispositivos (
             id,
             nome,
             token_hash,
             unlock_pin_hash,
             unlock_pin_updated_at
           )
           select
             $5::uuid,
             $10,
             $11,
             $12,
             now()
           from idempotencia
           where request_nonce=$4::uuid
           returning id,nome
         ),
         auditoria_criacao as (
           insert into auditoria (
             ator_auth_id,
             ator_tipo,
             acao,
             entidade,
             entidade_id,
             detalhes
           )
           select
             $2,
             'ADMIN',
             'CRIAR_DISPOSITIVO',
             'DISPOSITIVO',
             novo_dispositivo.id::text,
             $13::jsonb
           from novo_dispositivo
           returning id
         )
         select
           i.idempotency_key,
           i.ator_auth_id,
           i.request_fingerprint,
           i.request_nonce,
           i.dispositivo_id,
           i.token_ciphertext,
           i.token_iv,
           i.token_auth_tag,
           i.http_status,
           i.expira_em,
           (i.request_nonce=$4::uuid) as criado_agora,
           (select nome from novo_dispositivo limit 1) as nome_criado
         from idempotencia i
         where (select count(*) from auditoria_criacao) >= 0
         limit 1`,
        [
          idempotencyKey,
          actor.id,
          fingerprint,
          requestNonce,
          deviceId,
          encryptedToken.ciphertext,
          encryptedToken.iv,
          encryptedToken.authTag,
          config.deviceRegistrationIdempotencyTtlSeconds,
          deviceName,
          hashToken(token),
          unlockPinHash,
          JSON.stringify({
            nome: deviceName,
            pinConfigurado: true,
            idempotencia: true,
          }),
        ],
      )

      const registration = created.rows[0]
      if (!registration) throw new Error('DEVICE_NOT_PERSISTED')

      stage = 'DEVICE_REPLAY_VALIDATION'
      if (registration.ator_auth_id !== actor.id || registration.request_fingerprint !== fingerprint) {
        return c.json(
          errorPayload(
            c,
            'Esta chave de idempotência já foi usada com outros dados.',
            'IDEMPOTENCY_KEY_REUSED',
          ),
          409,
        )
      }

      if (new Date(registration.expira_em).getTime() <= Date.now()) {
        return c.json(
          errorPayload(
            c,
            'Esta tentativa de cadastro expirou. Inicie um novo cadastro.',
            'IDEMPOTENCY_KEY_EXPIRED',
          ),
          409,
        )
      }

      let responseToken = token
      if (!registration.criado_agora) {
        stage = 'DEVICE_REPLAY_DECRYPT'
        responseToken = decryptDeviceRegistrationToken(
          registration.token_ciphertext,
          registration.token_iv,
          registration.token_auth_tag,
          biometricKey(),
          {
            idempotencyKey: registration.idempotency_key,
            actorId: registration.ator_auth_id,
            fingerprint: registration.request_fingerprint,
            deviceId: registration.dispositivo_id,
          },
        )
      }

      return c.json({
        id: registration.dispositivo_id,
        nome: registration.nome_criado || deviceName,
        token: responseToken,
        pinConfigurado: true,
        replayIdempotente: !registration.criado_agora,
        aviso: 'Este token de ativação de 10 caracteres é exibido uma única vez.',
        requestId: c.get('requestId'),
      }, registration.criado_agora ? 201 : 200)
    } catch (error) {
      const databaseError = error as { code?: string; constraint?: string }
      const tokenCollision = databaseError.code === '23505' && databaseError.constraint === 'dispositivos_token_hash_key'
      if (tokenCollision && attempt < 2) continue

      const descriptor = safeErrorDescriptor(error)
      logServerError(c, 'device_create_failure', error, {
        stage,
        attempt: attempt + 1,
        tokenCollision,
        idempotencyProvided: true,
      })

      const diagnosticCode = tokenCollision ? 'DEVICE_TOKEN_COLLISION' : 'DEVICE_CREATE_FAILED'
      const diagnosticMessage = tokenCollision
        ? 'Não foi possível gerar um token de ativação único.'
        : `Não foi possível criar o dispositivo. Diagnóstico ${stage}/${descriptor.code}.`

      return c.json(
        errorPayload(c, diagnosticMessage, diagnosticCode, {
          diagnostico: {
            etapa: stage,
            tipo: descriptor.code,
            codigoBanco: descriptor.databaseCode,
          },
        }),
        500,
      )
    }
  }

  return c.json(
    errorPayload(c, 'Não foi possível gerar um token de ativação único.', 'DEVICE_TOKEN_COLLISION'),
    500,
  )
})
