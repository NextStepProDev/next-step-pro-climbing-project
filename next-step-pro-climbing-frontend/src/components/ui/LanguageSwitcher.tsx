import { useState, useRef, useEffect } from 'react'
import { Globe } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import clsx from 'clsx'
import { useAuth } from '../../context/AuthContext'
import { authApi } from '../../api/client'

const LANGUAGES = [
  { code: 'pl', label: 'PL' },
  { code: 'en', label: 'EN' },
  { code: 'es', label: 'ES' },
] as const

interface LanguageSwitcherProps {
  /**
   * `segmented` lays the three languages out in a row instead of behind a popover — for the
   * mobile drawer, where a dropdown anchored at the bottom of the sheet would open off-screen.
   * The change handler stays shared, so switching language means the same thing in both.
   */
  variant?: 'dropdown' | 'segmented'
  className?: string
}

export function LanguageSwitcher({ variant = 'dropdown', className }: LanguageSwitcherProps = {}) {
  const { i18n } = useTranslation()
  const { isAuthenticated } = useAuth()
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  const currentLang = LANGUAGES.find((l) => l.code === i18n.language) ?? LANGUAGES[0]

  const handleLanguageChange = (langCode: string) => {
    i18n.changeLanguage(langCode)
    setOpen(false)
    if (isAuthenticated) {
      authApi.updateLanguage(langCode).catch(() => {})
    }
  }

  if (variant === 'segmented') {
    return (
      <div
        className={clsx(
          'flex items-center gap-1 p-1 rounded-lg border border-surface-800 bg-surface-950/40',
          className,
        )}
      >
        <Globe className="w-4 h-4 shrink-0 ml-1.5 mr-0.5 text-surface-500" aria-hidden />
        {LANGUAGES.map((lang) => (
          <button
            key={lang.code}
            onClick={() => handleLanguageChange(lang.code)}
            aria-pressed={lang.code === i18n.language}
            className={clsx(
              'flex-1 px-2 py-1.5 rounded-md text-xs font-semibold transition-all duration-150 active:scale-95',
              // surface-800 sits one step from the sheet it lies on — the selected language was
              // indistinguishable from the other two. The pill needs its own contrast, not a tint.
              lang.code === i18n.language
                ? 'bg-surface-700 text-surface-50 shadow-sm'
                : 'text-surface-400 hover:text-surface-200',
            )}
          >
            {lang.label}
          </button>
        ))}
      </div>
    )
  }

  return (
    <div className="relative" ref={ref}>
      <button
        onClick={() => setOpen(!open)}
        className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-surface-300 hover:text-surface-100 hover:bg-surface-800 transition-all duration-150 active:scale-95 text-sm"
      >
        <Globe className="w-4 h-4" />
        <span className="font-medium">{currentLang.label}</span>
      </button>

      {open && (
        <div className="absolute right-0 mt-1 bg-surface-900 border border-surface-700 rounded-lg shadow-lg shadow-black/30 overflow-hidden z-50">
          {LANGUAGES.map((lang) => (
            <button
              key={lang.code}
              onClick={() => handleLanguageChange(lang.code)}
              className={clsx(
                'w-full px-4 py-2 text-sm text-left transition-all duration-150 active:scale-95',
                lang.code === i18n.language
                  ? 'bg-primary-500/10 text-primary-400'
                  : 'text-surface-300 hover:bg-surface-800 hover:text-surface-100',
              )}
            >
              {lang.label}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
