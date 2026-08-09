// @vitest-environment jsdom
import { afterEach, describe, expect, it } from 'vitest'
import { clearStored, readStored, writeStored } from './storage'

/**
 * The rename from `predictor.` to `prono10.` must not cost anyone their
 * session: signing back in requires an emailed code, so a dropped token is
 * a lockout, not a re-login.
 */
describe('storage carry-over', () => {
  afterEach(() => localStorage.clear())

  it('keeps a session stored under the former name', () => {
    localStorage.setItem('predictor.token', 'jwt-from-before-the-rename')

    expect(readStored('token')).toBe('jwt-from-before-the-rename')
  })

  it('moves the value across so the old key is read only once', () => {
    localStorage.setItem('predictor.device', 'trusted-device-token')

    readStored('device')

    expect(localStorage.getItem('prono10.device')).toBe('trusted-device-token')
    expect(localStorage.getItem('predictor.device')).toBeNull()
  })

  it('prefers the new key when both exist', () => {
    localStorage.setItem('predictor.token', 'stale')
    localStorage.setItem('prono10.token', 'current')

    expect(readStored('token')).toBe('current')
  })

  it('reports nothing stored when neither name is present', () => {
    expect(readStored('token')).toBeNull()
  })

  it('clears both names, so a sign-out cannot resurrect the old session', () => {
    localStorage.setItem('predictor.token', 'stale')
    writeStored('token', 'current')

    clearStored('token')

    expect(localStorage.getItem('prono10.token')).toBeNull()
    expect(localStorage.getItem('predictor.token')).toBeNull()
  })
})
