import { useState } from 'react'
import { Trans, useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { useFocusTrap } from '../../utils/useFocusTrap'
import { Mail } from 'lucide-react'
import { authApi } from '../../api/client'
import { Button } from './Button'

interface Props {
  onDone: () => Promise<void>
}

export function NewsletterChoiceModal({ onDone }: Props) {
  const trapRef = useFocusTrap(true)
  const { t } = useTranslation('common')
  const [loading, setLoading] = useState(false)

  const handleChoice = async (subscribed: boolean) => {
    setLoading(true)
    try {
      await authApi.updateNewsletter(subscribed)
      await onDone()
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" />
      <div ref={trapRef} role="dialog" aria-modal="true" className="relative bg-surface-900 border border-surface-700 rounded-xl p-6 max-w-sm w-full shadow-2xl">
        <div className="flex items-center justify-center w-12 h-12 bg-primary-500/15 rounded-full mx-auto mb-4">
          <Mail className="w-6 h-6 text-primary-400" />
        </div>

        <h2 className="text-lg font-semibold text-surface-100 text-center mb-2">
          {t('newsletter.modalTitle')}
        </h2>
        <p className="text-sm text-surface-400 text-center mb-6 leading-relaxed">
          {t('newsletter.modalDescription')}
        </p>

        <div className="flex flex-col gap-3">
          <Button
            variant="primary"
            className="w-full"
            loading={loading}
            onClick={() => handleChoice(true)}
          >
            {t('newsletter.modalYes')}
          </Button>
          <button
            onClick={() => handleChoice(false)}
            disabled={loading}
            className="w-full py-2 text-sm text-surface-400 hover:text-surface-200 transition-colors disabled:opacity-50"
          >
            {t('newsletter.modalNo')}
          </button>
        </div>

        {/* GDPR information duty (art. 13): the privacy policy is shown at account creation
            time — it is information, not consent, hence no checkbox. The newsletter above
            is a separate, voluntary consent (opt-in). */}
        <p className="mt-5 pt-4 border-t border-surface-800 text-xs text-surface-500 text-center leading-relaxed">
          <Trans
            i18nKey="newsletter.privacyNotice"
            ns="common"
            components={{
              1: <Link to="/polityka-prywatnosci" target="_blank" className="text-surface-400 hover:text-primary-400 underline transition-colors" />,
            }}
          />
        </p>
      </div>
    </div>
  )
}
