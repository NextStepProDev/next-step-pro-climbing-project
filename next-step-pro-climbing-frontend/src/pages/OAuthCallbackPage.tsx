import { useEffect, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../context/AuthContext'
import { consumeRedirectPath } from '../utils/redirect'
import { LoadingSpinner } from '../components/ui/LoadingSpinner'

export function OAuthCallbackPage() {
  const { t } = useTranslation('auth')
  const { loginWithTokens } = useAuth()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [error, setError] = useState<string | null>(() => {
    const accessToken = searchParams.get('accessToken')
    const refreshToken = searchParams.get('refreshToken')
    const expiresIn = searchParams.get('expiresIn')
    if (!accessToken || !refreshToken || !expiresIn) {
      return t('oauth.callbackError')
    }
    return null
  })
  const processed = useRef(false)

  useEffect(() => {
    if (processed.current) return
    processed.current = true

    const accessToken = searchParams.get('accessToken')
    const refreshToken = searchParams.get('refreshToken')
    const expiresIn = searchParams.get('expiresIn')

    if (!accessToken || !refreshToken || !expiresIn) {
      setTimeout(() => navigate('/login', { replace: true }), 2000)
      return
    }

    window.history.replaceState({}, '', '/oauth-callback')

    loginWithTokens({
      accessToken,
      refreshToken,
      expiresIn: Number(expiresIn),
    })
      .then(() => {
        const redirect = consumeRedirectPath()
        navigate(redirect || '/', { replace: true })
      })
      .catch(() => {
        setError(t('oauth.callbackError'))
        setTimeout(() => navigate('/login', { replace: true }), 2000)
      })
  }, [searchParams, loginWithTokens, navigate, t])

  return (
    <div className="flex flex-col items-center justify-center min-h-[60vh] px-4">
      <div className="bg-dark-900 rounded-xl p-8 max-w-md w-full border border-dark-800 text-center">
        {error ? (
          <p className="text-rose-400/80">{error}</p>
        ) : (
          <>
            <LoadingSpinner />
            <p className="text-dark-300 mt-4">{t('oauth.callbackLoading')}</p>
          </>
        )}
      </div>
    </div>
  )
}
