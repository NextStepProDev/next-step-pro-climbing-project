import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { format } from 'date-fns'
import { ExternalLink } from 'lucide-react'
import { formatAvailability } from '../../utils/events'
import { parseCalendarDate } from '../../utils/calendarDate'
import type { EventSummary } from '../../types'

interface EventListItemProps {
  event: EventSummary
  dotClass: string
  courseReturnTo: string
  onSelect: (event: EventSummary) => void
}

/**
 * One row of the event list under the month and week calendars. Shared, because the two copies
 * had already drifted apart (the week one never learned CONTACT_DAY).
 *
 * On a phone the title gets its own line and the badges wrap below it: four pieces in one
 * non-wrapping row do not fit ~360 px, and the title was being painted underneath the badges.
 */
export function EventListItem({ event, dotClass, courseReturnTo, onSelect }: EventListItemProps) {
  const { t } = useTranslation('calendar')
  const { label, badgeClass } = formatAvailability(event)
  // Full also when other people's invitations hold the remaining seats — then the waitlist.
  const reservedForOthers = Math.max(0, (event.reservedSeats ?? 0) - (event.isReservedForUser ? 1 : 0))
  const isFull = event.currentParticipants + reservedForOthers >= event.maxParticipants

  const status =
    event.eventType === 'UNAVAILABLE'
      ? { text: t('event.unavailable'), cls: 'bg-slate-500/20 text-slate-300' }
      : event.isUserRegistered
        ? { text: t('signedUp'), cls: 'bg-primary-500/20 text-primary-400' }
        : event.eventType === 'CONTACT_DAY'
          ? { text: t('common:callPhone'), cls: 'bg-indigo-500/20 text-indigo-400' }
          : !event.enrollmentOpen
            ? { text: t('common:callPhone'), cls: 'bg-surface-700 text-surface-400' }
            : isFull
              ? { text: t('event.waitlist.join'), cls: 'bg-amber-500/20 text-amber-400' }
              : { text: t('signUp'), cls: 'bg-primary-600 text-white' }

  return (
    <div
      className="text-sm bg-surface-800/40 rounded-lg px-3 py-2 cursor-pointer hover:bg-surface-800/70 transition-colors"
      onClick={() => onSelect(event)}
    >
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between sm:gap-3">
        <span className="flex items-start gap-2 text-surface-100 font-medium min-w-0">
          <span className={`w-2.5 h-2.5 mt-1.5 rounded-full shrink-0 ${dotClass}`} />
          <span className="min-w-0 break-words">{event.title}</span>
        </span>

        <div className="flex flex-wrap items-center gap-x-3 gap-y-1.5 sm:shrink-0 sm:flex-nowrap">
          <span className={`px-2 py-1 rounded-full text-xs font-medium whitespace-nowrap ${badgeClass}`}>
            {label}
          </span>
          <span className="text-surface-400 text-xs whitespace-nowrap">
            {format(parseCalendarDate(event.startDate), 'dd.MM')}
            {event.isMultiDay && <> - {format(parseCalendarDate(event.endDate), 'dd.MM')}</>}
          </span>
          <span className={`ml-auto sm:ml-0 px-3 py-1 text-xs font-medium rounded whitespace-nowrap ${status.cls}`}>
            {status.text}
          </span>
        </div>
      </div>
      {event.courseId && (
        <Link
          to={`/kursy/${event.courseId}`}
          state={{ returnTo: courseReturnTo }}
          onClick={(e) => e.stopPropagation()}
          className="mt-1.5 flex items-center gap-1 text-xs text-primary-400 hover:text-primary-300 transition-colors"
        >
          <ExternalLink className="w-3 h-3" />
          {t('event.courseDetails')}
        </Link>
      )}
    </div>
  )
}
