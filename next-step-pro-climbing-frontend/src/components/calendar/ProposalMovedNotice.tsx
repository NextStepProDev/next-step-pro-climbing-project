import { useTranslation } from 'react-i18next'
import { AlertTriangle } from 'lucide-react'
import { useDateLocale } from '../../utils/dateFnsLocale'
import { formatTerm } from '../../utils/proposalTerm'
import type { MyInvitation, TrainingRequest } from '../../types'

/*
 * ⚠️ A proposal accepted at other hours must never read as accepted as proposed.
 *
 * The admin answers a proposal by creating an entry, and nothing stops it landing an hour later.
 * The client's request card prints the hours THEY typed, and a green "accepted" beside them says
 * "come then". Whether the term moved is decided by the server (`termChanged`, `proposedDate`),
 * never recomputed here, so these only render what they are handed.
 */

/** On the client's own request: what was agreed, and their proposal struck through. */
export function AcceptedAtOtherTimeNotice({ request }: { request: TrainingRequest }) {
  const { t } = useTranslation('reservations')
  const locale = useDateLocale()
  if (request.status !== 'ACCEPTED' || !request.termChanged || !request.agreedDate) return null

  const allDay = t('trainingRequests.allDay')
  return (
    <div className="mt-3 p-3 rounded-lg border border-amber-500/40 bg-amber-500/10 text-sm">
      <p className="flex items-start gap-1.5 font-medium text-amber-300">
        <AlertTriangle className="w-4 h-4 mt-0.5 shrink-0" />
        {t('trainingRequests.changedHint')}
      </p>
      <p className="mt-1.5 text-surface-100">
        <span className="text-surface-400">{t('trainingRequests.agreedTerm')}</span>{' '}
        <span className="font-semibold capitalize">
          {formatTerm(
            { date: request.agreedDate, endDate: request.agreedEndDate, startTime: request.agreedStartTime, endTime: request.agreedEndTime },
            locale,
            allDay,
          )}
        </span>
      </p>
      <p className="mt-0.5 text-surface-400">
        {t('trainingRequests.yourProposal')}{' '}
        <span className="line-through capitalize">
          {formatTerm({ date: request.requestedDate, startTime: request.startTime, endTime: request.endTime }, locale, allDay)}
        </span>
      </p>
    </div>
  )
}

/** On the invitation, right above "book": the last point where the change can still be noticed. */
export function InvitationMovedNotice({ invitation }: { invitation: MyInvitation }) {
  const { t } = useTranslation('reservations')
  const locale = useDateLocale()
  if (!invitation.proposedDate) return null

  return (
    <p className="mt-2 flex items-start gap-1.5 p-2.5 rounded-lg border border-amber-500/40 bg-amber-500/10 text-sm font-medium text-amber-300">
      <AlertTriangle className="w-4 h-4 mt-0.5 shrink-0" />
      {t('invitations.moved', {
        proposal: formatTerm(
          { date: invitation.proposedDate, startTime: invitation.proposedStartTime, endTime: invitation.proposedEndTime },
          locale,
          t('trainingRequests.allDay'),
        ),
      })}
    </p>
  )
}
