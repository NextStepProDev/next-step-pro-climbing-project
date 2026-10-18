import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../context/AuthContext'
import { consumeRedirectPath } from '../utils/redirect'
import { exchangeOAuthCode } from '../api/auth'
import { LoadingSpinner } from '../components/ui/LoadingSpinner'

/**
 * The one-time code Google sign-in hands back, read from the URL FRAGMENT. A fragment is never sent
 * to any server, which is the point: the tokens used to arrive in the query string, i.e. in nginx
 * logs and browser history.
 */
function readCode(): string | null {
  return new URLSearchParams(window.location.hash.slice(1)).get('code')
}

export function OAuthCallbackPage() {
  const { t } = useTranslation('auth')
  const { loginWithTokens } = useAuth()
  const navigate = useNavigate()
  const [error, setError] = useState<string | null>(() => (readCode() ? null : t('oauth.callbackError')))
  const processed = useRef(false)

  useEffect(() => {
    if (processed.current) return
    processed.current = true
    let redirectTimer: ReturnType<typeof setTimeout> | undefined

    const code = readCode()
    // Gone from the address bar and from history before anything else runs; it works once anyway.
    window.history.replaceState({}, '', '/oauth-callback')

    if (!code) {
      redirectTimer = setTimeout(() => navigate('/login', { replace: true }), 2000)
      return
    }

    exchangeOAuthCode(code)
      .then((tokens) => loginWithTokens(tokens))
      .then(() => {
        const redirect = consumeRedirectPath()
        navigate(redirect || '/', { replace: true })
      })
      .catch(() => {
        setError(t('oauth.callbackError'))
        redirectTimer = setTimeout(() => navigate('/login', { replace: true }), 2000)
      })

    // Both branches park a delayed navigate so the user can read the error first. Left uncleared,
    // a timer that outlives this page yanks whoever is already somewhere else back to /login.
    return () => clearTimeout(redirectTimer)
  }, [loginWithTokens, navigate, t])

  return (
    <div className="flex flex-col items-center justify-center min-h-[60vh] px-4">
      <div className="bg-surface-900 rounded-xl p-8 max-w-md w-full border border-surface-800 text-center">
        {error ? (
          <p className="text-rose-400/80">{error}</p>
        ) : (
          <>
            <LoadingSpinner />
            <p className="text-surface-300 mt-4">{t('oauth.callbackLoading')}</p>
          </>
        )}
      </div>
    </div>
  )
}
