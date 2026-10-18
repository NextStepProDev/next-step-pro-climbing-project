import { addDays, format } from 'date-fns'
import { APP_TIME_ZONE, parseCalendarDate } from './calendarDate'
import { toPlainText } from './renderRichText'

// Every time here is a Polish wall clock reading, never an instant, so the export must name
// that zone outright. Stamping the device's zone told an athlete abroad's calendar app that
// a 17:00 session in Poland was 17:00 where they happen to be standing.

interface CalendarEvent {
  title: string
  date: string
  /** Last day of a multi-day event; absent for one day. */
  endDate?: string | null
  startTime?: string | null
  endTime?: string | null
  location?: string | null
  description?: string | null
}

function formatDateTime(date: string, time: string): string {
  // ICS and Google both want HHMMSS; an 'HH:mm' time alone gave 'T1700', which neither reads.
  return date.replace(/-/g, '') + 'T' + (time.replace(/:/g, '') + '00').slice(0, 6)
}

function formatDateOnly(date: string): string {
  return date.replace(/-/g, '')
}

function lastDay(event: CalendarEvent): string {
  return event.endDate ?? event.date
}

/**
 * An all-day end is EXCLUSIVE (RFC 5545, and Google's `dates=` the same): the day after the last
 * one. Writing the start date there made a zero-length entry — some apps drop it, the rest draw
 * nothing — and every day of a multi-day course after the first was lost.
 */
function dayAfterLast(event: CalendarEvent): string {
  return format(addDays(parseCalendarDate(lastDay(event)), 1), 'yyyyMMdd')
}

/** Mirror of the backend's CalendarUtils.WARSAW_VTIMEZONE — see there for why a file needs it. */
const WARSAW_VTIMEZONE = [
  'BEGIN:VTIMEZONE',
  `TZID:${APP_TIME_ZONE}`,
  'BEGIN:DAYLIGHT',
  'TZOFFSETFROM:+0100',
  'TZOFFSETTO:+0200',
  'TZNAME:CEST',
  'DTSTART:19700329T020000',
  'RRULE:FREQ=YEARLY;BYMONTH=3;BYDAY=-1SU',
  'END:DAYLIGHT',
  'BEGIN:STANDARD',
  'TZOFFSETFROM:+0200',
  'TZOFFSETTO:+0100',
  'TZNAME:CET',
  'DTSTART:19701025T030000',
  'RRULE:FREQ=YEARLY;BYMONTH=10;BYDAY=-1SU',
  'END:STANDARD',
  'END:VTIMEZONE',
]

/**
 * An event description is written in the pseudo-markdown the site renders, and a calendar app
 * renders none of it — so the markers that structure the page on screen are exactly what would
 * show up as "## Co zabrać" on somebody's phone.
 */
function describe(description: string): string {
  return toPlainText(description)
}

/**
 * RFC 5545 escaping. Without it a description containing a newline — which every multi-line one
 * does — emits a bare line that is not a property, and the calendar app drops the event or
 * refuses the file. Commas and semicolons are value separators and need the same treatment.
 */
function escapeIcs(value: string): string {
  return value
    .replace(/\\/g, '\\\\')
    .replace(/,/g, '\\,')
    .replace(/;/g, '\\;')
    // A bare CR as well: many parsers end a content line on it, so it could start a property
    .replace(/\r\n|\r|\n/g, '\\n')
}

export function buildGoogleCalendarUrl(event: CalendarEvent): string {
  const params = new URLSearchParams()
  params.set('action', 'TEMPLATE')
  params.set('text', event.title)

  if (event.startTime && event.endTime) {
    params.set('dates', `${formatDateTime(event.date, event.startTime)}/${formatDateTime(lastDay(event), event.endTime)}`)
    params.set('ctz', APP_TIME_ZONE)
  } else {
    params.set('dates', `${formatDateOnly(event.date)}/${dayAfterLast(event)}`)
  }

  if (event.location) params.set('location', event.location)
  // URLSearchParams handles the URL encoding; only the markers have to go.
  if (event.description) params.set('details', describe(event.description))

  return `https://calendar.google.com/calendar/render?${params.toString()}`
}

export function buildIcsContent(event: CalendarEvent): string {
  const now = new Date().toISOString().replace(/[-:]/g, '').replace(/\.\d{3}/, '')
  const uid = `${Date.now()}@nextsteppro.pl`

  let dtStart: string
  let dtEnd: string

  const timed = Boolean(event.startTime && event.endTime)
  if (event.startTime && event.endTime) {
    dtStart = `DTSTART;TZID=${APP_TIME_ZONE}:${formatDateTime(event.date, event.startTime)}`
    dtEnd = `DTEND;TZID=${APP_TIME_ZONE}:${formatDateTime(lastDay(event), event.endTime)}`
  } else {
    dtStart = `DTSTART;VALUE=DATE:${formatDateOnly(event.date)}`
    dtEnd = `DTEND;VALUE=DATE:${dayAfterLast(event)}`
  }

  const lines = [
    'BEGIN:VCALENDAR',
    'VERSION:2.0',
    'PRODID:-//Next Step Pro Climbing//EN',
    ...(timed ? WARSAW_VTIMEZONE : []),
    'BEGIN:VEVENT',
    `UID:${uid}`,
    `DTSTAMP:${now}`,
    dtStart,
    dtEnd,
    `SUMMARY:${escapeIcs(event.title)}`,
  ]

  if (event.location) lines.push(`LOCATION:${escapeIcs(event.location)}`)
  if (event.description) lines.push(`DESCRIPTION:${escapeIcs(describe(event.description))}`)

  lines.push('END:VEVENT', 'END:VCALENDAR')

  return lines.join('\r\n')
}

export function downloadIcs(event: CalendarEvent) {
  const content = buildIcsContent(event)
  const blob = new Blob([content], { type: 'text/calendar;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${event.title.replace(/[^a-zA-Z0-9]/g, '_')}.ics`
  a.click()
  URL.revokeObjectURL(url)
}
