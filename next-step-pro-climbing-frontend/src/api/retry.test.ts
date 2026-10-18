import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { coursesApi, reservationApi } from './client'
import { saveTokens } from '../utils/tokenStorage'

/**
 * Which requests may be sent twice.
 *
 * `fetchApi` retried EVERY request on a network error (including its own 30 s timeout) and on any
 * 5xx — POSTs too. A 504, a timeout or a dropped connection says nothing about whether the server
 * already did the work, so a retried POST could book a seat twice, record a payment twice or send a
 * newsletter twice. Only a request that ends in the same state when repeated may be repeated.
 */

const TOKENS = { accessToken: 'access-token', refreshToken: 'refresh-token', expiresIn: 900 }

function respond(status: number, body: unknown = {}) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

describe('fetchApi retries', () => {
  beforeEach(() => {
    localStorage.clear()
    saveTokens(TOKENS)
    vi.useFakeTimers({ shouldAdvanceTime: true })
    vi.spyOn(console, 'warn').mockImplementation(() => {})
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
    localStorage.clear()
  })

  async function settle<T>(promise: Promise<T>) {
    const outcome = promise.catch((e: unknown) => e)
    await vi.runAllTimersAsync()
    return outcome
  }

  it('does not send a POST twice after a gateway timeout', async () => {
    const fetchMock = vi.fn().mockResolvedValue(respond(504))
    vi.stubGlobal('fetch', fetchMock)

    await settle(reservationApi.create('slot-1'))

    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('does not send a POST twice after a network error', async () => {
    const fetchMock = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'))
    vi.stubGlobal('fetch', fetchMock)

    await settle(reservationApi.create('slot-1'))

    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('does not send a POST twice after a 500', async () => {
    const fetchMock = vi.fn().mockResolvedValue(respond(500))
    vi.stubGlobal('fetch', fetchMock)

    await settle(reservationApi.create('slot-1'))

    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('still retries a read through a restart', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(respond(503))
      .mockResolvedValueOnce(respond(200, []))
    vi.stubGlobal('fetch', fetchMock)

    const result = await settle(coursesApi.getAll())

    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(result).toEqual([])
  })

  it('still retries a DELETE, which ends in the same state when repeated', async () => {
    const fetchMock = vi.fn()
      .mockRejectedValueOnce(new TypeError('Failed to fetch'))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    await settle(reservationApi.cancel('r-1'))

    expect(fetchMock).toHaveBeenCalledTimes(2)
  })
})
