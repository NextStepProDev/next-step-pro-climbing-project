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
    text: 'text-amber-400', border: 'border-amber-500/30', bg: 'bg-amber-500/5',
    hoverBorder: 'hover:border-amber-500', dot: 'bg-amber-400',
    barBg: 'bg-amber-600/40', barBorder: 'border-amber-500/40',
    barText: 'text-amber-300', barHover: 'hover:bg-amber-600/50',
  },
  WORKSHOP: {
    text: 'text-emerald-400', border: 'border-emerald-500/30', bg: 'bg-emerald-500/5',
    hoverBorder: 'hover:border-emerald-500', dot: 'bg-emerald-400',
    barBg: 'bg-emerald-600/40', barBorder: 'border-emerald-500/40',
    barText: 'text-emerald-300', barHover: 'hover:bg-emerald-600/50',
  },
  CONTACT_DAY: {
    text: 'text-rose-400', border: 'border-rose-500/30', bg: 'bg-rose-500/5',
    hoverBorder: 'hover:border-rose-500', dot: 'bg-rose-400',
    barBg: 'bg-rose-600/40', barBorder: 'border-rose-500/40',
    barText: 'text-rose-300', barHover: 'hover:bg-rose-600/50',
  },
};

export function getEventColorByType(eventType: string): EventAccentColor {
  return EVENT_TYPE_COLORS[eventType] ?? EVENT_TYPE_COLORS.COURSE;
}

export function getEventColorForDisplay(eventType: string, isFull: boolean): EventAccentColor {
  if (isFull && eventType !== 'CONTACT_DAY') {
    return EVENT_TYPE_COLORS.TRAINING; // amber — same as "full" slots
  }
  return getEventColorByType(eventType);
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
