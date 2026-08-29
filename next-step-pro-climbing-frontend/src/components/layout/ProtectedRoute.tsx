import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'
import { saveRedirectPath } from '../../utils/redirect'
import { LoadingSpinner } from '../ui/LoadingSpinner'
import { SessionUnavailable } from './SessionUnavailable'

interface ProtectedRouteProps {
  children: ReactNode
}

export function ProtectedRoute({ children }: ProtectedRouteProps) {
  const { isAuthenticated, isLoading, sessionUnknown } = useAuth()
  const location = useLocation()

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-[50vh]">
        <LoadingSpinner />
      </div>
    )
  }

  // Same rule as AdminRoute: a transient failure must not read as a logout, or the person is
  // sent to /login holding a session that was valid the whole time.
  if (sessionUnknown) {
    return <SessionUnavailable />
  }

  if (!isAuthenticated) {
    saveRedirectPath(location.pathname + location.search)
    return <Navigate to="/login" replace />
  }

  return <>{children}</>
}
