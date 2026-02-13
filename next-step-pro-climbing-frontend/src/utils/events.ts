import type { EventSummary } from "../types";

export const EVENT_ACCENT_COLORS = [
  { text: 'text-primary-400', border: 'border-primary-500/30', bg: 'bg-primary-500/5', hoverBorder: 'hover:border-primary-500', dot: 'bg-primary-400' },
  { text: 'text-emerald-400', border: 'border-emerald-500/30', bg: 'bg-emerald-500/5', hoverBorder: 'hover:border-emerald-500', dot: 'bg-emerald-400' },
  { text: 'text-violet-400', border: 'border-violet-500/30', bg: 'bg-violet-500/5', hoverBorder: 'hover:border-violet-500', dot: 'bg-violet-400' },
  { text: 'text-teal-400', border: 'border-teal-500/30', bg: 'bg-teal-500/5', hoverBorder: 'hover:border-teal-500', dot: 'bg-teal-400' },
  { text: 'text-cyan-400', border: 'border-cyan-500/30', bg: 'bg-cyan-500/5', hoverBorder: 'hover:border-cyan-500', dot: 'bg-cyan-400' },
  { text: 'text-orange-400', border: 'border-orange-500/30', bg: 'bg-orange-500/5', hoverBorder: 'hover:border-orange-500', dot: 'bg-orange-400' },
] as const;

export function getEventColor(index: number) {
  return EVENT_ACCENT_COLORS[index % EVENT_ACCENT_COLORS.length];
}

export function buildEventColorMap(events: { id: string }[]) {
  const map = new Map<string, number>();
  events.forEach((e, i) => map.set(e.id, i));
  return map;
}

export function pluralizeTraining(n: number): string {
  if (n === 1) return `${n} trening`;
  const mod10 = n % 10;
  const mod100 = n % 100;
  if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return `${n} treningi`;
  return `${n} treningów`;
}

export function formatAvailability(event: EventSummary) {
  if (!event.enrollmentOpen) {
    return { label: "Zapisy zamknięte", badgeClass: "bg-dark-700 text-dark-400" };
  }

  const free = event.maxParticipants - event.currentParticipants;

  if (free === 0) return { label: "Brak miejsc", badgeClass: "bg-amber-500/10 text-amber-400" };
  if (free === 1) return { label: "1 miejsce wolne", badgeClass: "bg-primary-500/10 text-primary-400" };
  return { label: `${free}/${event.maxParticipants} wolne`, badgeClass: "bg-primary-500/10 text-primary-400" };
}