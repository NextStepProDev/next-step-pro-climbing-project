/**
 * Wall-clock times of day as the API sends them ("HH:mm", or "HH:mm:ss" — seconds ignored) and
 * minutes since midnight. No dates and no time zones here: a time of day on its own has neither.
 */

export function timeToMinutes(time: string): number {
  const [h, m] = time.split(':').map(Number)
  return h * 60 + m
}

export function minutesToTime(totalMinutes: number): string {
  return `${String(Math.floor(totalMinutes / 60)).padStart(2, '0')}:${String(totalMinutes % 60).padStart(2, '0')}`
}

/** `time` moved forward by `minutes`, never past `latest` (minutes since midnight). */
export function addMinutes(time: string, minutes: number, latest: number): string {
  return minutesToTime(Math.min(timeToMinutes(time) + minutes, latest))
}
