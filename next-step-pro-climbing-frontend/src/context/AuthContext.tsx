import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { authApi } from '../api/client'
import { loginUser as apiLogin } from '../api/auth'
import { saveTokens, clearTokens, hasTokens, type AuthTokens } from '../utils/tokenStorage'
import { ApiError } from '../utils/errors'
import type { User } from '../types'

/**
 * A failed `/user/me` is not proof that the login is over.
 *
 * This used to clear the tokens on ANY error, so one 429 from the rate limiter — or a
 * momentary network drop — threw the user out of a perfectly valid session and made them log
 * in again. Only a server that actively refused the credentials ends a session; everything
 * else leaves the tokens alone, so the next attempt (or a reload) picks the session back up.
 */
function endSessionOnlyIfRejected(error: unknown, forget: () => void) {
  // Positive proof required, and the default is to keep the session: a network failure arrives
  // here as a plain Error (fetchApi turns it into one), so "not an ApiError" must mean "I don't
  // know", not "log them out". Keeping a stale token costs nothing — the next request 401s, the
  // refresh runs, and THAT path ends the session properly if the server really refuses it.
  const refused = error instanceof ApiError && error.isAuthRejection
  if (!refused) return
  clearTokens()
  forget()
}

interface AuthContextType {
  user: User | null
  isLoading: boolean
  isAuthenticated: boolean
  isAdmin: boolean
  login: (email: string, password: string) => Promise<void>
  loginWithTokens: (tokens: AuthTokens) => Promise<void>
  logout: () => void
  refreshUser: () => Promise<void>
}

const AuthContext = createContext<AuthContextType | undefined>(undefined)

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const { i18n } = useTranslation()
  const [user, setUser] = useState<User | null>(null)
  // Initialise from token presence so the "logged out" case needs no synchronous
  // state update on mount (avoids set-state-in-effect).
  const [isLoading, setIsLoading] = useState(() => hasTokens())

  const i18nRef = useRef(i18n)
  useEffect(() => {
    i18nRef.current = i18n
  }, [i18n])

  const syncLanguage = useCallback((preferredLanguage: string) => {
    if (preferredLanguage && preferredLanguage !== i18nRef.current.language) {
      i18nRef.current.changeLanguage(preferredLanguage)
    }
  }, [])

  const fetchUser = useCallback(async () => {
    if (!hasTokens()) {
      setUser(null)
      setIsLoading(false)
      return
    }
    try {
      const currentUser = await authApi.getCurrentUser()
      setUser(currentUser)
      syncLanguage(currentUser.preferredLanguage)
    } catch (error) {
      endSessionOnlyIfRejected(error, () => setUser(null))
    } finally {
      setIsLoading(false)
    }
  }, [syncLanguage])

  // Load the existing session on mount. With no tokens the initial state already
  // reflects "logged out", so we bail out without a synchronous state update; every
  // state write below happens only after the request settles.
  useEffect(() => {
    if (!hasTokens()) return
    let cancelled = false
    authApi.getCurrentUser()
      .then((currentUser) => {
        if (cancelled) return
        setUser(currentUser)
        syncLanguage(currentUser.preferredLanguage)
      })
      .catch((error) => {
        if (cancelled) return
        endSessionOnlyIfRejected(error, () => setUser(null))
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [syncLanguage])

  useEffect(() => {
    const handler = () => {
      clearTokens()
      setUser(null)
      queryClient.clear()
    }
    window.addEventListener('auth:session-expired', handler)
    return () => window.removeEventListener('auth:session-expired', handler)
  }, [queryClient])

  const login = useCallback(async (email: string, password: string) => {
    const tokens = await apiLogin({ email, password })
    saveTokens(tokens)
    const currentUser = await authApi.getCurrentUser()
    setUser(currentUser)
    syncLanguage(currentUser.preferredLanguage)
  }, [syncLanguage])

  const loginWithTokens = useCallback(async (tokens: AuthTokens) => {
    saveTokens(tokens)
    const currentUser = await authApi.getCurrentUser()
    setUser(currentUser)
    syncLanguage(currentUser.preferredLanguage)
  }, [syncLanguage])

  const logout = useCallback(() => {
    authApi.logout()
    setUser(null)
    queryClient.clear()
  }, [queryClient])

  const value = useMemo<AuthContextType>(() => ({
    user,
    isLoading,
    isAuthenticated: !!user,
    isAdmin: user?.isAdmin ?? false,
    login,
    loginWithTokens,
    logout,
    refreshUser: fetchUser,
  }), [user, isLoading, login, loginWithTokens, logout, fetchUser])

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  )
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() {
  const context = useContext(AuthContext)
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}

