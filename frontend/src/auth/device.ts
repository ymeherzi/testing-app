import { readStored, writeStored } from '../lib/storage'

const DEVICE_KEY = 'device'

/** Opaque token proving this device already passed a code check. */
export function deviceToken(): string | null {
  return readStored(DEVICE_KEY)
}

export function rememberDeviceToken(token: string | null) {
  if (token) {
    writeStored(DEVICE_KEY, token)
  }
}
