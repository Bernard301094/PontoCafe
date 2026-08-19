import { createHmac } from 'node:crypto'
import { config } from './config.js'
import { secureHexEquals } from './security.js'

export type AvatarStoredObject = {
  arrayBuffer(): Promise<ArrayBuffer>
  etag?: string
  httpMetadata?: {
    contentType?: string
    cacheControl?: string
  }
}

export type AvatarBucket = {
  put(
    key: string,
    value: ArrayBuffer,
    options?: {
      httpMetadata?: {
        contentType?: string
        cacheControl?: string
      }
    },
  ): Promise<unknown>
  get(key: string): Promise<AvatarStoredObject | null>
  delete(key: string): Promise<void>
}

export const AVATAR_MAX_BYTES = 28 * 1024

export function avatarObjectKey(collaboratorId: string): string {
  return `collaborators/${collaboratorId}/avatar.webp`
}

function avatarSignature(collaboratorId: string, version: number): string {
  return createHmac('sha256', config.codePepper)
    .update(`avatar:${collaboratorId}:${version}`)
    .digest('hex')
}

export function avatarUrl(origin: string, collaboratorId: string, version: number): string | null {
  if (!Number.isInteger(version) || version <= 0) return null
  const sig = avatarSignature(collaboratorId, version)
  return `${origin}/media/avatars/${encodeURIComponent(collaboratorId)}?v=${version}&sig=${sig}`
}

export function validateAvatarSignature(collaboratorId: string, version: number, signature: string): boolean {
  if (!Number.isInteger(version) || version <= 0 || !/^[0-9a-f]{64}$/i.test(signature)) return false
  return secureHexEquals(avatarSignature(collaboratorId, version), signature)
}

export function isWebP(bytes: Uint8Array): boolean {
  if (bytes.byteLength < 12) return false
  return (
    bytes[0] === 0x52 && bytes[1] === 0x49 && bytes[2] === 0x46 && bytes[3] === 0x46 &&
    bytes[8] === 0x57 && bytes[9] === 0x45 && bytes[10] === 0x42 && bytes[11] === 0x50
  )
}
