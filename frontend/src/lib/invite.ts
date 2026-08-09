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
