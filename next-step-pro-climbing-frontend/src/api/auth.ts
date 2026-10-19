import i18n from '../i18n'
import { ApiError, parseRetryAfter } from '../utils/errors'

const API_BASE = '/api/auth'

export interface RegisterRequest {
  email: string
  password: string
  firstName: string
  lastName: string
  phone: string
  newsletterSubscribed?: boolean
  preferredLanguage?: string
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
    // ApiError carries the status out of here because /refresh runs through this function, and
    // doRefresh may only end the session when the server actually rejected the token.
    const fail = (message: string) => new ApiError(
      message,
      response.status,
      error?.code,
      parseRetryAfter(response.headers.get('Retry-After')),
    )
    const serverMessage = error?.message
    if (serverMessage) {
      throw fail(serverMessage)
    }
    if (response.status === 429) {
      throw fail(i18n.t('rateLimited', { ns: 'errors' }))
    }
    if (response.status >= 500) {
      throw fail(i18n.t('authServer', { ns: 'errors' }))
    }
    throw fail(i18n.t('authGeneric', { status: response.status, ns: 'errors' }))
  }

  // /logout answers 204 with no body; everything else here returns JSON.
  if (response.status === 204) return undefined as T
  return response.json()
}

export function registerUser(data: RegisterRequest): Promise<MessageResponse> {
  return authFetch('/register', data)
}

export function loginUser(data: LoginRequest): Promise<AuthTokensResponse> {
  return authFetch('/login', data)
}

export function verifyEmail(token: string): Promise<MessageResponse> {
  // In the body, never the URL: a query string lands in every proxy's access log.
  return authFetch('/verify-email', { token })
}

export function resendVerification(email: string): Promise<MessageResponse> {
  return authFetch('/resend-verification', { email })
}

/**
 * Trades the token from a link that no longer works for a fresh one. The token is already in the
 * URL of the page showing the failure, so the way out of a dead link is a single click — nothing
 * to type, nothing to go looking for.
 */
export function resendVerificationByToken(token: string): Promise<MessageResponse> {
  return authFetch('/verify-email/resend', { token })
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

/**
 * Ends this session on the server. Before, logging out only forgot the tokens in this browser, so a
 * copied refresh token stayed valid for its remaining seven days.
 */
export function revokeSession(refreshToken: string): Promise<void> {
  return authFetch('/logout', { refreshToken })
}

/**
 * Swaps the one-time code from the Google sign-in redirect for tokens. The tokens used to arrive in
 * the redirect's query string — i.e. in server logs and browser history.
 */
export function exchangeOAuthCode(code: string): Promise<AuthTokensResponse> {
  return authFetch('/oauth/exchange', { code })
}
