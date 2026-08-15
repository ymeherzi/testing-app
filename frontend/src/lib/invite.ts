import { clearStored, readStored, writeStored } from './storage'

const PENDING_KEY = 'pendingInvite'

export function inviteLink(code: string): string {
  return `${window.location.origin}/join/${code}`
}

export function stashPendingInvite(code: string) {
  writeStored(PENDING_KEY, code.trim().toUpperCase())
}

export function popPendingInvite(): string | null {
  const code = readStored(PENDING_KEY)
  if (code) {
    clearStored(PENDING_KEY)
  }
  return code
}

export function peekPendingInvite(): string | null {
  return readStored(PENDING_KEY)
}

/**
 * Forgets the stashed invite. Kept apart from reading it: a code must survive
 * a failed attempt, a reload or a detour through the login screen, and only go
 * once the server has actually answered.
 */
export function clearPendingInvite() {
  clearStored(PENDING_KEY)
}
