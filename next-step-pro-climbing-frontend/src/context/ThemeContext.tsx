import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'

type ThemeChoice = 'dark' | 'light' | 'system'
type ResolvedTheme = 'dark' | 'light'

interface ThemeContextValue {
  theme: ResolvedTheme
  choice: ThemeChoice
  toggleTheme: () => void
  setTheme: (choice: ThemeChoice) => void
}

const ThemeContext = createContext<ThemeContextValue | null>(null)

const STORAGE_KEY = 'nsp-theme'

function getSystemTheme(): ResolvedTheme {
  return window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark'
}

function getStoredChoice(): ThemeChoice {
  const stored = localStorage.getItem(STORAGE_KEY)
  if (stored === 'light' || stored === 'dark' || stored === 'system') return stored
  return 'dark'
}

function applyTheme(theme: ResolvedTheme, choice: ThemeChoice) {
  document.documentElement.dataset.theme = theme
  localStorage.setItem(STORAGE_KEY, choice)
  const meta = document.querySelector('meta[name="theme-color"]')
  if (meta) {
    meta.setAttribute('content', theme === 'dark' ? '#1a1816' : '#f5f0ea')
  }
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [choice, setChoiceState] = useState<ThemeChoice>(getStoredChoice)
  const [systemTheme, setSystemTheme] = useState<ResolvedTheme>(getSystemTheme)

  const resolved: ResolvedTheme = choice === 'system' ? systemTheme : choice

  useEffect(() => {
    applyTheme(resolved, choice)
  }, [resolved, choice])

  useEffect(() => {
    const mq = window.matchMedia('(prefers-color-scheme: light)')
    const handler = () => setSystemTheme(getSystemTheme())
    mq.addEventListener('change', handler)
    return () => mq.removeEventListener('change', handler)
  }, [])

  const toggleTheme = useCallback(() => {
    setChoiceState(prev => {
      const current = prev === 'system' ? systemTheme : prev
      return current === 'dark' ? 'light' : 'dark'
    })
  }, [systemTheme])

  const setTheme = useCallback((c: ThemeChoice) => {
    setChoiceState(c)
  }, [])

  const value = useMemo(() => ({
    theme: resolved,
    choice,
    toggleTheme,
    setTheme,
  }), [resolved, choice, toggleTheme, setTheme])

  return (
    <ThemeContext value={value}>
      {children}
    </ThemeContext>
  )
}

// eslint-disable-next-line react-refresh/only-export-components
export function useTheme() {
  const ctx = useContext(ThemeContext)
  if (!ctx) throw new Error('useTheme must be used within ThemeProvider')
  return ctx
}
