const ACCESS_TOKEN_KEY = 'nsp_access_token'
const REFRESH_TOKEN_KEY = 'nsp_refresh_token'
const EXPIRES_AT_KEY = 'nsp_expires_at'

export interface AuthTokens {
  accessToken: string
  refreshToken: string
  expiresIn: number
}

export function saveTokens(tokens: AuthTokens): void {
  localStorage.setItem(ACCESS_TOKEN_KEY, tokens.accessToken)
  localStorage.setItem(REFRESH_TOKEN_KEY, tokens.refreshToken)
  const expiresAt = Date.now() + tokens.expiresIn * 1000
  localStorage.setItem(EXPIRES_AT_KEY, expiresAt.toString())
}

export function getAccessToken(): string | null {
  return localStorage.getItem(ACCESS_TOKEN_KEY)
}

export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_TOKEN_KEY)
}

export function isAccessTokenExpired(): boolean {
  const expiresAt = localStorage.getItem(EXPIRES_AT_KEY)
  if (!expiresAt) return true
  // 30 second buffer before actual expiry
  return Date.now() >= Number(expiresAt) - 30_000
}

export function clearTokens(): void {
  localStorage.removeItem(ACCESS_TOKEN_KEY)
  localStorage.removeItem(REFRESH_TOKEN_KEY)
  localStorage.removeItem(EXPIRES_AT_KEY)
}

export function hasTokens(): boolean {
  return getAccessToken() !== null && getRefreshToken() !== null
}
