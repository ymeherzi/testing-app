const TOKEN_KEY = 'predictor.token'

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string | null) {
  if (token) {
    localStorage.setItem(TOKEN_KEY, token)
  } else {
    localStorage.removeItem(TOKEN_KEY)
  }
}

export class ApiError extends Error {
  readonly status: number
  readonly errors: Record<string, string> | undefined

  constructor(status: number, message: string, errors?: Record<string, string>) {
    super(message)
    this.status = status
    this.errors = errors
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
    try {
      const problem = await response.json()
      message = problem.detail ?? problem.title ?? message
      errors = problem.errors
    } catch {
      // non-JSON error body: keep the generic message
    }
    throw new ApiError(response.status, message, errors)
  }
  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}
