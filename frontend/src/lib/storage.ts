/**
 * Local storage under the `prono10.` prefix, carrying over anything left
 * behind by the old `predictor.` name.
 *
 * Without the carry-over, renaming the keys would sign every existing
 * player out and throw away their remembered device — and since signing
 * back in needs a code delivered by email, that is a lockout rather than an
 * inconvenience. The old key is read once, rewritten under the new name and
 * removed, so this costs nothing after the first visit.
 */

const PREFIX = 'prono10.'
const FORMER_PREFIX = 'predictor.'

export function readStored(name: string): string | null {
  const current = localStorage.getItem(PREFIX + name)
  if (current !== null) {
    return current
  }
  const carried = localStorage.getItem(FORMER_PREFIX + name)
  if (carried !== null) {
    localStorage.setItem(PREFIX + name, carried)
    localStorage.removeItem(FORMER_PREFIX + name)
  }
  return carried
}

export function writeStored(name: string, value: string) {
  localStorage.setItem(PREFIX + name, value)
}

export function clearStored(name: string) {
  localStorage.removeItem(PREFIX + name)
  localStorage.removeItem(FORMER_PREFIX + name)
}
