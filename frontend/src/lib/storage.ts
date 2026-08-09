/**
 * Local storage, namespaced in one place.
 *
 * The product has been renamed twice already; keeping the prefix here means
 * the next rename touches one line instead of five files. No carry-over
 * from the former `predictor.` / `prono10.` names: nobody is signed in yet,
 * so there is no session to preserve.
 */

const PREFIX = 'tengames.'

export function readStored(name: string): string | null {
  return localStorage.getItem(PREFIX + name)
}

export function writeStored(name: string, value: string) {
  localStorage.setItem(PREFIX + name, value)
}

export function clearStored(name: string) {
  localStorage.removeItem(PREFIX + name)
}
