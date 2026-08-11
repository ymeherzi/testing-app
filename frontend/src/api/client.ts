import { clearStored, readStored, writeStored } from '../lib/storage'

const TOKEN_KEY = 'token'

export function getToken(): string | null {
  return readStored(TOKEN_KEY)
}

export function setToken(token: string | null) {
  if (token) {
    writeStored(TOKEN_KEY, token)
  } else {
    clearStored(TOKEN_KEY)
  }
}

export class ApiError extends Error {
  readonly status: number
  readonly errors: Record<string, string> | undefined
  /**
   * A translation key when the server has one for this failure, so a French
   * player is not shown an English sentence. `message` stays as the fallback.
   */
  readonly code: string | undefined

  constructor(status: number, message: string, errors?: Record<string, string>, code?: string) {
    super(message)
    this.status = status
    this.errors = errors
    this.code = code
  }
}

export async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers as Record<string, string> | undefined),
  }
  const token = getToken()
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }
  const response = await fetch(path, { ...options, headers })
  if (response.status === 401 && token) {
    setToken(null)
    window.location.href = '/login'
  }
  if (!response.ok) {
    let message = `Request failed (${response.status})`
    let errors: Record<string, string> | undefined
    let code: string | undefined
    try {
      const problem = await response.json()
      message = problem.detail ?? problem.message ?? problem.title ?? message
      errors = problem.errors
      code = problem.code
    } catch {
      // non-JSON error body: keep the generic message
    }
    throw new ApiError(response.status, message, errors, code)
  }
  // 204, or a 200 from a handler that returns nothing: an empty body is not a
  // failure, and parsing it as JSON is what made "enable notifications" report
  // "Unexpected end of JSON input" on a call that had in fact succeeded
  const body = await response.text()
  return (body ? JSON.parse(body) : undefined) as T
}
