import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'pl', changeLanguage: () => {} } }),
  initReactI18next: { type: '3rdParty', init: () => {} },
}))

const getCurrentUser = vi.fn()
vi.mock('../api/client', () => ({
  authApi: {
    getCurrentUser: () => getCurrentUser(),
    logout: () => {},
  },
}))

import { AuthProvider, useAuth } from './AuthContext'
import { ApiError } from '../utils/errors'
import { hasTokens, saveTokens } from '../utils/tokenStorage'

function Probe() {
  const { user, isLoading } = useAuth()
  if (isLoading) return <span>loading</span>
  return <span>{user ? `user:${user.email}` : 'anonymous'}</span>
}

function renderProvider() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <Probe />
      </AuthProvider>
    </QueryClientProvider>,
  )
}

/**
 * Loading the session on mount is one `/user/me`, and it used to wipe the tokens whenever that
 * call failed for ANY reason. So a burst of clicks that tripped the 429 limiter, followed by a
 * refresh of the page, logged the user out of an account that was never in question.
 */
describe('AuthProvider session bootstrap', () => {
  beforeEach(() => {
    localStorage.clear()
    saveTokens({ accessToken: 'access-token', refreshToken: 'refresh-token', expiresIn: 900 })
    getCurrentUser.mockReset()
  })

  afterEach(() => {
    localStorage.clear()
  })

  it('shouldKeepTheTokensWhenTheProfileCallIsRateLimited', async () => {
    getCurrentUser.mockRejectedValue(new ApiError('Zbyt wiele żądań', 429, 'TOO_MANY_REQUESTS', 60))

    renderProvider()

    await waitFor(() => expect(screen.getByText('anonymous')).toBeInTheDocument())
    expect(hasTokens(), 'a reload once the minute passes must find the session intact').toBe(true)
  })

  it('shouldKeepTheTokensWhenTheProfileCallCannotReachTheServer', async () => {
    getCurrentUser.mockRejectedValue(new TypeError('Failed to fetch'))

    renderProvider()

    await waitFor(() => expect(screen.getByText('anonymous')).toBeInTheDocument())
    expect(hasTokens()).toBe(true)
  })

  it('shouldClearTheTokensWhenTheServerRefusesThem', async () => {
    getCurrentUser.mockRejectedValue(new ApiError('Unauthorized', 401))

    renderProvider()

    await waitFor(() => expect(screen.getByText('anonymous')).toBeInTheDocument())
    expect(hasTokens(), 'a refused token really is the end of the session').toBe(false)
  })

  it('shouldLoadTheUserWhenTheProfileCallSucceeds', async () => {
    getCurrentUser.mockResolvedValue({ email: 'coach@nextsteppro.pl', preferredLanguage: 'pl' })

    renderProvider()

    await waitFor(() => expect(screen.getByText('user:coach@nextsteppro.pl')).toBeInTheDocument())
    expect(hasTokens()).toBe(true)
  })
})
