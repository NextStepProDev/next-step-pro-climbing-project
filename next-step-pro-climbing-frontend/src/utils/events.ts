import i18n from "../i18n";
import type { EventSummary } from "../types";

type EventAccentColor = {
  text: string; border: string; bg: string; hoverBorder: string; dot: string;
  barBg: string; barBorder: string; barText: string; barHover: string;
}

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
    text: 'text-emerald-400', border: 'border-emerald-500/30', bg: 'bg-emerald-500/5',
    hoverBorder: 'hover:border-emerald-500', dot: 'bg-emerald-400',
    barBg: 'bg-emerald-600/40', barBorder: 'border-emerald-500/40',
    barText: 'text-emerald-300', barHover: 'hover:bg-emerald-600/50',
  },
  CONTACT_DAY: {
    text: 'text-violet-400', border: 'border-violet-500/30', bg: 'bg-violet-500/5',
    hoverBorder: 'hover:border-violet-500', dot: 'bg-violet-400',
    barBg: 'bg-violet-600/40', barBorder: 'border-violet-500/40',
    barText: 'text-violet-300', barHover: 'hover:bg-violet-600/50',
  },
  FULL: {
    text: 'text-amber-400', border: 'border-amber-500/30', bg: 'bg-amber-500/5',
    hoverBorder: 'hover:border-amber-500', dot: 'bg-amber-400',
    barBg: 'bg-amber-600/40', barBorder: 'border-amber-500/40',
    barText: 'text-amber-300', barHover: 'hover:bg-amber-600/50',
  },
};

// Palette for non-full events — avoids amber (full) and primary blue (available slots)
const INDEX_PALETTE: EventAccentColor[] = [
  { text: 'text-violet-400', border: 'border-violet-500/30', bg: 'bg-violet-500/5', hoverBorder: 'hover:border-violet-500', dot: 'bg-violet-400', barBg: 'bg-violet-600/40', barBorder: 'border-violet-500/40', barText: 'text-violet-300', barHover: 'hover:bg-violet-600/50' },
  { text: 'text-sky-400', border: 'border-sky-500/30', bg: 'bg-sky-500/5', hoverBorder: 'hover:border-sky-500', dot: 'bg-sky-400', barBg: 'bg-sky-600/40', barBorder: 'border-sky-500/40', barText: 'text-sky-300', barHover: 'hover:bg-sky-600/50' },
  { text: 'text-emerald-400', border: 'border-emerald-500/30', bg: 'bg-emerald-500/5', hoverBorder: 'hover:border-emerald-500', dot: 'bg-emerald-400', barBg: 'bg-emerald-600/40', barBorder: 'border-emerald-500/40', barText: 'text-emerald-300', barHover: 'hover:bg-emerald-600/50' },
  { text: 'text-orange-400', border: 'border-orange-500/30', bg: 'bg-orange-500/5', hoverBorder: 'hover:border-orange-500', dot: 'bg-orange-400', barBg: 'bg-orange-600/40', barBorder: 'border-orange-500/40', barText: 'text-orange-300', barHover: 'hover:bg-orange-600/50' },
  { text: 'text-pink-400', border: 'border-pink-500/30', bg: 'bg-pink-500/5', hoverBorder: 'hover:border-pink-500', dot: 'bg-pink-400', barBg: 'bg-pink-600/40', barBorder: 'border-pink-500/40', barText: 'text-pink-300', barHover: 'hover:bg-pink-600/50' },
  { text: 'text-teal-400', border: 'border-teal-500/30', bg: 'bg-teal-500/5', hoverBorder: 'hover:border-teal-500', dot: 'bg-teal-400', barBg: 'bg-teal-600/40', barBorder: 'border-teal-500/40', barText: 'text-teal-300', barHover: 'hover:bg-teal-600/50' },
]

export function getEventColorByType(eventType: string): EventAccentColor {
  return EVENT_TYPE_COLORS[eventType] ?? EVENT_TYPE_COLORS.COURSE;
}

export function getEventColorForDisplay(eventType: string, isFull: boolean): EventAccentColor {
  if (isFull && eventType !== 'CONTACT_DAY') {
    return EVENT_TYPE_COLORS.FULL; // amber — same as "full" slots
  }
  return getEventColorByType(eventType);
}

/** Use this when multiple events are shown together (day/week/month view).
 *  CONTACT_DAY keeps its violet. Full events get amber. Others get a stable color derived from event ID. */
export function getEventColorByIndex(eventId: string, eventType: string, isFull: boolean): EventAccentColor {
  if (eventType === 'CONTACT_DAY') return EVENT_TYPE_COLORS.CONTACT_DAY;
  if (isFull) return EVENT_TYPE_COLORS.FULL;
  const hash = eventId.split('').reduce((acc, c) => acc + c.charCodeAt(0), 0);
  return INDEX_PALETTE[hash % INDEX_PALETTE.length];
}

/** Conflict-aware color map for month view.
 *  Ensures adjacent/overlapping events (gap ≤ 1 day) get different colors. */
export function buildEventColorMap(events: EventSummary[]): Map<string, EventAccentColor> {
  const map = new Map<string, EventAccentColor>()

  const paletteEvents = events.filter(
    e => e.eventType !== 'CONTACT_DAY' && e.currentParticipants < e.maxParticipants
  )

  const sorted = [...paletteEvents].sort(
    (a, b) => new Date(a.startDate).getTime() - new Date(b.startDate).getTime()
  )

  sorted.forEach(event => {
    const eStart = new Date(event.startDate).getTime()
    const eEnd = new Date(event.endDate).getTime()
    const DAY = 86_400_000

    const usedIndices = new Set<number>()
    sorted.forEach(other => {
      if (other.id === event.id) return
      const oStart = new Date(other.startDate).getTime()
      const oEnd = new Date(other.endDate).getTime()
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
      if (e.eventType === 'CONTACT_DAY') map.set(e.id, EVENT_TYPE_COLORS.CONTACT_DAY)
      else map.set(e.id, EVENT_TYPE_COLORS.FULL)
    }
  })

  return map
}

export function pluralizeTraining(n: number): string {
  return i18n.t('training', { count: n, ns: 'calendar' });
}

export function formatAvailability(event: EventSummary) {
  if (!event.enrollmentOpen) {
    return { label: i18n.t('availability.closed', { ns: 'calendar' }), badgeClass: "bg-dark-700 text-dark-400" };
  }

  const free = event.maxParticipants - event.currentParticipants;

  if (free === 0) return { label: i18n.t('availability.noSpots', { ns: 'calendar' }), badgeClass: "bg-amber-500/10 text-amber-400" };
  if (free === 1) return { label: i18n.t('availability.oneSpot', { ns: 'calendar' }), badgeClass: "bg-primary-500/10 text-primary-400" };
  return { label: i18n.t('availability.freeSpots', { free, max: event.maxParticipants, ns: 'calendar' }), badgeClass: "bg-primary-500/10 text-primary-400" };
}
