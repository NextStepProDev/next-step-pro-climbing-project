import { RefreshCw } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Button } from './Button'
import { getErrorMessage } from '../../utils/errors'

interface QueryErrorProps {
  error: unknown
  onRetry?: () => void
}

export function QueryError({ error, onRetry }: QueryErrorProps) {
  const { t } = useTranslation('errors')

  return (
    <div className="bg-dark-900 rounded-xl border border-dark-800 p-8 text-center">
      <h2 className="text-lg font-semibold text-dark-100 mb-2">
        {t('queryError.title')}
      </h2>
      <p className="text-dark-400 mb-4">
        {getErrorMessage(error)}
      </p>
      {onRetry && (
        <Button variant="ghost" onClick={onRetry}>
          <RefreshCw className="w-4 h-4 mr-2" />
          {t('queryError.retry')}
        </Button>
      )}
    </div>
  )
}
