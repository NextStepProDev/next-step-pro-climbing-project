import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { verifyEmail, resendVerificationByToken } from '../api/auth'
import { getErrorMessage } from '../utils/errors'
import { Button } from '../components/ui/Button'

/**
 * The failure state is the point of this page. A confirmation link that no longer works used to
 * offer nothing but "go to sign in", so the one way forward — asking for a new link — was a link at
 * the bottom of another page that nobody found. The dead token is right there in the URL, so it can
 * be traded for a live one without the reader typing or finding anything.
 */
export function VerifyEmailPage() {
  const { t } = useTranslation('auth')
  const [searchParams] = useSearchParams()
  const [status, setStatus] = useState<'loading' | 'success' | 'error'>('loading')
  const [message, setMessage] = useState('')
  const [renewal, setRenewal] = useState<'idle' | 'sending' | 'sent' | 'failed'>('idle')
  const [renewalMessage, setRenewalMessage] = useState('')

  const token = searchParams.get('token')

  useEffect(() => {
    if (!token) return

    verifyEmail(token)
      .then((res) => {
        setStatus('success')
        setMessage(res.message)
      })
      .catch((err) => {
        setStatus('error')
        setMessage(getErrorMessage(err))
      })
  }, [token, t])

  const handleRenew = async () => {
    if (!token) return
    setRenewal('sending')
    try {
      const res = await resendVerificationByToken(token)
      setRenewalMessage(res.message)
      setRenewal('sent')
    } catch (err) {
      setRenewalMessage(getErrorMessage(err))
      setRenewal('failed')
    }
  }

  if (!token) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[60vh] px-4">
        <div className="bg-surface-900 rounded-xl p-8 max-w-md w-full border border-surface-800 text-center">
          <div className="w-12 h-12 bg-rose-500/10 rounded-full flex items-center justify-center mx-auto mb-4">
            <svg className="w-6 h-6 text-rose-400/80" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </div>
          <h2 className="text-xl font-bold text-surface-100 mb-2">{t('verify.errorTitle')}</h2>
          <p className="text-surface-400 mb-6">{t('verify.noToken')}</p>
          <Link to="/login" className="text-primary-400 hover:text-primary-300 font-medium">
            {t('verify.goToLogin')}
          </Link>
        </div>
      </div>
    )
  }

  return (
    <div className="flex flex-col items-center justify-center min-h-[60vh] px-4">
      <div className="bg-surface-900 rounded-xl p-8 max-w-md w-full border border-surface-800 text-center">
        {status === 'loading' && (
          <p className="text-surface-300">{t('verify.loading')}</p>
        )}

        {status === 'success' && (
          <>
            <div className="w-12 h-12 bg-green-500/20 rounded-full flex items-center justify-center mx-auto mb-4">
              <svg className="w-6 h-6 text-green-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
              </svg>
            </div>
            <h2 className="text-xl font-bold text-surface-100 mb-2">{t('verify.successTitle')}</h2>
            <p className="text-surface-400 mb-6">{message}</p>
            <Link
              to="/login"
              className="text-primary-400 hover:text-primary-300 font-medium"
            >
              {t('verify.goToLogin')}
            </Link>
          </>
        )}

        {status === 'error' && renewal === 'sent' && (
          <>
            <div className="w-12 h-12 bg-green-500/20 rounded-full flex items-center justify-center mx-auto mb-4">
              <svg className="w-6 h-6 text-green-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
              </svg>
            </div>
            <h2 className="text-xl font-bold text-surface-100 mb-2">{t('verify.renewedTitle')}</h2>
            <p className="text-surface-400 mb-6">{renewalMessage}</p>
            <Link to="/login" className="text-primary-400 hover:text-primary-300 font-medium">
              {t('verify.goToLogin')}
            </Link>
          </>
        )}

        {status === 'error' && renewal !== 'sent' && (
          <>
            <div className="w-12 h-12 bg-rose-500/10 rounded-full flex items-center justify-center mx-auto mb-4">
              <svg className="w-6 h-6 text-rose-400/80" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </div>
            <h2 className="text-xl font-bold text-surface-100 mb-2">{t('verify.errorTitle')}</h2>
            <p className="text-surface-400 mb-2">{message}</p>

            {renewal === 'failed' ? (
              <>
                <p className="text-sm text-rose-400/80 mb-6">{renewalMessage}</p>
                <div className="space-y-2 text-sm">
                  <p>
                    <Link to="/register" className="text-primary-400 hover:text-primary-300 font-medium">
                      {t('verify.registerAgain')}
                    </Link>
                  </p>
                  <p>
                    <Link to="/resend-verification" className="text-surface-400 hover:text-primary-400">
                      {t('verify.resendByEmail')}
                    </Link>
                  </p>
                </div>
              </>
            ) : (
              <>
                <p className="text-sm text-surface-500 mb-6">{t('verify.renewHint')}</p>
                <Button
                  type="button"
                  variant="primary"
                  className="w-full"
                  loading={renewal === 'sending'}
                  onClick={handleRenew}
                >
                  {t('verify.renewButton')}
                </Button>
              </>
            )}

            <p className="mt-6 text-sm">
              <Link to="/login" className="text-surface-400 hover:text-primary-400">
                {t('verify.goToLogin')}
              </Link>
            </p>
          </>
        )}
      </div>
    </div>
  )
}
