import { useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { KeyRound } from 'lucide-react'
import { resetPassword } from '../api/auth'
import { validatePassword } from '../utils/validation'
import { Button } from '../components/ui/Button'

export function ResetPasswordPage() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState(false)
  const [loading, setLoading] = useState(false)

  if (!token) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[60vh] px-4">
        <div className="bg-dark-900 rounded-xl p-8 max-w-md w-full border border-dark-800 text-center">
          <h2 className="text-xl font-bold text-dark-100 mb-2">Nieprawidłowy link</h2>
          <p className="text-dark-400 mb-6">Brak tokenu resetowania hasła w URL.</p>
          <Link to="/forgot-password" className="text-primary-400 hover:text-primary-300 font-medium">
            Poproś o nowy link
          </Link>
        </div>
      </div>
    )
  }

  const validate = (): string | null => {
    return validatePassword(password, confirmPassword)
  }

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)

    const validationError = validate()
    if (validationError) {
      setError(validationError)
      return
    }

    setLoading(true)
    try {
      await resetPassword(token, password)
      setSuccess(true)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Wystąpił błąd')
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
          <h2 className="text-xl font-bold text-dark-100 mb-2">Hasło zmienione</h2>
          <p className="text-dark-400 mb-6">Twoje hasło zostało pomyślnie zmienione.</p>
          <Link to="/login" className="text-primary-400 hover:text-primary-300 font-medium">
            Zaloguj się
          </Link>
        </div>
      </div>
    )
  }

  return (
    <div className="flex flex-col items-center justify-center min-h-[60vh] px-4">
      <div className="bg-dark-900 rounded-xl p-8 max-w-md w-full border border-dark-800">
        <div className="text-center mb-6">
          <KeyRound className="w-10 h-10 text-primary-500 mx-auto mb-3" />
          <h1 className="text-2xl font-bold text-dark-100">Nowe hasło</h1>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label htmlFor="password" className="block text-sm font-medium text-dark-300 mb-1">
              Nowe hasło
            </label>
            <input
              id="password"
              type="password"
              required
              minLength={8}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="w-full px-3 py-2 bg-dark-800 border border-dark-700 rounded-lg text-dark-100 placeholder-dark-500 focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent"
            />
            <p className="text-xs text-dark-500 mt-1">
              Min. 4 znaki
            </p>
          </div>

          <div>
            <label htmlFor="confirmPassword" className="block text-sm font-medium text-dark-300 mb-1">
              Potwierdź hasło
            </label>
            <input
              id="confirmPassword"
              type="password"
              required
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              className="w-full px-3 py-2 bg-dark-800 border border-dark-700 rounded-lg text-dark-100 placeholder-dark-500 focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent"
            />
          </div>

          {error && (
            <p className="text-sm text-rose-400/80">{error}</p>
          )}

          <Button type="submit" variant="primary" className="w-full" loading={loading}>
            Ustaw nowe hasło
          </Button>
        </form>
      </div>
    </div>
  )
}
