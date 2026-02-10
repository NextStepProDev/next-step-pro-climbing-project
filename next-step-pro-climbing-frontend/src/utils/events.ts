import type { EventSummary } from "../types";

export function formatAvailability(event: EventSummary) {
  if (!event.enrollmentOpen) {
    return { label: "Zapisy zamknięte", badgeClass: "bg-dark-700 text-dark-400" };
  }

  const free = event.maxParticipants - event.currentParticipants;

  if (free === 0) return { label: "Brak miejsc", badgeClass: "bg-amber-500/10 text-amber-400" };
  if (free === 1) return { label: "1 miejsce wolne", badgeClass: "bg-primary-500/10 text-primary-400" };
  return { label: `${free}/${event.maxParticipants} wolne`, badgeClass: "bg-primary-500/10 text-primary-400" };
}