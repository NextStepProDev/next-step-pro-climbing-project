import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { format } from 'date-fns'
import { Send, Check, BellOff, UserCheck } from 'lucide-react'
import { adminApi } from '../../api/client'
import { getErrorMessage } from '../../utils/errors'
import { inviteStatus, invitesAwaitingMail } from '../../utils/inviteStatus'
import type { InvitedUser, NotifyInvitesResult } from '../../types'

interface InviteNotifySectionProps {
  target: { type: 'slot'; slotId: string } | { type: 'event'; eventId: string }
  /** Invitee baseline from the server (with notifiedAt) — NOT the local, unedited picker state. */
  invites: InvitedUser[]
}

/**
 * Manual sending of invitation emails to people with a held seat.
 * Says per person why they will or will not be written to, plus a button to send to those still
 * waiting. Sending is deliberate — nothing goes out automatically when invitations are saved.
 */
export function InviteNotifySection({ target, invites }: InviteNotifySectionProps) {
  const { t } = useTranslation('admin')
  const queryClient = useQueryClient()
  const [lastResult, setLastResult] = useState<NotifyInvitesResult | null>(null)

  const notifyMutation = useMutation({
    mutationFn: () =>
      target.type === 'slot'
        ? adminApi.notifySlotInvites(target.slotId)
        : adminApi.notifyEventInvites(target.eventId),
    onSuccess: (result) => {
      setLastResult(result)
      queryClient.invalidateQueries({
        queryKey: target.type === 'slot'
          ? ['admin', 'slotInvites', target.slotId]
          : ['admin', 'eventInvites', target.eventId],
      })
    },
  })

  if (invites.length === 0) return null

  const awaiting = invitesAwaitingMail(invites)

  return (
    <div className="p-3 bg-violet-500/5 border border-violet-500/20 rounded-lg space-y-2">
      <p className="text-xs font-medium text-violet-300">{t('inviteNotify.title')}</p>
      <ul className="space-y-1">
        {invites.map((u) => (
          <li key={u.userId} className="flex items-center justify-between gap-2 text-xs">
            <span className="text-surface-300 truncate" title={u.email}>{u.fullName || u.email}</span>
            <InviteStatusLabel user={u} />
          </li>
        ))}
      </ul>

      {awaiting.length > 0 ? (
        <button
          type="button"
          onClick={() => notifyMutation.mutate()}
          disabled={notifyMutation.isPending}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-violet-600 hover:bg-violet-500 disabled:opacity-50 text-white text-xs font-medium transition-colors"
        >
          <Send className="w-3.5 h-3.5" />
          {t('inviteNotify.send', { count: awaiting.length })}
        </button>
      ) : (
        lastResult !== null && (
          <p className="text-xs text-emerald-400/90">
            {t('inviteNotify.sentResult', { count: lastResult.notifiedCount })}
            {lastResult.skippedNotificationsOff > 0 && (
              ' ' + t('inviteNotify.skipped', { count: lastResult.skippedNotificationsOff })
            )}
          </p>
        )
      )}

      {notifyMutation.isError && (
        <p className="text-xs text-rose-400/80">{getErrorMessage(notifyMutation.error)}</p>
      )}
    </div>
  )
}

/**
 * One line per invitee. "Not sent" alone could not distinguish someone waiting for a mail from
 * someone the send will never write to, so the two skipped cases name themselves.
 */
function InviteStatusLabel({ user }: { user: InvitedUser }) {
  const { t } = useTranslation('admin')
  const status = inviteStatus(user)

  if (status === 'booked') {
    return (
      <span className="flex items-center gap-1 text-surface-400 shrink-0">
        <UserCheck className="w-3.5 h-3.5" />
        {t('inviteNotify.booked')}
      </span>
    )
  }
  if (status === 'sent') {
    return (
      <span className="flex items-center gap-1 text-emerald-400/90 shrink-0">
        <Check className="w-3.5 h-3.5" />
        {t('inviteNotify.sentAt', { date: format(new Date(user.notifiedAt!), 'dd.MM HH:mm') })}
      </span>
    )
  }
  if (status === 'notificationsOff') {
    return (
      <span className="flex items-center gap-1 text-amber-500 shrink-0">
        <BellOff className="w-3.5 h-3.5" />
        {t('inviteNotify.notificationsOff')}
      </span>
    )
  }
  return <span className="text-surface-500 shrink-0">{t('inviteNotify.notSent')}</span>
}
