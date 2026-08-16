import { useState, useRef, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { Search, X, MailWarning } from 'lucide-react'
import type { AdminUser } from '../../types'

interface UserSearchSelectProps {
  users: AdminUser[]
  value: string
  onChange: (userId: string) => void
  placeholder?: string
}

export function UserSearchSelect({ users, value, onChange, placeholder }: UserSearchSelectProps) {
  const { t } = useTranslation('admin')
  const selected = users.find((u) => u.id === value) ?? null
  const [query, setQuery] = useState('')
  const [open, setOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)

  const normalized = query.trim().toLowerCase()
  const filtered = normalized
    ? users.filter((u) => {
        const full = `${u.firstName} ${u.lastName}`.toLowerCase()
        return (
          full.includes(normalized) ||
          u.firstName.toLowerCase().includes(normalized) ||
          u.lastName.toLowerCase().includes(normalized) ||
          u.email.toLowerCase().includes(normalized)
        )
      })
    : users.slice(0, 8)

  // Close dropdown when clicking outside
  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  function handleSelect(user: AdminUser) {
    if (!user.emailVerified) return
    onChange(user.id)
    setQuery('')
    setOpen(false)
  }

  function handleClear() {
    onChange('')
    setQuery('')
  }

  return (
    <div ref={containerRef} className="relative">
      {selected && !open ? (
        <div className="flex items-center justify-between bg-surface-800 border border-surface-600 rounded-lg px-3 py-2 text-sm">
          <div className="min-w-0">
            <span className="font-medium text-surface-100">
              {selected.firstName} {selected.lastName}
            </span>
            <span className="ml-2 text-surface-400 text-xs truncate">{selected.email}</span>
          </div>
          <button
            type="button"
            aria-label="Clear selection"
            onClick={handleClear}
            className="ml-2 shrink-0 text-surface-400 hover:text-surface-200 transition-colors"
          >
            <X className="w-3.5 h-3.5" />
          </button>
        </div>
      ) : (
        <div className="relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-surface-400 pointer-events-none" />
          <input
            type="text"
            value={query}
            onChange={(e) => { setQuery(e.target.value); setOpen(true) }}
            onFocus={() => setOpen(true)}
            placeholder={placeholder ?? 'Szukaj po imieniu, nazwisku lub emailu...'}
            className="w-full bg-surface-800 border border-surface-700 rounded-lg pl-9 pr-3 py-2 text-surface-100 text-sm focus:outline-none focus:ring-1 focus:ring-primary-500 focus:border-primary-500"
          />
        </div>
      )}

      {open && (
        <div className="absolute z-50 w-full mt-1 bg-surface-800 border border-surface-700 rounded-lg shadow-lg overflow-hidden">
          {filtered.length === 0 ? (
            <div className="px-3 py-2 text-sm text-surface-500">Brak wyników</div>
          ) : (
            <ul className="max-h-48 overflow-y-auto">
              {filtered.map((u) => (
                <li key={u.id}>
                  {/* An unverified account stays visible but unselectable. Filtering it out would
                      answer "does this person have an account?" with silence, and the admin would
                      conclude there is none. */}
                  <button
                    type="button"
                    disabled={!u.emailVerified}
                    title={u.emailVerified ? undefined : t('users.unverifiedHint')}
                    onMouseDown={(e) => { e.preventDefault(); handleSelect(u) }}
                    className={`w-full text-left px-3 py-2 text-sm transition-colors ${
                      u.emailVerified
                        ? 'hover:bg-surface-700'
                        : 'cursor-not-allowed opacity-60'
                    }`}
                  >
                    <span className={u.emailVerified ? 'font-medium text-surface-100' : 'font-medium text-surface-400'}>
                      {u.firstName} {u.lastName}
                    </span>
                    <span className="ml-2 text-surface-400 text-xs">{u.email}</span>
                    {!u.emailVerified && (
                      <span className="ml-2 inline-flex items-center gap-1 text-amber-400/90 text-xs">
                        <MailWarning className="w-3 h-3 shrink-0" />
                        {t('users.unverifiedBadge')}
                      </span>
                    )}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  )
}
