import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { consumeRedirectPath } from '../utils/redirect'
import { Button } from '../components/ui/Button'
import logoWhite from '../assets/logo/logo-white.png'

export function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    setLoading(true)

    try {
      await login(email, password)
      const redirect = consumeRedirectPath()
      navigate(redirect || '/', { replace: true })
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Wystąpił błąd logowania')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex flex-col items-center justify-center min-h-[60vh] px-4">
      <div className="bg-dark-900 rounded-xl p-8 max-w-md w-full border border-dark-800">
        <div className="text-center mb-6">
          <img src={logoWhite} alt="Next Step Pro Climbing" className="h-16 mx-auto mb-4" />
          <h1 className="text-2xl font-bold text-dark-100">Zaloguj się</h1>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label htmlFor="email" className="block text-sm font-medium text-dark-300 mb-1">
              Email
            </label>
            <input
              id="email"
              type="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="w-full px-3 py-2 bg-dark-800 border border-dark-700 rounded-lg text-dark-100 placeholder-dark-500 focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent"
              placeholder="jan@example.com"
            />
          </div>

          <div>
            <label htmlFor="password" className="block text-sm font-medium text-dark-300 mb-1">
              Hasło
            </label>
            <input
              id="password"
              type="password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="w-full px-3 py-2 bg-dark-800 border border-dark-700 rounded-lg text-dark-100 placeholder-dark-500 focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent"
              placeholder="Twoje hasło"
            />
          </div>

          {error && (
            <p className="text-sm text-rose-400/80">{error}</p>
          )}

          <Button type="submit" variant="primary" className="w-full" loading={loading}>
            Zaloguj się
          </Button>
        </form>

        <div className="mt-6 space-y-2 text-center text-sm">
          <p className="text-dark-400">
            Nie masz konta?{' '}
            <Link to="/register" className="text-primary-400 hover:text-primary-300">
              Zarejestruj się
            </Link>
          </p>
          <p className="text-dark-400">
            <Link to="/forgot-password" className="text-primary-400 hover:text-primary-300">
              Zapomniałeś hasła?
            </Link>
          </p>
          <p className="text-dark-400">
            <Link to="/resend-verification" className="text-primary-400 hover:text-primary-300">
              Wyślij ponownie email weryfikacyjny
            </Link>
          </p>
        </div>
      </div>
    </div>
  )
}
