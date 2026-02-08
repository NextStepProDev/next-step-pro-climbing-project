import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { verifyEmail } from '../api/auth'

export function VerifyEmailPage() {
  const [searchParams] = useSearchParams()
  const [status, setStatus] = useState<'loading' | 'success' | 'error'>('loading')
  const [message, setMessage] = useState('')

  const token = searchParams.get('token')

  useEffect(() => {
    if (!token) return

    verifyEmail(token)
      .then((res) => {
        setStatus('success')
        setMessage(res.message)
      })
      .catch((err) => {
        setStatus('error')
        setMessage(err instanceof Error ? err.message : 'Weryfikacja nie powiodła się')
      })
  }, [token])

  if (!token) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[60vh] px-4">
        <div className="bg-dark-900 rounded-xl p-8 max-w-md w-full border border-dark-800 text-center">
          <div className="w-12 h-12 bg-rose-500/10 rounded-full flex items-center justify-center mx-auto mb-4">
            <svg className="w-6 h-6 text-rose-400/80" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </div>
          <h2 className="text-xl font-bold text-dark-100 mb-2">Błąd weryfikacji</h2>
          <p className="text-dark-400 mb-6">Brak tokenu weryfikacyjnego w URL</p>
          <Link to="/login" className="text-primary-400 hover:text-primary-300 font-medium">
            Przejdź do logowania
          </Link>
        </div>
      </div>
    )
  }

  return (
    <div className="flex flex-col items-center justify-center min-h-[60vh] px-4">
      <div className="bg-dark-900 rounded-xl p-8 max-w-md w-full border border-dark-800 text-center">
        {status === 'loading' && (
          <p className="text-dark-300">Weryfikacja email...</p>
        )}

        {status === 'success' && (
          <>
            <div className="w-12 h-12 bg-green-500/20 rounded-full flex items-center justify-center mx-auto mb-4">
              <svg className="w-6 h-6 text-green-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
              </svg>
            </div>
            <h2 className="text-xl font-bold text-dark-100 mb-2">Email zweryfikowany</h2>
            <p className="text-dark-400 mb-6">{message}</p>
            <Link
              to="/login"
              className="text-primary-400 hover:text-primary-300 font-medium"
            >
              Przejdź do logowania
            </Link>
          </>
        )}

        {status === 'error' && (
          <>
            <div className="w-12 h-12 bg-rose-500/10 rounded-full flex items-center justify-center mx-auto mb-4">
              <svg className="w-6 h-6 text-rose-400/80" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </div>
            <h2 className="text-xl font-bold text-dark-100 mb-2">Błąd weryfikacji</h2>
            <p className="text-dark-400 mb-6">{message}</p>
            <Link
              to="/login"
              className="text-primary-400 hover:text-primary-300 font-medium"
            >
              Przejdź do logowania
            </Link>
          </>
        )}
      </div>
    </div>
  )
}
