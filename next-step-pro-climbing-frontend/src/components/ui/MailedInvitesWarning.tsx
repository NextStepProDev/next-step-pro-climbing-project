import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { AlertTriangle } from 'lucide-react'
import { adminApi } from '../../api/client'
import { invitesAlreadyMailed } from '../../utils/inviteStatus'

interface MailedInvitesWarningProps {
  target: { type: 'slot'; slotId: string } | { type: 'event'; eventId: string }
  /** Past term: the invitation describes something that already happened, so nothing is at stake. */
  archived?: boolean
}

/**
 * Warns, in a cancel/block confirmation, that people are holding a mailed invitation for the term
 * about to disappear.
 *
 * Cancellation mails go to confirmed reservations only. An invitee never booked, so nothing
 * reaches them — but they did receive an invitation carrying an ICS attachment, which means the
 * term is sitting in their phone's calendar and they will turn up for it. We deliberately do not
 * mail them automatically (an invitation mail cannot say "cancelled"), so the honest move is to
 * tell the admin, who can write to them in a sentence.
 *
 * Renders nothing when there is nobody in that position — which is the usual case.
 */
export function MailedInvitesWarning({ target, archived }: MailedInvitesWarningProps) {
  const { t } = useTranslation('admin')

  const { data } = useQuery({
    queryKey: target.type === 'slot'
      ? ['admin', 'slotInvites', target.slotId]
      : ['admin', 'eventInvites', target.eventId],
    queryFn: () =>
      target.type === 'slot'
        ? adminApi.getSlotInvites(target.slotId)
        : adminApi.getEventInvites(target.eventId),
    enabled: !archived,
  })

  const mailed = invitesAlreadyMailed(data ?? [])
  if (archived || mailed.length === 0) return null

  return (
    <div className="flex items-start gap-3 p-3 bg-amber-500/10 border border-amber-500/20 rounded-lg">
      <AlertTriangle className="w-5 h-5 text-amber-500 shrink-0 mt-0.5" />
      <div className="text-sm">
        <p className="font-medium text-amber-500 mb-1">
          {t('inviteNotify.cancelWarningTitle', { count: mailed.length })}
        </p>
        <p className="text-amber-500/80">{t('inviteNotify.cancelWarning')}</p>
        <ul className="mt-2 space-y-0.5 text-xs text-surface-400">
          {mailed.map((u) => (
            <li key={u.userId} className="truncate" title={u.email}>
              {u.fullName || u.email}
            </li>
          ))}
        </ul>
      </div>
    </div>
  )
}
