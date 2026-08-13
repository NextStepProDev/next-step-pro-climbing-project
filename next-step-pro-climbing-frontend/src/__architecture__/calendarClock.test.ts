import { describe, it, expect } from 'vitest'
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join } from 'node:path'

/**
 * The calendar asks one clock: Europe/Warsaw, via utils/calendarDate.
 *
 * The backend stores a LocalDate + LocalTime meaning Polish wall clock and evaluates every
 * rule with `LocalDateTime.now(Europe/Warsaw)`. A browser answering the same questions with
 * the device clock agrees only while the device sits in Poland, so the bugs are invisible
 * here and total for an athlete abroad: `new Date('2026-07-13')` is UTC midnight, i.e. the
 * 12th west of Greenwich, which shifted the week grid two days and made "next week" write
 * back the anchor already in the URL (the arrow did nothing at all); `new Date()` as "today"
 * put the completion button eight hours away from the moment the server would accept it.
 *
 * This is the frontend twin of the backend's bare-`now()` gate. Both shapes are banned here
 * because both look completely ordinary at the call site — that is why they survived review.
 *
 * Instants (createdAt, completedAt, expiresAt, confirmationDeadline, joinedAt, notifiedAt,
 * publishedAt, lastActivityAt) carry a zone and are CORRECT to render in device time, so
 * `new Date(instantField)` stays legal — only date-only fields and a bare `new Date()` trip
 * the gate.
 */

const SRC = join(__dirname, '..')

// Everything that draws or reasons about the booking/training calendar.
const WATCHED = [
  'components/calendar',
  'components/training',
  'pages/CalendarPage.tsx',
  'pages/EventPage.tsx',
  'pages/MyReservationsPage.tsx',
  'pages/admin/AdminEventsPanel.tsx',
  'pages/admin/AdminSlotsPanel.tsx',
  'pages/admin/AdminRequestsPanel.tsx',
  'pages/admin/AdminReservationsPanel.tsx',
  'pages/admin/AdminActivityPanel.tsx',
  'utils/events.ts',
  'utils/calendarLinks.ts',
]

// Fields the API serves as a bare 'yyyy-MM-dd'. Passing one to `new Date()` is the bug.
const DATE_ONLY_FIELDS = [
  'date', 'startDate', 'endDate', 'targetDate', 'requestedDate', 'slotDate',
  'eventStartDate', 'eventEndDate', 'firstActivityDate', 'measuredOn', 'trainingDate',
  'weekStart', 'anchorParam', 'dateParam',
]

/**
 * Cloning a Date (`new Date(someDate)`) is not the bug and stays allowed — the value already
 * carries a frame. Only string arguments are dangerous.
 */
const ALLOWED_BARE_NOW = new Set<string>([
  // DTSTAMP in an .ics file is "when this file was produced" — a real UTC instant, and the
  // one line in the export that is not a Polish wall clock reading.
  'utils/calendarLinks.ts',
])

function sourceFiles(): string[] {
  const out: string[] = []
  for (const entry of WATCHED) {
    const full = join(SRC, entry)
    if (statSync(full).isDirectory()) {
      for (const name of readdirSync(full)) {
        if (name.endsWith('.ts') || name.endsWith('.tsx')) {
          if (!name.endsWith('.test.ts') && !name.endsWith('.test.tsx')) out.push(join(full, name))
        }
      }
    } else {
      out.push(full)
    }
  }
  return out
}

function stripComments(source: string): string {
  return source.replace(/\/\*[\s\S]*?\*\//g, '').replace(/^\s*\/\/.*$/gm, '')
}

function relative(file: string): string {
  return file.slice(SRC.length + 1)
}

describe('calendar clock', () => {
  it('never asks the device what day it is', () => {
    const offenders: string[] = []

    for (const file of sourceFiles()) {
      const name = relative(file)
      if (ALLOWED_BARE_NOW.has(name)) continue
      const source = stripComments(readFileSync(file, 'utf-8'))
      // `new Date()` with no arguments — "now" according to the browser
      const hits = source.match(/new Date\(\s*\)/g) ?? []
      if (hits.length > 0) offenders.push(`${name}: ${hits.length}x new Date()`)
    }

    expect(
      offenders,
      'Use nowInWarsaw() / todayInWarsaw() from utils/calendarDate — the server decides ' +
        'these questions in Europe/Warsaw, so the device clock gives a different answer abroad',
    ).toEqual([])
  })

  it('never parses a date-only field with new Date()', () => {
    const offenders: string[] = []
    const pattern = new RegExp(
      String.raw`new Date\(\s*(?:\`[^\`]*\$\{)?[\w.?![\]]*\b(?:${DATE_ONLY_FIELDS.join('|')})\b`,
      'g',
    )

    for (const file of sourceFiles()) {
      const source = stripComments(readFileSync(file, 'utf-8'))
      for (const hit of source.match(pattern) ?? []) {
        offenders.push(`${relative(file)}: ${hit.trim()}`)
      }
    }

    expect(
      offenders,
      'Use parseCalendarDate() / parseCalendarDateTime() from utils/calendarDate — a ' +
        "'yyyy-MM-dd' from the API is a calendar label, and new Date() reads it as UTC midnight",
    ).toEqual([])
  })

  it('exports the calendar time zone from one place', () => {
    const source = readFileSync(join(SRC, 'utils', 'calendarDate.ts'), 'utf-8')
    expect(source).toContain("APP_TIME_ZONE = 'Europe/Warsaw'")

    // A hardcoded zone anywhere else is a second source of truth waiting to drift.
    const offenders = sourceFiles().filter((file) =>
      stripComments(readFileSync(file, 'utf-8')).includes("'Europe/Warsaw'"),
    )
    expect(offenders.map(relative)).toEqual([])
  })
})
