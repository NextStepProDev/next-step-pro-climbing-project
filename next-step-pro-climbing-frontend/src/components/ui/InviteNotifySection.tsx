import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { format } from 'date-fns'
import { Send, Check } from 'lucide-react'
import { adminApi } from '../../api/client'
import { getErrorMessage } from '../../utils/errors'
import type { InvitedUser } from '../../types'

interface InviteNotifySectionProps {
  target: { type: 'slot'; slotId: string } | { type: 'event'; eventId: string }
  /** Baseline zaproszonych z serwera (z notifiedAt) — NIE lokalny, nieedytowany stan pickera. */
  invites: InvitedUser[]
}

/**
 * Ręczna wysyłka maili z zaproszeniem do osób z trzymanym miejscem.
 * Pokazuje per-osoba, czy zaproszenie już poszło (notifiedAt), i przycisk
 * wysyłki do tych, którzy jeszcze go nie dostali. Wysyłka jest świadoma —
 * nic nie idzie automatycznie przy zapisie zaproszeń.
 */
export function InviteNotifySection({ target, invites }: InviteNotifySectionProps) {
  const { t } = useTranslation('admin')
  const queryClient = useQueryClient()
  const [lastSent, setLastSent] = useState<number | null>(null)

  const notifyMutation = useMutation({
    mutationFn: () =>
      target.type === 'slot'
        ? adminApi.notifySlotInvites(target.slotId)
        : adminApi.notifyEventInvites(target.eventId),
    onSuccess: (result) => {
      setLastSent(result.notifiedCount)
      queryClient.invalidateQueries({
        queryKey: target.type === 'slot'
          ? ['admin', 'slotInvites', target.slotId]
          : ['admin', 'eventInvites', target.eventId],
      })
    },
  })

  if (invites.length === 0) return null

  const unnotified = invites.filter((u) => !u.notifiedAt)

  return (
    <div className="p-3 bg-violet-500/5 border border-violet-500/20 rounded-lg space-y-2">
      <p className="text-xs font-medium text-violet-300">{t('inviteNotify.title')}</p>
      <ul className="space-y-1">
        {invites.map((u) => (
          <li key={u.userId} className="flex items-center justify-between text-xs">
            <span className="text-surface-300 truncate" title={u.email}>{u.fullName || u.email}</span>
            {u.notifiedAt ? (
              <span className="flex items-center gap-1 text-emerald-400/90 shrink-0">
                <Check className="w-3.5 h-3.5" />
                {t('inviteNotify.sentAt', { date: format(new Date(u.notifiedAt), 'dd.MM HH:mm') })}
              </span>
            ) : (
              <span className="text-surface-500 shrink-0">{t('inviteNotify.notSent')}</span>
            )}
          </li>
        ))}
      </ul>

      {unnotified.length > 0 ? (
        <button
          type="button"
          onClick={() => notifyMutation.mutate()}
          disabled={notifyMutation.isPending}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-violet-600 hover:bg-violet-500 disabled:opacity-50 text-white text-xs font-medium transition-colors"
        >
          <Send className="w-3.5 h-3.5" />
          {t('inviteNotify.send', { count: unnotified.length })}
        </button>
      ) : (
        lastSent !== null && (
          <p className="text-xs text-emerald-400/90">{t('inviteNotify.sentResult', { count: lastSent })}</p>
        )
      )}

      {notifyMutation.isError && (
        <p className="text-xs text-rose-400/80">{getErrorMessage(notifyMutation.error)}</p>
      )}
    </div>
  )
}
