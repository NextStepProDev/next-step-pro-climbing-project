import { describe, it, expect, afterEach, vi } from 'vitest'
import { verifyEmail, resendVerificationByToken } from './auth'

/**
 * One-time tokens from e-mailed links travel in the request body. As `?token=` on a POST they were
 * written into every proxy's access log on the way in; the password reset always used the body.
 */
describe('e-mailed link tokens', () => {
  afterEach(() => vi.restoreAllMocks())

  it.each([
    ['verifyEmail', verifyEmail, '/verify-email'],
    ['resendVerificationByToken', resendVerificationByToken, '/verify-email/resend'],
  ])('%s keeps the token out of the URL', async (_name, call, path) => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ message: 'ok' }), { status: 200, headers: { 'Content-Type': 'application/json' } }),
    )

    await call('secret-token-123')

    const [url, init] = fetchMock.mock.calls[0]
    expect(String(url)).toMatch(new RegExp(`${path}$`))
    expect(String(url)).not.toContain('secret-token-123')
    expect(JSON.parse(String(init?.body))).toEqual({ token: 'secret-token-123' })
  })
})
