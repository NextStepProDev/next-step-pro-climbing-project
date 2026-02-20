import i18n from '../i18n'

const API_BASE = '/api/auth'

export interface RegisterRequest {
  email: string
  password: string
  firstName: string
  lastName: string
  phone: string
}

export interface LoginRequest {
  email: string
  password: string
}

export interface AuthTokensResponse {
  accessToken: string
  refreshToken: string
  expiresIn: number
}

export interface MessageResponse {
  message: string
}

async function authFetch<T>(endpoint: string, body: unknown): Promise<T> {
  let response: Response
  try {
    response = await fetch(`${API_BASE}${endpoint}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept-Language': i18n.language,
      },
      body: JSON.stringify(body),
    })
  } catch {
    throw new Error(i18n.t('network', { ns: 'errors' }))
  }

  if (!response.ok) {
    const error = await response.json().catch(() => null)
    const serverMessage = error?.message
    if (serverMessage) {
      throw new Error(serverMessage)
    }
    if (response.status >= 500) {
      throw new Error(i18n.t('authServer', { ns: 'errors' }))
    }
    throw new Error(i18n.t('authGeneric', { status: response.status, ns: 'errors' }))
  }

  return response.json()
}

export function registerUser(data: RegisterRequest): Promise<MessageResponse> {
  return authFetch('/register', data)
}

export function loginUser(data: LoginRequest): Promise<AuthTokensResponse> {
  return authFetch('/login', data)
}

export function verifyEmail(token: string): Promise<MessageResponse> {
  return authFetch(`/verify-email?token=${encodeURIComponent(token)}`, {})
}

export function resendVerification(email: string): Promise<MessageResponse> {
  return authFetch('/resend-verification', { email })
}

export function forgotPassword(email: string): Promise<MessageResponse> {
  return authFetch('/forgot-password', { email })
}

export function resetPassword(token: string, newPassword: string): Promise<MessageResponse> {
  return authFetch('/reset-password', { token, newPassword })
}

export function refreshTokens(refreshToken: string): Promise<AuthTokensResponse> {
  return authFetch('/refresh', { refreshToken })
}
