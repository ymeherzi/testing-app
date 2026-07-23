const PENDING_KEY = 'predictor.pendingInvite'

export function inviteLink(code: string): string {
  return `${window.location.origin}/join/${code}`
}

export function stashPendingInvite(code: string) {
  localStorage.setItem(PENDING_KEY, code.trim().toUpperCase())
}

export function popPendingInvite(): string | null {
  const code = localStorage.getItem(PENDING_KEY)
  if (code) {
    localStorage.removeItem(PENDING_KEY)
  }
  return code
}

export function peekPendingInvite(): string | null {
  return localStorage.getItem(PENDING_KEY)
}
