import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { authApi } from '../api/client'
import { loginUser as apiLogin } from '../api/auth'
import { saveTokens, clearTokens, hasTokens } from '../utils/tokenStorage'
import type { User } from '../types'

const REDIRECT_KEY = 'nsp_redirect_after_login'

export function saveRedirectPath(path: string): void {
  sessionStorage.setItem(REDIRECT_KEY, path)
}

export function consumeRedirectPath(): string | null {
  const path = sessionStorage.getItem(REDIRECT_KEY)
  sessionStorage.removeItem(REDIRECT_KEY)
  return path
}

interface AuthContextType {
  user: User | null
  isLoading: boolean
  isAuthenticated: boolean
  isAdmin: boolean
  login: (email: string, password: string) => Promise<void>
  logout: () => void
  refreshUser: () => Promise<void>
}

const AuthContext = createContext<AuthContextType | undefined>(undefined)

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [user, setUser] = useState<User | null>(null)
  const [isLoading, setIsLoading] = useState(true)

  const fetchUser = useCallback(async () => {
    if (!hasTokens()) {
      setUser(null)
      setIsLoading(false)
      return
    }
    try {
      const currentUser = await authApi.getCurrentUser()
      setUser(currentUser)
    } catch {
      clearTokens()
      setUser(null)
    } finally {
      setIsLoading(false)
    }
  }, [])

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
        logout,
        refreshUser: fetchUser,
      }}
    >
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}
