import { RefreshCw } from 'lucide-react'
import { Button } from './Button'
import { getErrorMessage } from '../../utils/errors'

interface QueryErrorProps {
  error: unknown
  onRetry?: () => void
}

export function QueryError({ error, onRetry }: QueryErrorProps) {
  return (
    <div className="bg-dark-900 rounded-xl border border-dark-800 p-8 text-center">
      <h2 className="text-lg font-semibold text-dark-100 mb-2">
        Nie udało się załadować danych
      </h2>
      <p className="text-dark-400 mb-4">
        {getErrorMessage(error)}
      </p>
      {onRetry && (
        <Button variant="ghost" onClick={onRetry}>
          <RefreshCw className="w-4 h-4 mr-2" />
          Spróbuj ponownie
        </Button>
      )}
    </div>
  )
}
