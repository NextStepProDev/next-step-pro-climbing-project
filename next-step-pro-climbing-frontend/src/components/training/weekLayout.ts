import type { InvitationOverlayItem, PersonalTraining, ReservationOverlayItem } from '../../types'

// Same grid math as the public WeekCalendar (components/calendar/WeekCalendar.tsx) —
// copied constants, not the component: that one is welded to booking logic.
export const HOUR_HEIGHT = 40
export const START_HOUR = 7
export const END_HOUR = 23
export const TOTAL_HOURS = END_HOUR - START_HOUR

export function timeToMin(time: string): number {
  const [h, m] = time.split(':').map(Number)
  return h * 60 + m
}

/** Click position in a day column -> "HH:mm" snapped to 30 min, clamped to the grid. */
export function clickToTime(relY: number): string {
  const raw = Math.round(((relY / HOUR_HEIGHT) * 60) / 30) * 30
  const abs = START_HOUR * 60 + Math.max(0, Math.min(raw, TOTAL_HOURS * 60 - 30))
  return `${String(Math.floor(abs / 60)).padStart(2, '0')}:${String(abs % 60).padStart(2, '0')}`
}

export interface PositionedItem {
  key: string
  startMin: number
  endMin: number
  lane: number
  lanes: number
  clampedTop: boolean
  clampedBottom: boolean
  training?: PersonalTraining
  reservation?: ReservationOverlayItem
  invitation?: InvitationOverlayItem
}

function baseItem(key: string, startTime: string, endTime: string,
                  refs: { training?: PersonalTraining; reservation?: ReservationOverlayItem; invitation?: InvitationOverlayItem }): PositionedItem {
  const GRID_START = START_HOUR * 60
  const GRID_END = END_HOUR * 60
  const rawStart = timeToMin(startTime)
  const rawEnd = timeToMin(endTime)
  // Clamp fully inside the grid with a minimum 30-min visible height — an entry entirely
  // outside 7:00-23:00 pins to the nearest edge with a clamp arrow instead of overflowing
  const startMin = Math.min(Math.max(rawStart, GRID_START), GRID_END - 30)
  const endMin = Math.min(Math.max(rawEnd, startMin + 30), GRID_END)
  return {
    key,
    startMin,
    endMin,
    lane: 0,
    lanes: 1,
    clampedTop: rawStart < startMin,
    clampedBottom: rawEnd > endMin,
    ...refs,
  }
}

// Greedy lane assignment so overlapping blocks render side by side instead of stacking.
// Callers pass only timed entries (untimed trainings live in the all-day lane).
export function layoutDay(trainings: PersonalTraining[], reservations: ReservationOverlayItem[],
                          invitations: InvitationOverlayItem[]): PositionedItem[] {
  const items: PositionedItem[] = [
    ...trainings.map((t) => baseItem(`t-${t.id}`, t.startTime ?? '07:00', t.endTime ?? '08:00', { training: t })),
    ...reservations.map((r) => baseItem(`r-${r.id}`, r.startTime, r.endTime, { reservation: r })),
    // Only timed invites reach the grid; all-day invites are filtered out into the all-day lane.
    ...invitations.map((inv, i) => baseItem(`i-${i}-${inv.slotId ?? inv.eventId}`,
      inv.startTime ?? '07:00', inv.endTime ?? '08:00', { invitation: inv })),
  ].sort((a, b) => a.startMin - b.startMin || a.endMin - b.endMin)

  const laneEnds: number[] = []
  for (const item of items) {
    let lane = laneEnds.findIndex((end) => end <= item.startMin)
    if (lane === -1) {
      lane = laneEnds.length
      laneEnds.push(0)
    }
    laneEnds[lane] = item.endMin
    item.lane = lane
  }
  // Every overlapping cluster shares the max lane count for a stable side-by-side split
  const lanes = laneEnds.length
  items.forEach((i) => { i.lanes = lanes })
  return items
}

export interface DayEntries {
  /** Blocks placed in the hour grid, already laid out into lanes */
  timed: PositionedItem[]
  /** Untimed (V72 "all-day") trainings, pinned above the grid */
  allDayTrainings: PersonalTraining[]
  /** Event invitations with no times, pinned above the grid */
  allDayInvitations: InvitationOverlayItem[]
}

/**
 * Split one day's entries into the hour grid and the all-day lane.
 * Untimed trainings (V72: both times NULL) and timeless event invitations never
 * reach the grid — they have no position there.
 */
export function splitDay(
  date: string,
  trainings: PersonalTraining[],
  reservations: ReservationOverlayItem[],
  invitations: InvitationOverlayItem[],
): DayEntries {
  const dayTrainings = trainings.filter((tr) => tr.date === date)
  const dayInvitations = invitations.filter((inv) => inv.date === date)
  return {
    timed: layoutDay(
      dayTrainings.filter((tr) => tr.startTime != null),
      reservations.filter((r) => r.date === date),
      dayInvitations.filter((inv) => inv.startTime != null),
    ),
    allDayTrainings: dayTrainings.filter((tr) => tr.startTime == null),
    allDayInvitations: dayInvitations.filter((inv) => inv.startTime == null),
  }
}
