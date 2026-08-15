// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor, cleanup, fireEvent } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { AuthProvider } from './AuthContext'
import { VerifyCodeForm } from './VerifyCodeForm'
import { I18nProvider } from '../i18n'

/**
 * The code screen is the only thing standing between a new player and their
 * account, and every way it can fail is server-side. What matters is that
 * the server's own words reach the screen instead of a silent no-op.
 */

function problem(status: number, detail: string) {
  return new Response(JSON.stringify({ status, detail }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })
}

function renderForm() {
  return render(
    <MemoryRouter>
      <I18nProvider>
        <AuthProvider>
          <VerifyCodeForm email="player@example.com" />
        </AuthProvider>
      </I18nProvider>
    </MemoryRouter>,
  )
}

function typeCode(code: string) {
  fireEvent.change(screen.getByRole('textbox'), { target: { value: code } })
}

afterEach(() => {
  cleanup()
  localStorage.clear()
  vi.unstubAllGlobals()
})

describe('VerifyCodeForm', () => {
  it('shows the reason the server rejected the code', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(problem(400, 'That code is not right')))
    renderForm()

    typeCode('000000')
    screen.getByRole('button', { name: 'Confirm' }).click()

    expect(await screen.findByText('That code is not right')).toBeDefined()
  })

  it('tells the user when a code expired rather than failing quietly', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(problem(410, 'That code has expired — request a new one')))
    renderForm()

    typeCode('123456')
    screen.getByRole('button', { name: 'Confirm' }).click()

    expect(await screen.findByText('That code has expired — request a new one')).toBeDefined()
  })

  it('confirms a fresh code was requested', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ verificationRequired: true, email: 'player@example.com' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)
    renderForm()

    screen.getByRole('button', { name: 'Send another code' }).click()

    expect(await screen.findByText('A new code is on its way.')).toBeDefined()
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/auth/resend', expect.anything()))
  })

  it('empties the box after a refused code, so the next one can be typed', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(problem(400, 'That code is not right')))
    renderForm()

    typeCode('000000')
    screen.getByRole('button', { name: 'Confirm' }).click()

    await screen.findByText('That code is not right')
    // six digits and maxLength=6: leaving them there means nothing can be typed
    expect((screen.getByRole('textbox') as HTMLInputElement).value).toBe('')
  })

  it('confirms a resend and clears the old code', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ verificationRequired: true, email: 'player@example.com' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    ))
    renderForm()
    typeCode('111111')

    screen.getByRole('button', { name: 'Send another code' }).click()

    expect(await screen.findByText('A new code is on its way.')).toBeDefined()
    // the code on its way is a different one
    expect((screen.getByRole('textbox') as HTMLInputElement).value).toBe('')
  })

  it('surfaces a mail delivery failure instead of pretending a code was sent', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      problem(502, "We couldn't send your code — try again in a moment"),
    ))
    renderForm()

    screen.getByRole('button', { name: 'Send another code' }).click()

    expect(await screen.findByText("We couldn't send your code — try again in a moment")).toBeDefined()
    expect(screen.queryByText('A new code is on its way.')).toBeNull()
  })
})
