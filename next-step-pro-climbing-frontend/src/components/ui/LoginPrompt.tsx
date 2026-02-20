import { LogIn } from 'lucide-react'
import { Link, useNavigate, useLocation } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { saveRedirectPath } from '../../utils/redirect'
import { Button } from './Button'

interface LoginPromptProps {
  message?: string
}

export function LoginPrompt({ message }: LoginPromptProps) {
  const { t } = useTranslation('auth')
  const navigate = useNavigate()
  const location = useLocation()

  const handleLogin = () => {
    saveRedirectPath(location.pathname + location.search)
    navigate('/login')
  }

  return (
    <div className="flex flex-col items-center justify-center min-h-[50vh] px-4">
      <div className="bg-dark-900 rounded-xl p-8 max-w-md w-full text-center border border-dark-800">
        <LogIn className="w-12 h-12 text-primary-500 mx-auto mb-4" />
        <h2 className="text-xl font-semibold text-dark-100 mb-2">
          {t('loginPrompt.title')}
        </h2>
        <p className="text-dark-400 mb-6">{message ?? t('loginPrompt.defaultMessage')}</p>
        <Button
          variant="primary"
          className="w-full"
          onClick={handleLogin}
        >
          {t('loginPrompt.loginButton')}
        </Button>
        <p className="mt-4 text-sm text-dark-400">
          {t('loginPrompt.noAccount')}{' '}
          <Link to="/register" className="text-primary-400 hover:text-primary-300">
            {t('loginPrompt.register')}
          </Link>
        </p>
      </div>
    </div>
  )
}
