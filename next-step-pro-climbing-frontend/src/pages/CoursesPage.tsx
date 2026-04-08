import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { format } from 'date-fns'
import { ChevronDown, BookOpen, ImageIcon, Calendar, ArrowRight, Clock } from 'lucide-react'
import { coursesApi, calendarApi } from '../api/client'
import type { CourseSummary, CourseDetail, CourseEvent } from '../types'
import { LoadingSpinner } from '../components/ui/LoadingSpinner'
import { QueryError } from '../components/ui/QueryError'
import { renderRichText } from '../utils/renderRichText'
import { useDateLocale } from '../utils/dateFnsLocale'
import clsx from 'clsx'

export function CoursesPage() {
  const { t } = useTranslation('common')

  const { data: courses, isLoading, error } = useQuery({
    queryKey: ['courses'],
    queryFn: () => coursesApi.getAll(),
    staleTime: 5 * 60 * 1000,
  })

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-24">
        <LoadingSpinner />
      </div>
    )
  }

  if (error) {
    return (
      <div className="max-w-3xl mx-auto px-4 py-12">
        <QueryError error={error} />
      </div>
    )
  }

  return (
    <div className="max-w-3xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
      <h1 className="text-3xl font-bold text-dark-100 mb-8">{t('courses.title')}</h1>

      {!courses || courses.length === 0 ? (
        <p className="text-dark-400 text-center py-12">{t('courses.noCourses')}</p>
      ) : (
        <div className="space-y-3">
          {courses.map((course) => (
            <CourseAccordionItem key={course.id} course={course} />
          ))}
        </div>
      )}
    </div>
  )
}

function CourseAccordionItem({ course }: { course: CourseSummary }) {
  const [isOpen, setIsOpen] = useState(false)

  const { data: detail, isLoading } = useQuery({
    queryKey: ['courses', course.id],
    queryFn: () => coursesApi.getById(course.id),
    enabled: isOpen,
    staleTime: 10 * 60 * 1000,
  })

  const { data: courseEvents } = useQuery({
    queryKey: ['courseEvents', course.id],
    queryFn: () => calendarApi.getCourseEvents(course.id),
    enabled: isOpen,
    staleTime: 5 * 60 * 1000,
  })

  return (
    <div className="bg-dark-800 border border-dark-700 rounded-lg overflow-hidden">
      <button
        onClick={() => setIsOpen(!isOpen)}
        className="w-full flex items-center gap-4 p-4 text-left hover:bg-dark-750 transition-colors"
        aria-expanded={isOpen}
      >
        {/* Miniaturka */}
        <div className="flex-shrink-0 w-20 h-20 bg-dark-700 rounded-lg overflow-hidden">
          {course.thumbnailUrl ? (
            <img
              src={course.thumbnailUrl}
              alt={course.title}
              className="w-full h-full object-contain"
              onError={(e) => { (e.currentTarget as HTMLImageElement).style.display = 'none' }}
            />
          ) : (
            <div className="w-full h-full flex items-center justify-center">
              <BookOpen className="h-8 w-8 text-dark-500" />
            </div>
          )}
        </div>

        {/* Tytuł + zajawka */}
        <div className="flex-1 min-w-0">
          <p className="font-semibold text-dark-100 text-lg leading-snug">{course.title}</p>
          {course.excerpt && (
            <p className="text-sm text-dark-400 mt-1 line-clamp-2">{course.excerpt}</p>
          )}
        </div>

        {/* Chevron */}
        <ChevronDown
          className={clsx(
            'flex-shrink-0 h-5 w-5 text-dark-400 transition-transform duration-200',
            isOpen && 'rotate-180'
          )}
        />
      </button>

      {/* Rozwinięta treść */}
      {isOpen && (
        <div className="border-t border-dark-700 px-6 py-6 space-y-8">
          {isLoading ? (
            <div className="flex justify-center py-8">
              <LoadingSpinner />
            </div>
          ) : detail ? (
            <CourseBlocks detail={detail} />
          ) : null}

          <CourseEventsList events={courseEvents} />
        </div>
      )}
    </div>
  )
}

function CourseEventsList({ events }: { events: CourseEvent[] | undefined }) {
  const { t } = useTranslation('common')

  return (
    <div>
      <h3 className="text-base font-semibold text-dark-200 mb-3 flex items-center gap-2">
        <Calendar className="h-4 w-4 text-primary-400" />
        {t('courses.availableDates')}
      </h3>

      {!events || events.length === 0 ? (
        <p className="text-sm text-dark-500">{t('courses.noDates')}</p>
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

function CourseEventRow({ event }: { event: CourseEvent }) {
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
    <div className="flex items-center justify-between bg-dark-900 rounded-lg px-4 py-3 gap-4">
      <div className="min-w-0">
        <p className="text-sm text-dark-200 capitalize">
          {endDate ? `${startDate} – ${endDate}` : startDate}
        </p>
        {event.startTime && event.endTime && (
          <p className="text-xs text-dark-400 flex items-center gap-1">
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
          {t('courses.goToCalendar')}
          <ArrowRight className="h-3 w-3" />
        </Link>
      </div>
    </div>
  )
}

function CourseBlocks({ detail }: { detail: CourseDetail }) {
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
              className="text-dark-200 leading-relaxed"
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
                  className="w-full rounded-lg object-cover"
                  onError={(e) => {
                    const el = e.currentTarget
                    el.style.display = 'none'
                    const fallback = el.nextElementSibling as HTMLElement | null
                    if (fallback) fallback.style.display = 'flex'
                  }}
                />
              ) : null}
              <div
                className="w-full h-32 bg-dark-700 rounded-lg items-center justify-center hidden"
              >
                <ImageIcon className="h-8 w-8 text-dark-500" />
              </div>
              {block.caption && (
                <figcaption className="text-sm text-dark-400 mt-2 text-center italic">
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
