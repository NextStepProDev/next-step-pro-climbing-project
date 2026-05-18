import { useState, useRef, useEffect } from 'react'
import { CalendarPlus } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { buildGoogleCalendarUrl, downloadIcs } from '../../utils/calendarLinks'

interface AddToCalendarButtonProps {
  title: string
  date: string
  startTime?: string | null
  endTime?: string | null
  location?: string | null
  description?: string | null
}

export function AddToCalendarButton(props: AddToCalendarButtonProps) {
  const { t } = useTranslation('common')
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const handler = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [open])

  const event = {
    title: props.title,
    date: props.date,
    startTime: props.startTime,
    endTime: props.endTime,
    location: props.location,
    description: props.description,
  }

  return (
    <div className="relative" ref={ref}>
      <button
        onClick={() => setOpen(!open)}
        className="inline-flex items-center gap-1.5 text-sm text-primary-400 hover:text-primary-300 transition-colors"
      >
        <CalendarPlus className="w-4 h-4" />
        {t('calendarAction.addToCalendar')}
      </button>

      {open && (
        <div className="absolute bottom-full mb-2 left-0 bg-surface-800 border border-surface-700 rounded-lg shadow-lg py-1 z-50 min-w-[180px]">
          <a
            href={buildGoogleCalendarUrl(event)}
            target="_blank"
            rel="noopener noreferrer"
            onClick={() => setOpen(false)}
            className="block px-4 py-2 text-sm text-surface-200 hover:bg-surface-700 transition-colors"
          >
            Google Calendar
          </a>
          <button
            onClick={() => { downloadIcs(event); setOpen(false) }}
            className="block w-full text-left px-4 py-2 text-sm text-surface-200 hover:bg-surface-700 transition-colors"
          >
            {t('calendarAction.downloadIcs')}
          </button>
        </div>
      )}
    </div>
  )
}
