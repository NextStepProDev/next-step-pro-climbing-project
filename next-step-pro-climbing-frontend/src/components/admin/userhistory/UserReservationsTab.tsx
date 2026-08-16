import type { ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { format } from 'date-fns'
import { CalendarClock, History, ListOrdered, Ticket, MessageSquarePlus, UserCog } from 'lucide-react'
import { adminUserHistoryApi } from '../../../api/client'
import { LoadingSpinner } from '../../ui/LoadingSpinner'
import { QueryError } from '../../ui/QueryError'
import { useDateLocale } from '../../../utils/dateFnsLocale'
import { parseCalendarDate } from '../../../utils/calendarDate'
import type { HistoryReservation } from '../../../types'

interface UserReservationsTabProps {
  userId: string
}

const RESERVATION_STATUS_STYLE: Record<HistoryReservation['status'], string> = {
  CONFIRMED: 'bg-green-500/10 text-green-400',
  CANCELLED: 'bg-surface-700 text-surface-300',
  CANCELLED_BY_ADMIN: 'bg-rose-500/10 text-rose-400',
}

/**
 * Everything booking-shaped about one person: bookings, queue entries, held seats and time
 * proposals. Upcoming/past come split from the server, which asks the question in Europe/Warsaw —
 * the browser must not re-derive it from the device clock.
 */
export function UserReservationsTab({ userId }: UserReservationsTabProps) {
  const { t } = useTranslation('admin')
  const locale = useDateLocale()

  const query = useQuery({
    queryKey: ['admin', 'userHistory', userId, 'reservations'],
    queryFn: () => adminUserHistoryApi.getReservations(userId),
  })

  if (query.isLoading) return <div className="py-16 flex justify-center"><LoadingSpinner /></div>
  if (query.isError || !query.data) {
    return <QueryError error={query.error} onRetry={() => query.refetch()} />
  }

  const { upcoming, past, waitlist, invitations, trainingRequests } = query.data
  const isEmpty = !upcoming.length && !past.length && !waitlist.length
    && !invitations.length && !trainingRequests.length

  if (isEmpty) {
    return (
      <div className="bg-surface-900 rounded-xl border border-surface-800 p-8 text-center text-surface-400">
        {t('users.detail.noReservations')}
      </div>
    )
  }

  /** A date label from the API is a Warsaw wall-clock string, never a moment — hence
   *  parseCalendarDate rather than new Date(), which would shift it a day west of Greenwich. */
  const dayLabel = (date: string | null) =>
    date ? format(parseCalendarDate(date), 'd MMM yyyy', { locale }) : '—'

  const timeLabel = (start: string | null, end?: string | null) =>
    start ? `${start.slice(0, 5)}${end ? `–${end.slice(0, 5)}` : ''}` : null

  const reservationRows = (items: HistoryReservation[]) =>
    items.map((r) => (
      <li key={r.id} className="p-3 flex items-start gap-3 flex-wrap">
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2 flex-wrap">
            <span className="text-surface-100 text-sm font-medium truncate">{r.title}</span>
            <span className={`px-2 py-0.5 text-xs rounded ${RESERVATION_STATUS_STYLE[r.status]}`}>
              {t(`users.detail.reservationStatus.${r.status}`)}
            </span>
            {r.createdByAdmin && (
              <span className="inline-flex items-center gap-1 px-2 py-0.5 text-xs rounded bg-surface-700 text-surface-300">
                <UserCog className="w-3 h-3" />
                {t('users.detail.addedByAdmin')}
              </span>
            )}
          </div>
          <div className="text-xs text-surface-400 mt-0.5">
            {dayLabel(r.date)} {timeLabel(r.startTime, r.endTime)}
            {r.eventTitle && r.eventTitle !== r.title && <> · {r.eventTitle}</>}
            {r.participants > 1 && <> · {t('users.detail.participants', { count: r.participants })}</>}
          </div>
          {r.comment && <p className="text-xs text-surface-500 mt-1 break-words">{r.comment}</p>}
        </div>
      </li>
    ))

  return (
    <div className="space-y-4">
      <Section icon={<CalendarClock className="w-4 h-4" />} title={t('users.detail.upcoming')} count={upcoming.length}>
        {reservationRows(upcoming)}
      </Section>

      <Section icon={<History className="w-4 h-4" />} title={t('users.detail.past')} count={past.length}>
        {reservationRows(past)}
      </Section>

      <Section icon={<ListOrdered className="w-4 h-4" />} title={t('users.detail.waitlist')} count={waitlist.length}>
        {waitlist.map((w) => (
          <li key={w.id} className="p-3">
            <div className="flex items-center gap-2 flex-wrap">
              <span className="text-surface-100 text-sm font-medium truncate">{w.title}</span>
              <span className="px-2 py-0.5 text-xs rounded bg-surface-700 text-surface-300">
                {t(`users.detail.waitlistStatus.${w.status}`)}
              </span>
              <span className="text-xs text-surface-400">
                {t('users.detail.position', { position: w.position })}
              </span>
            </div>
            <div className="text-xs text-surface-400 mt-0.5">
              {dayLabel(w.date)} {timeLabel(w.startTime)}
            </div>
          </li>
        ))}
      </Section>

      <Section icon={<Ticket className="w-4 h-4" />} title={t('users.detail.invitations')} count={invitations.length}>
        {invitations.map((i) => (
          <li key={i.id} className="p-3">
            <div className="flex items-center gap-2 flex-wrap">
              <span className="text-surface-100 text-sm font-medium truncate">{i.title}</span>
              {/* Invitation emails are only ever sent by hand, so "not notified" is a normal
                  state and worth showing rather than hiding. */}
              <span className={`px-2 py-0.5 text-xs rounded ${i.notifiedAt ? 'bg-green-500/10 text-green-400' : 'bg-amber-500/10 text-amber-300'}`}>
                {i.notifiedAt ? t('users.detail.notified') : t('users.detail.notNotified')}
              </span>
            </div>
            <div className="text-xs text-surface-400 mt-0.5">
              {dayLabel(i.date)} {timeLabel(i.startTime)}
            </div>
          </li>
        ))}
      </Section>

      <Section icon={<MessageSquarePlus className="w-4 h-4" />} title={t('users.detail.proposals')} count={trainingRequests.length}>
        {trainingRequests.map((r) => (
          <li key={r.id} className="p-3">
            <div className="flex items-center gap-2 flex-wrap">
              <span className="text-surface-100 text-sm font-medium">
                {dayLabel(r.requestedDate)} {timeLabel(r.startTime, r.endTime)}
              </span>
              <span className="px-2 py-0.5 text-xs rounded bg-surface-700 text-surface-300">
                {t(`users.detail.requestStatus.${r.status}`)}
              </span>
              {r.courseTitle && <span className="text-xs text-surface-400 truncate">{r.courseTitle}</span>}
            </div>
            {r.comment && <p className="text-xs text-surface-500 mt-1 break-words">{r.comment}</p>}
            {r.adminNote && (
              <p className="text-xs text-surface-400 mt-1 break-words">
                {t('users.detail.adminNote')}: {r.adminNote}
              </p>
            )}
          </li>
        ))}
      </Section>
    </div>
  )
}

/** A section disappears when it is empty — five permanently empty headings would bury the one
 *  section that has anything in it. */
function Section({ icon, title, count, children }: {
  icon: ReactNode
  title: string
  count: number
  children: ReactNode
}) {
  if (count === 0) return null
  return (
    <div>
      <h3 className="flex items-center gap-1.5 text-sm font-semibold text-surface-300 mb-2">
        {icon}
        {title}
        <span className="text-surface-500 font-normal">({count})</span>
      </h3>
      <ul className="bg-surface-900 border border-surface-800 rounded-xl divide-y divide-surface-800">
        {children}
      </ul>
    </div>
  )
}
