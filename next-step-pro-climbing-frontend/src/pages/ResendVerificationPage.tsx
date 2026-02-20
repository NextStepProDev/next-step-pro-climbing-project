import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Mail } from 'lucide-react'
import { resendVerification } from '../api/auth'
import { Button } from '../components/ui/Button'

export function ResendVerificationPage() {
  const { t } = useTranslation('auth')
  const [email, setEmail] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState(false)
  const [loading, setLoading] = useState(false)

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    setLoading(true)

    try {
      await resendVerification(email)
      setSuccess(true)
    } catch (err) {
      setError(err instanceof Error ? err.message : t('resendVerification.error'))
    } finally {
      setLoading(false)
    }
  }

  if (success) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[60vh] px-4">
        <div className="bg-dark-900 rounded-xl p-8 max-w-md w-full border border-dark-800 text-center">
          <div className="w-12 h-12 bg-green-500/20 rounded-full flex items-center justify-center mx-auto mb-4">
            <svg className="w-6 h-6 text-green-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
            </svg>
          </div>
          <h2 className="text-xl font-bold text-dark-100 mb-2">{t('resendVerification.successTitle')}</h2>
          <p className="text-dark-400 mb-6">
            {t('resendVerification.successMessage')}
          </p>
          <Link to="/login" className="text-primary-400 hover:text-primary-300 font-medium">
            {t('resendVerification.backToLogin')}
          </Link>
        </div>
      </div>
    )
  }

  return (
    <div className="flex flex-col items-center justify-center min-h-[60vh] px-4">
      <div className="bg-dark-900 rounded-xl p-8 max-w-md w-full border border-dark-800">
        <div className="text-center mb-6">
          <Mail className="w-10 h-10 text-primary-500 mx-auto mb-3" />
          <h1 className="text-2xl font-bold text-dark-100">{t('resendVerification.title')}</h1>
          <p className="text-dark-400 mt-2 text-sm">
            {t('resendVerification.description')}
          </p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label htmlFor="email" className="block text-sm font-medium text-dark-300 mb-1">
              {t('resendVerification.email')}
            </label>
            <input
              id="email"
              type="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="w-full px-3 py-2 bg-dark-800 border border-dark-700 rounded-lg text-dark-100 placeholder-dark-500 focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent"
              placeholder={t('resendVerification.emailPlaceholder')}
            />
          </div>

          {error && (
            <p className="text-sm text-rose-400/80">{error}</p>
          )}

          <Button type="submit" variant="primary" className="w-full" loading={loading}>
            {t('resendVerification.submit')}
          </Button>
        </form>

        <p className="mt-6 text-center text-sm text-dark-400">
          <Link to="/login" className="text-primary-400 hover:text-primary-300">
            {t('resendVerification.backToLogin')}
          </Link>
        </p>
      </div>
    </div>
  )
}
