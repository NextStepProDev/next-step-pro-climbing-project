import { format } from 'date-fns'
import type { Locale } from 'date-fns'
import i18n from "../i18n";
import { parseCalendarDate } from './calendarDate'
import type { EventSummary } from "../types";

type EventAccentColor = {
  text: string; border: string; bg: string; hoverBorder: string; dot: string;
  barBg: string; barBorder: string; barText: string; barHover: string;
}

export type EventColorMap = Map<string, EventAccentColor>

const EVENT_TYPE_COLORS: Record<string, EventAccentColor> = {
  COURSE: {
    text: 'text-primary-400', border: 'border-primary-500/30', bg: 'bg-primary-500/5',
    hoverBorder: 'hover:border-primary-500', dot: 'bg-primary-400',
    barBg: 'bg-primary-600/40', barBorder: 'border-primary-500/40',
    barText: 'text-primary-300', barHover: 'hover:bg-primary-600/50',
  },
  TRAINING: {
    text: 'text-orange-400', border: 'border-orange-500/30', bg: 'bg-orange-500/5',
    hoverBorder: 'hover:border-orange-500', dot: 'bg-orange-400',
    barBg: 'bg-orange-600/40', barBorder: 'border-orange-500/40',
    barText: 'text-orange-300', barHover: 'hover:bg-orange-600/50',
  },
  WORKSHOP: {
    text: 'text-green-400', border: 'border-green-500/30', bg: 'bg-green-500/5',
    hoverBorder: 'hover:border-green-500', dot: 'bg-green-400',
    barBg: 'bg-green-600/40', barBorder: 'border-green-500/40',
    barText: 'text-green-300', barHover: 'hover:bg-green-600/50',
  },
  CONTACT_DAY: {
    text: 'text-indigo-400', border: 'border-indigo-500/30', bg: 'bg-indigo-500/5',
    hoverBorder: 'hover:border-indigo-500', dot: 'bg-indigo-400',
    barBg: 'bg-indigo-600/40', barBorder: 'border-indigo-500/40',
    barText: 'text-indigo-300', barHover: 'hover:bg-indigo-600/50',
  },
  FULL: {
    text: 'text-amber-400', border: 'border-amber-500/30', bg: 'bg-amber-500/5',
    hoverBorder: 'hover:border-amber-500', dot: 'bg-amber-400',
    barBg: 'bg-amber-600/40', barBorder: 'border-amber-500/40',
    barText: 'text-amber-300', barHover: 'hover:bg-amber-600/50',
  },
  UNAVAILABLE: {
    text: 'text-slate-400', border: 'border-slate-500/30', bg: 'bg-slate-500/5',
    hoverBorder: 'hover:border-slate-500', dot: 'bg-slate-400',
    barBg: 'bg-slate-600/40', barBorder: 'border-slate-500/40',
    barText: 'text-slate-300', barHover: 'hover:bg-slate-600/50',
  },
};

// Palette for non-full events — avoids amber (full) and primary blue (available slots)
const INDEX_PALETTE: EventAccentColor[] = [
  { text: 'text-indigo-400', border: 'border-indigo-500/30', bg: 'bg-indigo-500/5', hoverBorder: 'hover:border-indigo-500', dot: 'bg-indigo-400', barBg: 'bg-indigo-600/40', barBorder: 'border-indigo-500/40', barText: 'text-indigo-300', barHover: 'hover:bg-indigo-600/50' },
  { text: 'text-sky-400', border: 'border-sky-500/30', bg: 'bg-sky-500/5', hoverBorder: 'hover:border-sky-500', dot: 'bg-sky-400', barBg: 'bg-sky-600/40', barBorder: 'border-sky-500/40', barText: 'text-sky-300', barHover: 'hover:bg-sky-600/50' },
  { text: 'text-green-400', border: 'border-green-500/30', bg: 'bg-green-500/5', hoverBorder: 'hover:border-green-500', dot: 'bg-green-400', barBg: 'bg-green-600/40', barBorder: 'border-green-500/40', barText: 'text-green-300', barHover: 'hover:bg-green-600/50' },
  { text: 'text-orange-400', border: 'border-orange-500/30', bg: 'bg-orange-500/5', hoverBorder: 'hover:border-orange-500', dot: 'bg-orange-400', barBg: 'bg-orange-600/40', barBorder: 'border-orange-500/40', barText: 'text-orange-300', barHover: 'hover:bg-orange-600/50' },
  { text: 'text-pink-400', border: 'border-pink-500/30', bg: 'bg-pink-500/5', hoverBorder: 'hover:border-pink-500', dot: 'bg-pink-400', barBg: 'bg-pink-600/40', barBorder: 'border-pink-500/40', barText: 'text-pink-300', barHover: 'hover:bg-pink-600/50' },
  { text: 'text-teal-400', border: 'border-teal-500/30', bg: 'bg-teal-500/5', hoverBorder: 'hover:border-teal-500', dot: 'bg-teal-400', barBg: 'bg-teal-600/40', barBorder: 'border-teal-500/40', barText: 'text-teal-300', barHover: 'hover:bg-teal-600/50' },
]

export function getEventColorByType(eventType: string): EventAccentColor {
  return EVENT_TYPE_COLORS[eventType] ?? EVENT_TYPE_COLORS.COURSE;
}

/** Stable color for an event — CONTACT_DAY → indigo, full → amber, others → hash-based from ID. */
export function getEventColorByIndex(eventId: string, eventType: string, isFull: boolean): EventAccentColor {
  if (eventType === 'UNAVAILABLE') return EVENT_TYPE_COLORS.UNAVAILABLE;
  if (eventType === 'CONTACT_DAY') return EVENT_TYPE_COLORS.CONTACT_DAY;
  if (isFull) return EVENT_TYPE_COLORS.FULL;
  const hash = eventId.split('').reduce((acc, c) => acc + c.charCodeAt(0), 0);
  return INDEX_PALETTE[hash % INDEX_PALETTE.length];
}

/** Graph-coloring map — overlapping events (gap ≤ 1 day) get different colors.
 *  Use the returned map as single source of truth for a given event set. */
export function buildEventColorMap(events: EventSummary[]): Map<string, EventAccentColor> {
  const map = new Map<string, EventAccentColor>()

  const paletteEvents = events.filter(
    e => e.eventType !== 'CONTACT_DAY' && e.eventType !== 'UNAVAILABLE' && e.currentParticipants < e.maxParticipants
  )

  const sorted = [...paletteEvents].sort(
    (a, b) => parseCalendarDate(a.startDate).getTime() - parseCalendarDate(b.startDate).getTime()
  )

  sorted.forEach(event => {
    const eStart = parseCalendarDate(event.startDate).getTime()
    const eEnd = parseCalendarDate(event.endDate).getTime()
    const DAY = 86_400_000

    const usedIndices = new Set<number>()
    sorted.forEach(other => {
      if (other.id === event.id) return
      const oStart = parseCalendarDate(other.startDate).getTime()
      const oEnd = parseCalendarDate(other.endDate).getTime()
      if (eEnd + DAY >= oStart && oEnd + DAY >= eStart) {
        const assigned = map.get(other.id)
        if (assigned) {
          const idx = INDEX_PALETTE.indexOf(assigned)
          if (idx !== -1) usedIndices.add(idx)
        }
      }
    })

    let idx = INDEX_PALETTE.findIndex((_, i) => !usedIndices.has(i))
    if (idx === -1) {
      const hash = event.id.split('').reduce((acc, c) => acc + c.charCodeAt(0), 0)
      idx = hash % INDEX_PALETTE.length
    }
    map.set(event.id, INDEX_PALETTE[idx])
  })

  events.forEach(e => {
    if (!map.has(e.id)) {
      if (e.eventType === 'UNAVAILABLE') map.set(e.id, EVENT_TYPE_COLORS.UNAVAILABLE)
      else if (e.eventType === 'CONTACT_DAY') map.set(e.id, EVENT_TYPE_COLORS.CONTACT_DAY)
      else map.set(e.id, EVENT_TYPE_COLORS.FULL)
    }
  })

  return map
}

/** The one "when" line for an event: the day (or the span) plus whatever hours the entry carries.
 *
 *  On a multi-day event each clock stays beside its OWN date and the two are never joined into a
 *  range. The backend deliberately allows an end earlier than the start once the dates differ
 *  (`CreateEventRequest.isSameDayTimeRangeValid`) — Friday 18:00 to Sunday 08:00 is a weekend, not
 *  an inverted window — so "18:00–08:00" would describe a single day instead of the trip.
 *
 *  The all-day label is only added on a single day: a bare date range already reads as whole days.
 *
 *  `allDayLabel` is passed in rather than read from i18n here so the function stays pure; both
 *  call sites already hold the `common` namespace. */
export function formatEventWhen(
  event: Pick<EventSummary, 'startDate' | 'endDate' | 'startTime' | 'endTime' | 'isMultiDay'>,
  opts: { dateFormat: string; allDayLabel: string; locale?: Locale },
): string {
  const { dateFormat, allDayLabel, locale } = opts
  // A 'yyyy-MM-dd' from the API is a label, not a moment — parseCalendarDate, never new Date().
  const startDate = format(parseCalendarDate(event.startDate), dateFormat, { locale })
  const endDate = format(parseCalendarDate(event.endDate), dateFormat, { locale })
  // The wire carries both "18:00" and "18:00:00"; the rest of the app slices the same way.
  const startTime = event.startTime ? event.startTime.slice(0, 5) : null
  const endTime = event.endTime ? event.endTime.slice(0, 5) : null

  if (event.isMultiDay) {
    return `${startDate}${startTime ? ` ${startTime}` : ''} – ${endDate}${endTime ? ` ${endTime}` : ''}`
  }
  if (startTime && endTime) return `${startDate} · ${startTime}–${endTime}`
  // Exactly one clock is an API-only shape: the admin form moves both together, but the columns
  // are nullable with no CHECK. Say only the day — "all day" would be a plain lie about an entry
  // that does carry an hour, and a lone "18:00" cannot say which end of the day it is.
  if (startTime || endTime) return startDate
  return `${startDate} · ${allDayLabel}`
}

export function pluralizeTraining(n: number): string {
  return i18n.t('training', { count: n, ns: 'calendar' });
}

export function formatAvailability(event: EventSummary) {
  if (event.eventType === 'UNAVAILABLE') {
    return { label: i18n.t('availability.unavailable', { ns: 'calendar' }), badgeClass: "bg-slate-500/10 text-slate-300" };
  }
  if (!event.enrollmentOpen) {
    return { label: i18n.t('availability.closed', { ns: 'calendar' }), badgeClass: "bg-surface-700 text-surface-400" };
  }

  // Seats invitation-held for OTHER people are not free for this viewer — otherwise the label
  // would show "1 free seat" where a walk-in cannot book (consistent with the modal).
  const reservedForOthers = Math.max(0, (event.reservedSeats ?? 0) - (event.isReservedForUser ? 1 : 0));
  const free = Math.max(0, event.maxParticipants - event.currentParticipants - reservedForOthers);

  if (free === 0) return { label: i18n.t('availability.noSpots', { ns: 'calendar' }), badgeClass: "bg-amber-500/10 text-amber-400" };
  if (free === 1) return { label: i18n.t('availability.oneSpot', { ns: 'calendar' }), badgeClass: "bg-green-500/15 text-green-300" };
  return { label: i18n.t('availability.freeSpots', { free, max: event.maxParticipants, ns: 'calendar' }), badgeClass: "bg-green-500/15 text-green-300" };
}
