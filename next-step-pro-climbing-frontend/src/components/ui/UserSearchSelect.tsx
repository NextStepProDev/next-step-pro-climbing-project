import { useState, useRef, useEffect } from 'react'
import { Search, X } from 'lucide-react'
import type { User } from '../../types'

interface UserSearchSelectProps {
  users: User[]
  value: string
  onChange: (userId: string) => void
  placeholder?: string
}

export function UserSearchSelect({ users, value, onChange, placeholder }: UserSearchSelectProps) {
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

  function handleSelect(user: User) {
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
        <div className="flex items-center justify-between bg-dark-800 border border-dark-600 rounded-lg px-3 py-2 text-sm">
          <div className="min-w-0">
            <span className="font-medium text-dark-100">
              {selected.firstName} {selected.lastName}
            </span>
            <span className="ml-2 text-dark-400 text-xs truncate">{selected.email}</span>
          </div>
          <button
            type="button"
            onClick={handleClear}
            className="ml-2 shrink-0 text-dark-400 hover:text-dark-200 transition-colors"
          >
            <X className="w-3.5 h-3.5" />
          </button>
        </div>
      ) : (
        <div className="relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-dark-400 pointer-events-none" />
          <input
            type="text"
            value={query}
            onChange={(e) => { setQuery(e.target.value); setOpen(true) }}
            onFocus={() => setOpen(true)}
            placeholder={placeholder ?? 'Szukaj po imieniu, nazwisku lub emailu...'}
            className="w-full bg-dark-800 border border-dark-700 rounded-lg pl-9 pr-3 py-2 text-dark-100 text-sm focus:outline-none focus:ring-1 focus:ring-primary-500 focus:border-primary-500"
          />
        </div>
      )}

      {open && (
        <div className="absolute z-50 w-full mt-1 bg-dark-800 border border-dark-700 rounded-lg shadow-lg overflow-hidden">
          {filtered.length === 0 ? (
            <div className="px-3 py-2 text-sm text-dark-500">Brak wyników</div>
          ) : (
            <ul className="max-h-48 overflow-y-auto">
              {filtered.map((u) => (
                <li key={u.id}>
                  <button
                    type="button"
                    onMouseDown={(e) => { e.preventDefault(); handleSelect(u) }}
                    className="w-full text-left px-3 py-2 text-sm hover:bg-dark-700 transition-colors"
                  >
                    <span className="font-medium text-dark-100">
                      {u.firstName} {u.lastName}
                    </span>
                    <span className="ml-2 text-dark-400 text-xs">{u.email}</span>
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
