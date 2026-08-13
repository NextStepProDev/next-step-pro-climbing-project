import i18n from '../i18n'

/**
 * An API failure that still knows what the server said.
 *
 * Everything used to throw a bare `Error` carrying only a message, which meant the two places
 * that decide whether a session is over — `AuthContext` on a failed `/user/me` and `doRefresh`
 * on a failed refresh — could not tell "the server rejected this token" from "I was rate
 * limited" or "the network blinked". They cleared the tokens either way, so a burst of clicks
 * that tripped the 429 limiter logged the user out of an account that was perfectly valid.
 *
 * Keep `message` populated: `getErrorMessage` and every catch block in the UI still read it.
 */
export class ApiError extends Error {
  readonly status: number
  readonly code?: string
  /** Seconds from the server's Retry-After, when it sent one (the limiter always does). */
  readonly retryAfterSeconds?: number

  constructor(message: string, status: number, code?: string, retryAfterSeconds?: number) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.retryAfterSeconds = retryAfterSeconds
  }

  /** Rate limited. Always transient: the window is one minute wide and fixed. */
  get isRateLimited(): boolean {
    return this.status === 429
  }

  /**
   * The server actively refused these credentials. The ONLY shape that may end a session —
   * 429, 5xx and network errors are the caller's problem to retry, not proof of a dead login.
   */
  get isAuthRejection(): boolean {
    return this.status === 401 || this.status === 403
  }
}

/** Retry-After is seconds here (the limiter sends "60"); ignore an HTTP-date form. */
export function parseRetryAfter(header: string | null): number | undefined {
  if (!header) return undefined
  const seconds = Number(header)
  return Number.isFinite(seconds) && seconds >= 0 ? seconds : undefined
}

export function getErrorMessage(error: unknown): string {
  if (error instanceof TypeError && error.message === 'Failed to fetch') {
    return i18n.t('network', { ns: 'errors' })
  }
  if (error instanceof Error) return error.message
  if (typeof error === 'string') return error
  return i18n.t('unexpected', { ns: 'errors' })
}
