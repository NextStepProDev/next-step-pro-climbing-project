import { useTranslation } from 'react-i18next'
import { format } from 'date-fns'
import { CalendarDays, GraduationCap, Mail } from 'lucide-react'
import { parseCalendarDate } from '../../utils/calendarDate'
import type { ContactContext, ContactContextType } from '../../types'

interface ContextChipProps {
  context?: ContactContext
  /** The stored form, as a message carries it. Either this or `context`. */
  stored?: { contextType: ContactContextType; courseTitle: string | null; date: string | null }
  compact?: boolean
}

/**
 * "About: …" — what a message is about. Shown above the form before sending, and on every
 * inbound message after, in both the person's conversation and the team's.
 */
export function ContextChip({ context, stored, compact = false }: ContextChipProps) {
  const { t } = useTranslation('common')
  const type = context?.type ?? stored?.contextType ?? 'GENERAL'
  const courseTitle = context?.type === 'COURSE' ? context.courseTitle : stored?.courseTitle ?? null
  // The day is a calendar LABEL from the API, never a moment — parse it as one (calendarDate.ts).
  const date = context?.type === 'CALENDAR' ? context.date : stored?.date ?? null

  let icon = <Mail className="w-3.5 h-3.5 shrink-0" aria-hidden="true" />
  let label = t('contactForm.about.general')
  if (type === 'COURSE') {
    icon = <GraduationCap className="w-3.5 h-3.5 shrink-0" aria-hidden="true" />
    label = t('contactForm.about.course', { title: courseTitle ?? t('contactForm.about.courseRemoved') })
  } else if (type === 'CALENDAR') {
    icon = <CalendarDays className="w-3.5 h-3.5 shrink-0" aria-hidden="true" />
    label = date
      ? t('contactForm.about.calendar', { date: format(parseCalendarDate(date), 'dd.MM.yyyy') })
      : t('contactForm.about.calendarAny')
  }

  return (
    <p
      className={
        compact
          ? 'inline-flex max-w-full items-center gap-1.5 text-[11px] text-surface-500'
          : 'inline-flex max-w-full items-center gap-1.5 px-2.5 py-1 rounded-full bg-surface-800 border border-surface-700 text-xs text-surface-400'
      }
    >
      {icon}
      <span className="min-w-0 truncate">
        <span className="text-surface-500">{t('contactForm.about.prefix')}</span> {label}
      </span>
    </p>
  )
}
