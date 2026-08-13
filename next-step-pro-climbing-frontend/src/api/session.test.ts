import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { authApi } from './client'
import { ApiError } from '../utils/errors'
import { hasTokens, saveTokens } from '../utils/tokenStorage'

/**
 * A failed request may end a session only when the server actually refused the credentials.
 *
 * The report behind these tests: an admin clicking quickly around the panel tripped the 429
 * rate limiter and was then thrown out to the login screen. Both places that can end a session
 * — the `/user/me` bootstrap and the token refresh — cleared the tokens on ANY error, and a
 * bare `Error` carrying only a message gave them nothing to tell "token rejected" apart from
 * "too many requests" or "the network blinked".
 */

const TOKENS = { accessToken: 'access-token', refreshToken: 'refresh-token', expiresIn: 900 }

function respond(status: number, body: unknown = {}, headers: Record<string, string> = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...headers },
  })
}

describe('session survival', () => {
  beforeEach(() => {
    localStorage.clear()
    saveTokens(TOKENS)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    localStorage.clear()
  })

  it('shouldKeepTheSessionWhenAReadIsRateLimited', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      respond(429, { code: 'TOO_MANY_REQUESTS', message: 'Zbyt wiele żądań' }, { 'Retry-After': '60' }),
    ))

    const error = await authApi.getCurrentUser().catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(429)
    expect((error as ApiError).isRateLimited).toBe(true)
    expect((error as ApiError).retryAfterSeconds).toBe(60)
    expect(hasTokens(), 'a rate limit must not log anybody out').toBe(true)
  })

  it('shouldKeepTheSessionWhenTheRefreshItselfIsRateLimited', async () => {
    // The access token 401s, so the client refreshes — and the refresh hits the limiter too.
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(respond(401, {}))
      .mockResolvedValueOnce(respond(429, { message: 'Zbyt wiele żądań' }, { 'Retry-After': '60' }))
    vi.stubGlobal('fetch', fetchMock)

    const error = await authApi.getCurrentUser().catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(hasTokens(), 'a throttled refresh is not an expired session').toBe(true)
  })

  it('shouldKeepTheSessionWhenTheRefreshCannotReachTheServer', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(respond(401, {}))
      .mockRejectedValueOnce(new TypeError('Failed to fetch'))
    vi.stubGlobal('fetch', fetchMock)

    await authApi.getCurrentUser().catch(() => undefined)

    expect(hasTokens(), 'a network blip is not an expired session').toBe(true)
  })

  it('shouldEndTheSessionWhenTheServerRefusesTheRefreshToken', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(respond(401, {}))
      .mockResolvedValueOnce(respond(401, { message: 'Invalid refresh token' }))
    vi.stubGlobal('fetch', fetchMock)

    await authApi.getCurrentUser().catch(() => undefined)

    expect(hasTokens(), 'a refused refresh token really is the end of the session').toBe(false)
  })
})
