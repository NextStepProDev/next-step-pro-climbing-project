import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'
import { LoadingSpinner } from '../ui/LoadingSpinner'
import { SessionUnavailable } from './SessionUnavailable'

interface AdminRouteProps {
  children: ReactNode
}

export function AdminRoute({ children }: AdminRouteProps) {
  const { isAdmin, isAuthenticated, isLoading, sessionUnknown } = useAuth()

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-[50vh]">
        <LoadingSpinner />
      </div>
    )
  }

  // "We could not ask who you are" is not "you are not an admin". Redirecting here threw an
  // admin off their own page — and out of a half-written article — over one failed request.
  if (sessionUnknown) {
    return <SessionUnavailable />
  }

  if (!isAuthenticated || !isAdmin) {
    return <Navigate to="/" replace />
  }

  return <>{children}</>
}
