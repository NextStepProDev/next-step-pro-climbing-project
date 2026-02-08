import { LogIn } from 'lucide-react'
import { Link, useNavigate, useLocation } from 'react-router-dom'
import { saveRedirectPath } from '../../context/AuthContext'
import { Button } from './Button'

interface LoginPromptProps {
  message?: string
}

export function LoginPrompt({ message = 'Zaloguj się, aby kontynuować' }: LoginPromptProps) {
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
          Wymagane logowanie
        </h2>
        <p className="text-dark-400 mb-6">{message}</p>
        <Button
          variant="primary"
          className="w-full"
          onClick={handleLogin}
        >
          Zaloguj się
        </Button>
        <p className="mt-4 text-sm text-dark-400">
          Nie masz konta?{' '}
          <Link to="/register" className="text-primary-400 hover:text-primary-300">
            Zarejestruj się
          </Link>
        </p>
      </div>
    </div>
  )
}
