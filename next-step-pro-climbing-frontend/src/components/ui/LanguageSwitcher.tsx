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

export function LanguageSwitcher() {
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

  return (
    <div className="relative" ref={ref}>
      <button
        onClick={() => setOpen(!open)}
        className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-dark-300 hover:text-dark-100 hover:bg-dark-800 transition-colors text-sm"
      >
        <Globe className="w-4 h-4" />
        <span className="font-medium">{currentLang.label}</span>
      </button>

      {open && (
        <div className="absolute right-0 mt-1 bg-dark-900 border border-dark-700 rounded-lg shadow-lg shadow-black/30 overflow-hidden z-50">
          {LANGUAGES.map((lang) => (
            <button
              key={lang.code}
              onClick={() => handleLanguageChange(lang.code)}
              className={clsx(
                'w-full px-4 py-2 text-sm text-left transition-colors',
                lang.code === i18n.language
                  ? 'bg-primary-500/10 text-primary-400'
                  : 'text-dark-300 hover:bg-dark-800 hover:text-dark-100',
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
