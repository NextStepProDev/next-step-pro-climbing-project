import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { authApi } from '../api/client'
import { loginUser as apiLogin } from '../api/auth'
import { saveTokens, clearTokens, hasTokens, type AuthTokens } from '../utils/tokenStorage'
import { ApiError } from '../utils/errors'
import type { User } from '../types'

/**
 * A failed `/user/me` is not proof that the login is over. Returns true when the session really
 * ended, false when we simply could not find out.
 *
 * This used to clear the tokens on ANY error, so one 429 from the rate limiter — or a momentary
 * network drop — threw the user out of a perfectly valid session. Positive proof is required and
 * the default is to keep the session: a network failure arrives here as a plain Error (fetchApi
 * turns it into one), so "not an ApiError" must mean "I don't know", not "log them out". Keeping
 * a stale token costs nothing — the next request 401s, the refresh runs, and THAT path ends the
 * session properly if the server really refuses it.
 *
 * The return value matters as much as the clearing: "I don't know" has to reach the route guards,
 * or they read a null user as "not logged in" and redirect — which is how a blip used to eject an
 * admin from the page they were working on.
 */
function endSessionOnlyIfRejected(error: unknown, forget: () => void): boolean {
  const refused = error instanceof ApiError && error.isAuthRejection
  if (!refused) return false
  clearTokens()
  forget()
  return true
}

interface AuthContextType {
  user: User | null
  isLoading: boolean
  isAuthenticated: boolean
  isAdmin: boolean
  /**
   * We hold tokens but could not find out who they belong to, and the server never actually
   * refused them — a 429, a 500, a moment offline. This is NOT "logged out": treating it as
   * such sends the route guards into a redirect, which erases the address the person was on
   * and throws away anything unsaved on the page, all because one request blipped.
   */
  sessionUnknown: boolean
  retrySession: () => Promise<void>
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
  const [sessionUnknown, setSessionUnknown] = useState(false)

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
      setSessionUnknown(false)
      setIsLoading(false)
      return
    }
    try {
      const currentUser = await authApi.getCurrentUser()
      setUser(currentUser)
      setSessionUnknown(false)
      syncLanguage(currentUser.preferredLanguage)
    } catch (error) {
      const ended = endSessionOnlyIfRejected(error, () => setUser(null))
      setSessionUnknown(!ended)
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
        const ended = endSessionOnlyIfRejected(error, () => setUser(null))
        // The server never refused us; we just do not know yet. Say so, so the route guards
        // hold the page instead of redirecting a perfectly valid session off its own address.
        setSessionUnknown(!ended)
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
      setSessionUnknown(false)
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
    setSessionUnknown(false)
    syncLanguage(currentUser.preferredLanguage)
  }, [syncLanguage])

  const loginWithTokens = useCallback(async (tokens: AuthTokens) => {
    saveTokens(tokens)
    const currentUser = await authApi.getCurrentUser()
    setUser(currentUser)
    setSessionUnknown(false)
    syncLanguage(currentUser.preferredLanguage)
  }, [syncLanguage])

  const logout = useCallback(() => {
    authApi.logout()
    setUser(null)
    setSessionUnknown(false)
    queryClient.clear()
  }, [queryClient])

  const value = useMemo<AuthContextType>(() => ({
    user,
    isLoading,
    isAuthenticated: !!user,
    isAdmin: user?.isAdmin ?? false,
    sessionUnknown,
    retrySession: fetchUser,
    login,
    loginWithTokens,
    logout,
    refreshUser: fetchUser,
  }), [user, isLoading, sessionUnknown, login, loginWithTokens, logout, fetchUser])

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

