// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, api } from './client'

/**
 * Turning notifications on reported "Failed to execute 'json' on 'Response':
 * Unexpected end of JSON input" — on a call that had in fact succeeded. The
 * handler returns nothing, so the body is empty, and only 204 was treated as
 * "nothing to read".
 */

afterEach(() => {
  vi.unstubAllGlobals()
  localStorage.clear()
})

function answer(status: number, body: string, contentType = 'application/json') {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
    new Response(body || null, { status, headers: { 'Content-Type': contentType } }),
  ))
}

describe('api', () => {
  it('accepts an empty body on a 200 instead of failing to parse it', async () => {
    answer(200, '')

    await expect(api('/api/push/subscriptions', { method: 'POST' })).resolves.toBeUndefined()
  })

  it('still accepts a 204', async () => {
    answer(204, '')

    await expect(api('/api/push/subscriptions', { method: 'DELETE' })).resolves.toBeUndefined()
  })

  it('reads a body when there is one', async () => {
    answer(200, JSON.stringify({ subscribed: true }))

    await expect(api('/api/push/subscriptions')).resolves.toEqual({ subscribed: true })
  })

  it('carries the server error code so the client can translate it', async () => {
    answer(400, JSON.stringify({
      detail: 'Use at least 10 characters',
      code: 'password.tooShort',
      errors: { password: 'Use at least 10 characters' },
    }), 'application/problem+json')

    const failure = await api('/api/auth/signup', { method: 'POST' }).catch((e) => e)
    expect(failure).toBeInstanceOf(ApiError)
    expect((failure as ApiError).code).toBe('password.tooShort')
    expect((failure as ApiError).errors?.password).toBeDefined()
  })
})
