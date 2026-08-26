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

  it('should escape a title carrying a separator too', () => {
    const ics = buildIcsContent({ ...base, title: 'Kurs: skały, Jura' })
    expect(ics).toContain('SUMMARY:Kurs: skały\\, Jura')
  })
})
