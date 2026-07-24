const DEVICE_KEY = 'predictor.device'

/** Opaque token proving this device already passed a code check. */
export function deviceToken(): string | null {
  return localStorage.getItem(DEVICE_KEY)
}

export function rememberDeviceToken(token: string | null) {
  if (token) {
    localStorage.setItem(DEVICE_KEY, token)
  }
}
