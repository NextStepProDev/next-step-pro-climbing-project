import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Mail } from 'lucide-react'
import { authApi } from '../../api/client'
import { Button } from './Button'

interface Props {
  onDone: () => Promise<void>
}

export function NewsletterChoiceModal({ onDone }: Props) {
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
      <div className="relative bg-dark-900 border border-dark-700 rounded-xl p-6 max-w-sm w-full shadow-2xl">
        <div className="flex items-center justify-center w-12 h-12 bg-primary-500/15 rounded-full mx-auto mb-4">
          <Mail className="w-6 h-6 text-primary-400" />
        </div>

        <h2 className="text-lg font-semibold text-dark-100 text-center mb-2">
          {t('newsletter.modalTitle')}
        </h2>
        <p className="text-sm text-dark-400 text-center mb-6 leading-relaxed">
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
            className="w-full py-2 text-sm text-dark-400 hover:text-dark-200 transition-colors disabled:opacity-50"
          >
            {t('newsletter.modalNo')}
          </button>
        </div>
      </div>
    </div>
  )
}
