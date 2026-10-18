import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { OAuthCallbackPage } from './OAuthCallbackPage'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'pl' } }),
  initReactI18next: { type: '3rdParty', init: () => {} },
}))

const loginWithTokens = vi.fn()
vi.mock('../context/AuthContext', () => ({ useAuth: () => ({ loginWithTokens }) }))

const exchangeOAuthCode = vi.fn()
vi.mock('../api/auth', () => ({ exchangeOAuthCode: (...a: unknown[]) => exchangeOAuthCode(...a) }))

const tokens = { accessToken: 'a', refreshToken: 'r', expiresIn: 900 }

function renderAt(url: string) {
  window.history.replaceState({}, '', url)
  return render(
    <MemoryRouter initialEntries={[url]}>
      <OAuthCallbackPage />
    </MemoryRouter>,
  )
}

describe('OAuthCallbackPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    exchangeOAuthCode.mockResolvedValue(tokens)
    loginWithTokens.mockResolvedValue(undefined)
  })

  it('swaps the code from the fragment for tokens', async () => {
    renderAt('/oauth-callback#code=jednorazowy')

    await waitFor(() => expect(exchangeOAuthCode).toHaveBeenCalledWith('jednorazowy'))
    await waitFor(() => expect(loginWithTokens).toHaveBeenCalledWith(tokens))
  })

  it('wipes the code from the address bar straight away', async () => {
    renderAt('/oauth-callback#code=jednorazowy')

    await waitFor(() => expect(exchangeOAuthCode).toHaveBeenCalled())
    expect(window.location.hash).toBe('')
  })

  it('no longer accepts tokens in the query string', async () => {
    // The old shape: a seven-day refresh token in a URL that servers log.
    renderAt('/oauth-callback?accessToken=a&refreshToken=r&expiresIn=900')

    await new Promise((r) => setTimeout(r, 0))
    expect(exchangeOAuthCode).not.toHaveBeenCalled()
    expect(loginWithTokens).not.toHaveBeenCalled()
  })
})
