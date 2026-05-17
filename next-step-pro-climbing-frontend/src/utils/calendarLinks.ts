interface CalendarEvent {
  title: string
  date: string
  startTime?: string | null
  endTime?: string | null
  location?: string | null
  description?: string | null
}

function formatDateTime(date: string, time: string): string {
  return date.replace(/-/g, '') + 'T' + time.replace(/:/g, '').slice(0, 6)
}

function formatDateOnly(date: string): string {
  return date.replace(/-/g, '')
}

export function buildGoogleCalendarUrl(event: CalendarEvent): string {
  const params = new URLSearchParams()
  params.set('action', 'TEMPLATE')
  params.set('text', event.title)

  if (event.startTime && event.endTime) {
    params.set('dates', `${formatDateTime(event.date, event.startTime)}/${formatDateTime(event.date, event.endTime)}`)
    params.set('ctz', Intl.DateTimeFormat().resolvedOptions().timeZone)
  } else {
    params.set('dates', `${formatDateOnly(event.date)}/${formatDateOnly(event.date)}`)
  }

  if (event.location) params.set('location', event.location)
  if (event.description) params.set('details', event.description)

  return `https://calendar.google.com/calendar/render?${params.toString()}`
}

export function buildIcsContent(event: CalendarEvent): string {
  const now = new Date().toISOString().replace(/[-:]/g, '').replace(/\.\d{3}/, '')
  const uid = `${Date.now()}@nextsteppro.pl`

  let dtStart: string
  let dtEnd: string

  if (event.startTime && event.endTime) {
    const tz = Intl.DateTimeFormat().resolvedOptions().timeZone
    dtStart = `DTSTART;TZID=${tz}:${formatDateTime(event.date, event.startTime)}`
    dtEnd = `DTEND;TZID=${tz}:${formatDateTime(event.date, event.endTime)}`
  } else {
    dtStart = `DTSTART;VALUE=DATE:${formatDateOnly(event.date)}`
    dtEnd = `DTEND;VALUE=DATE:${formatDateOnly(event.date)}`
  }

  const lines = [
    'BEGIN:VCALENDAR',
    'VERSION:2.0',
    'PRODID:-//Next Step Pro Climbing//EN',
    'BEGIN:VEVENT',
    `UID:${uid}`,
    `DTSTAMP:${now}`,
    dtStart,
    dtEnd,
    `SUMMARY:${event.title}`,
  ]

  if (event.location) lines.push(`LOCATION:${event.location}`)
  if (event.description) lines.push(`DESCRIPTION:${event.description}`)

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
