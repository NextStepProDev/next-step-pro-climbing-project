import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { format } from 'date-fns'
import { Calendar, ArrowRight, Clock, ImageIcon } from 'lucide-react'
import type { CourseDetail, CourseEvent } from '../../types'
import { renderRichText } from '../../utils/renderRichText'
import { useDateLocale } from '../../utils/dateFnsLocale'
import clsx from 'clsx'

export function CourseContentBlocks({ detail }: { detail: CourseDetail }) {
  if (detail.blocks.length === 0) {
    return null
  }

  return (
    <div className="space-y-6">
      {detail.blocks.map((block) => {
        if (block.blockType === 'TEXT') {
          return (
            <div
              key={block.id}
              className="text-surface-200 leading-relaxed"
              dangerouslySetInnerHTML={{ __html: renderRichText(block.content ?? '') }}
            />
          )
        }

        if (block.blockType === 'IMAGE') {
          return (
            <figure key={block.id}>
              {block.imageUrl ? (
                <img
                  src={block.imageUrl}
                  alt={block.caption ?? ''}
                  loading="lazy"
                  className="block max-w-full max-h-[70vh] rounded-lg mx-auto"
                  onError={(e) => {
                    const el = e.currentTarget
                    el.style.display = 'none'
                    const fallback = el.nextElementSibling as HTMLElement | null
                    if (fallback) fallback.style.display = 'flex'
                  }}
                />
              ) : null}
              <div
                className="w-full h-32 bg-surface-700 rounded-lg items-center justify-center hidden"
              >
                <ImageIcon className="h-8 w-8 text-surface-500" />
              </div>
              {block.caption && (
                <figcaption className="text-sm text-surface-400 mt-2 text-center italic">
                  {block.caption}
                </figcaption>
              )}
            </figure>
          )
        }

        return null
      })}
    </div>
  )
}

export function CourseEventsList({ events }: { events: CourseEvent[] | undefined }) {
  const { t } = useTranslation('common')

  return (
    <div>
      <h3 className="text-base font-semibold text-surface-200 mb-3 flex items-center gap-2">
        <Calendar className="h-4 w-4 text-primary-400" />
        {t('courses.availableDates')}
      </h3>

      {!events || events.length === 0 ? (
        <p className="text-sm text-surface-500">{t('courses.noDates')}</p>
      ) : (
        <div className="space-y-2">
          {events.map((event) => (
            <CourseEventRow key={event.eventId} event={event} />
          ))}
        </div>
      )}
    </div>
  )
}

export function CourseEventRow({ event }: { event: CourseEvent }) {
  const { t } = useTranslation('common')
  const locale = useDateLocale()

  const startDate = format(new Date(event.startDate), 'd MMMM yyyy', { locale })
  const endDate = event.endDate !== event.startDate
    ? format(new Date(event.endDate), 'd MMMM yyyy', { locale })
    : null

  const statusColor = event.status === 'AVAILABLE'
    ? 'text-green-400'
    : event.status === 'FULL'
    ? 'text-rose-400'
    : 'text-amber-400'

  const spotsLabel = event.status === 'FULL'
    ? t('courses.spotsFull')
    : t(event.availableSpots === 1 ? 'courses.spotsOne' : 'courses.spots', { count: event.availableSpots })

  return (
    <div className="flex items-center justify-between bg-surface-900 rounded-lg px-4 py-3 gap-4">
      <div className="min-w-0">
        <p className="text-sm text-surface-200 capitalize">
          {endDate ? `${startDate} – ${endDate}` : startDate}
        </p>
        {event.startTime && event.endTime && (
          <p className="text-xs text-surface-400 flex items-center gap-1">
            <Clock className="h-3 w-3" />
            {event.startTime.slice(0, 5)} – {event.endTime.slice(0, 5)}
          </p>
        )}
      </div>
      <div className="flex items-center gap-3 shrink-0">
        <span className={clsx('text-xs font-medium', statusColor)}>{spotsLabel}</span>
        <Link
          to={`/calendar?date=${event.startDate}`}
          className="flex items-center gap-1 text-xs text-primary-400 hover:text-primary-300 transition-colors"
        >
          {event.status === 'FULL' ? t('courses.joinWaitlist') : t('courses.goToCalendar')}
          <ArrowRight className="h-3 w-3" />
        </Link>
      </div>
    </div>
  )
}
