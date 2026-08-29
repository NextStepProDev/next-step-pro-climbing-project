import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { CloudOff, RotateCw } from 'lucide-react'
import { useAuth } from '../../context/AuthContext'

/**
 * Shown by the route guards when we hold a session but could not find out who it belongs to.
 *
 * The point is what it does NOT do: it does not navigate. A redirect would drop the address the
 * person was on (`Navigate ... replace` erases it from history) and take any unsaved work on the
 * page with it — router navigation never reaches the `beforeunload` guard. A 429, a 500 or a
 * second offline is not a logout, so the page stays put and offers to try again.
 */
export function SessionUnavailable() {
  const { t } = useTranslation('common')
  const { retrySession } = useAuth()
  const [retrying, setRetrying] = useState(false)

  const retry = async () => {
    setRetrying(true)
    try {
      await retrySession()
    } finally {
      setRetrying(false)
    }
  }

  return (
    <div className="flex flex-col items-center justify-center min-h-[50vh] px-4 text-center">
      <CloudOff className="w-10 h-10 mb-4 text-surface-500" aria-hidden="true" />
      <h2 className="text-lg font-semibold text-surface-100 mb-2">{t('sessionUnavailable.title')}</h2>
      <p className="text-sm text-surface-300 max-w-md mb-6">{t('sessionUnavailable.description')}</p>
      <button
        type="button"
        onClick={retry}
        disabled={retrying}
        className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-primary-600 text-white font-medium hover:bg-primary-500 disabled:opacity-60 transition-colors"
      >
        <RotateCw className={`w-4 h-4 ${retrying ? 'animate-spin' : ''}`} aria-hidden="true" />
        {t('sessionUnavailable.retry')}
      </button>
    </div>
  )
}
