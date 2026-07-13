import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { X } from 'lucide-react'
import { adminApi } from '../../api/client'
import type { InvitedUser, User } from '../../types'

interface InvitedUsersPickerProps {
  value: InvitedUser[]
  onChange: (users: InvitedUser[]) => void
  /** Seat limit (e.g. maxParticipants) — shows a warning once exceeded. */
  maxSeats?: number
}

/**
 * Invited users picker (invitation-held seats).
 * Searches registered accounts by first/last name/email; shows selections as chips.
 */
export function InvitedUsersPicker({ value, onChange, maxSeats }: InvitedUsersPickerProps) {
  const { t } = useTranslation('calendar')
  const [search, setSearch] = useState('')

  const { data: users = [] } = useQuery({
    queryKey: ['admin', 'allUsers'],
    queryFn: adminApi.getAllUsers,
  })

  const selectedIds = useMemo(() => new Set(value.map((u) => u.userId)), [value])

  const results = useMemo(() => {
    const q = search.trim().toLowerCase()
    if (!q) return []
    return users
      .filter((u) => !selectedIds.has(u.id))
      .filter((u) => `${u.firstName} ${u.lastName} ${u.email}`.toLowerCase().includes(q))
      .slice(0, 6)
  }, [search, users, selectedIds])

  const add = (u: User) => {
    onChange([
      ...value,
      { userId: u.id, fullName: `${u.firstName} ${u.lastName}`.trim(), email: u.email },
    ])
    setSearch('')
  }

  const remove = (id: string) => onChange(value.filter((u) => u.userId !== id))

  const overLimit = maxSeats != null && value.length > maxSeats

  return (
    <div>
      <label className="block text-sm font-medium text-violet-300 mb-1">{t('invites.label')}</label>
      <p className="text-xs text-surface-400 mb-2">{t('invites.hint')}</p>

      {value.length > 0 && (
        <div className="flex flex-wrap gap-2 mb-2">
          {value.map((u) => (
            <span
              key={u.userId}
              className="inline-flex items-center gap-1.5 bg-violet-500/15 text-violet-200 border border-violet-500/30 rounded-full pl-3 pr-1.5 py-1 text-sm"
            >
              <span title={u.email}>{u.fullName || u.email}</span>
              <button
                type="button"
                onClick={() => remove(u.userId)}
                className="rounded-full p-0.5 hover:bg-violet-500/30"
                aria-label={t('invites.remove')}
              >
                <X size={14} />
              </button>
            </span>
          ))}
        </div>
      )}

      <input
        type="text"
        value={search}
        onChange={(e) => setSearch(e.target.value)}
        placeholder={t('invites.searchPlaceholder')}
        className="w-full bg-surface-800 border border-surface-700 rounded-lg px-4 py-2 text-surface-100 focus:outline-none focus:ring-2 focus:ring-violet-500"
      />

      {results.length > 0 && (
        <ul className="mt-1 bg-surface-800 border border-surface-700 rounded-lg divide-y divide-surface-700 max-h-48 overflow-auto">
          {results.map((u) => (
            <li key={u.id}>
              <button
                type="button"
                onClick={() => add(u)}
                className="w-full text-left px-3 py-2 hover:bg-surface-700"
              >
                <span className="text-surface-100 text-sm">{u.firstName} {u.lastName}</span>
                <span className="block text-xs text-surface-400">{u.email}</span>
              </button>
            </li>
          ))}
        </ul>
      )}

      {overLimit && (
        <p className="text-xs text-rose-400/80 mt-1">{t('invites.overLimit', { count: maxSeats })}</p>
      )}
    </div>
  )
}
