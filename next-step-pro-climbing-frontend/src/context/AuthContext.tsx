import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { authApi } from '../api/client'
import { loginUser as apiLogin } from '../api/auth'
import { saveTokens, clearTokens, hasTokens, type AuthTokens } from '../utils/tokenStorage'
import type { User } from '../types'

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
  const [isLoading, setIsLoading] = useState(true)

  const syncLanguage = useCallback((preferredLanguage: string) => {
    if (preferredLanguage && preferredLanguage !== i18n.language) {
      i18n.changeLanguage(preferredLanguage)
    }
  }, [i18n])

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
    } catch {
      clearTokens()
      setUser(null)
    } finally {
      setIsLoading(false)
    }
  }, [syncLanguage])

  useEffect(() => {
    fetchUser()
  }, [fetchUser])

  useEffect(() => {
    const handler = () => {
      clearTokens()
      setUser(null)
      queryClient.clear()
    }
    window.addEventListener('auth:session-expired', handler)
    return () => window.removeEventListener('auth:session-expired', handler)
  }, [queryClient])

  const login = async (email: string, password: string) => {
    const tokens = await apiLogin({ email, password })
    saveTokens(tokens)
    const currentUser = await authApi.getCurrentUser()
    setUser(currentUser)
    syncLanguage(currentUser.preferredLanguage)
  }

  const loginWithTokens = async (tokens: AuthTokens) => {
    saveTokens(tokens)
    const currentUser = await authApi.getCurrentUser()
    setUser(currentUser)
    syncLanguage(currentUser.preferredLanguage)
  }

  const logout = useCallback(() => {
    authApi.logout()
    setUser(null)
    queryClient.clear()
  }, [queryClient])

  return (
    <AuthContext.Provider
      value={{
        user,
        isLoading,
        isAuthenticated: !!user,
        isAdmin: user?.isAdmin ?? false,
        login,
        loginWithTokens,
        logout,
        refreshUser: fetchUser,
      }}
    >
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

