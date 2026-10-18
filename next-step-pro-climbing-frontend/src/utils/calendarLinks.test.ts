import { describe, it, expect } from 'vitest'
import { buildIcsContent, buildGoogleCalendarUrl } from './calendarLinks'

const base = { title: 'Kurs wspinaczki', date: '2026-09-10', startTime: '17:00', endTime: '19:00' }

describe('calendar export', () => {
  it('should hand the calendar plain text, not the markers the site renders', () => {
    const url = buildGoogleCalendarUrl({ ...base, description: '## Co zabrać\n- **uprząż**' })
    // URLSearchParams encodes spaces as '+', which decodeURIComponent leaves alone
    const details = decodeURIComponent(url.split('details=')[1]).replace(/\+/g, ' ')
    expect(details).toBe('Co zabrać\n• uprząż')
  })

  // A raw newline emits a line that is not a property, and the calendar drops the whole event.
  it('should escape the line breaks a multi-line description always has', () => {
    const ics = buildIcsContent({ ...base, description: 'pierwsza\ndruga' })
    const description = ics.split('\r\n').find((l) => l.startsWith('DESCRIPTION:'))
    expect(description).toBe('DESCRIPTION:pierwsza\\ndruga')
  })

  it('should escape the separators ICS reserves', () => {
    const ics = buildIcsContent({ ...base, description: 'buty, kask; lina' })
    expect(ics).toContain('DESCRIPTION:buty\\, kask\\; lina')
  })

  it('should not let a bare carriage return start a property of its own', () => {
    const ics = buildIcsContent({ ...base, title: 'Trening\rATTENDEE:mailto:x@evil.example' })
    for (const line of ics.split('\r\n')) {
      expect(line).not.toMatch(/[\r\n]/)
      expect(line.startsWith('ATTENDEE')).toBe(false)
    }
  })

  it('should escape a title carrying a separator too', () => {
    const ics = buildIcsContent({ ...base, title: 'Kurs: skały, Jura' })
    expect(ics).toContain('SUMMARY:Kurs: skały\\, Jura')
  })

  it('should end an all-day entry the day after its last day, not on its first', () => {
    // DTEND = DTSTART was a zero-length entry, and every day of a course after the first was lost.
    const allDay = { title: 'Kurs', date: '2026-10-30', endDate: '2026-11-01' }

    const ics = buildIcsContent(allDay)
    expect(ics).toContain('DTSTART;VALUE=DATE:20261030')
    expect(ics).toContain('DTEND;VALUE=DATE:20261102')
    expect(buildGoogleCalendarUrl(allDay)).toContain('dates=20261030%2F20261102')

    const oneDay = buildIcsContent({ title: 'Dzień', date: '2026-12-31' })
    expect(oneDay).toContain('DTEND;VALUE=DATE:20270101')
  })

  it('should end a timed multi-day event on its last day', () => {
    const ics = buildIcsContent({ ...base, date: '2026-10-24', endDate: '2026-10-25' })
    expect(ics).toContain('DTSTART;TZID=Europe/Warsaw:20261024T170000')
    expect(ics).toContain('DTEND;TZID=Europe/Warsaw:20261025T190000')
  })

  it('should define the zone its times refer to, and only when there are times', () => {
    const timed = buildIcsContent(base)
    expect(timed).toContain('BEGIN:VTIMEZONE\r\nTZID:Europe/Warsaw')
    expect(timed.indexOf('END:VTIMEZONE')).toBeLessThan(timed.indexOf('BEGIN:VEVENT'))
    expect(buildIcsContent({ title: 'Kurs', date: '2026-10-30' })).not.toContain('VTIMEZONE')
  })
})
