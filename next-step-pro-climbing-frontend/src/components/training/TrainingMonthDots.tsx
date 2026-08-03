import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { format, isSameMonth, isToday } from 'date-fns'
import clsx from 'clsx'
import { MonthNavHeader } from './MonthNavHeader'
import { monthGridDays } from './monthGrid'
import type { InvitationOverlayItem, PersonalTraining, ReservationOverlayItem } from '../../types'

interface TrainingMonthDotsProps {
  currentMonth: Date
  onMonthChange: (date: Date) => void
  trainings: PersonalTraining[]
  reservations: ReservationOverlayItem[]
  invitations: InvitationOverlayItem[]
  // Tapping a day opens the sheet — at this width a cell cannot hold the content itself
  onDayExpand: (date: string) => void
  pasteActive?: boolean
  onPasteAt?: (date: string) => void
}

/**
 * Beyond four the dots stop being countable and a number says it better.
 */
const MAX_DOTS = 4

/**
 * The month view at phone width.
 *
 * A separate component rather than a restyle of the tile grid: below `sm` a day cell is
 * about 45px wide, which can say that something is planned and never what. The seven
 * columns of the tile grid collapsing into one would make a month 42 stacked cells and
 * thousands of pixels of scrolling — most of it empty days — with no way to see a month
 * as a month.
 *
 * Colour carries the status, so SHAPE has to carry the kind: at six pixels it is the only
 * other channel that still reads.
 */
function dotClass(entry: Entry): string {
  if (entry.invitation) {
    // Hollow: a held seat is an offer, not something that is on the plan yet
    return 'rounded-full border border-amber-400'
  }
  if (entry.reservation) return 'rounded-[2px] bg-surface-400'
  const training = entry.training!
  const status = training.status === 'COMPLETED' ? 'bg-green-500'
    : training.status === 'MISSED' ? 'bg-rose-500'
    : 'bg-indigo-400'
  // A task is a square, a training a circle. Colour is already spent on the status, so the kind
  // has to ride the only channel left at six pixels.
  return `${training.kind === 'TASK' ? 'rounded-[1px]' : 'rounded-full'} ${status}`
}

type Entry = {
  training?: PersonalTraining
  reservation?: ReservationOverlayItem
  invitation?: InvitationOverlayItem
}

export function TrainingMonthDots({
  currentMonth, onMonthChange, trainings, reservations, invitations,
  onDayExpand, pasteActive, onPasteAt,
}: TrainingMonthDotsProps) {
  const { t } = useTranslation('training')
  const { t: tCal } = useTranslation('calendar')

  const weekdays = [
    tCal('weekdays.mon'), tCal('weekdays.tue'), tCal('weekdays.wed'),
    tCal('weekdays.thu'), tCal('weekdays.fri'), tCal('weekdays.sat'), tCal('weekdays.sun'),
  ]

  const calendarDays = useMemo(() => monthGridDays(currentMonth), [currentMonth])

  const entriesByDay = useMemo(() => {
    const map = new Map<string, Entry[]>()
    const push = (date: string, entry: Entry) => {
      const list = map.get(date) ?? []
      list.push(entry)
      map.set(date, list)
    }
    // Same order as the tile grid, so the dot that survives the cap is the same entry
    invitations.forEach((inv) => push(inv.date, { invitation: inv }))
    trainings.forEach((tr) => push(tr.date, { training: tr }))
    reservations.forEach((r) => push(r.date, { reservation: r }))
    return map
  }, [trainings, reservations, invitations])

  return (
    <div className="bg-surface-900 rounded-xl border border-surface-800 overflow-hidden">
      <MonthNavHeader currentMonth={currentMonth} onMonthChange={onMonthChange} />

      <div className="grid grid-cols-7 gap-1 px-1 pb-1">
        {weekdays.map((day) => (
          <div key={day} className="py-2 text-center text-[10px] font-medium text-surface-500">
            {day}
          </div>
        ))}

        {calendarDays.map((day) => {
          const dateStr = format(day, 'yyyy-MM-dd')
          const today = isToday(day)
          const outside = !isSameMonth(day, currentMonth)
          const entries = entriesByDay.get(dateStr) ?? []
          const overflow = entries.length - MAX_DOTS
          const unread = entries.some(
            (e) => e.training?.hasUnreadActivity || e.reservation?.isNew,
          )

          return (
            <button
              key={dateStr}
              onClick={() => (pasteActive ? onPasteAt?.(dateStr) : onDayExpand(dateStr))}
              aria-label={t('month.showDay', { date: dateStr })}
              className={clsx(
                'relative aspect-square flex flex-col items-center justify-start gap-0.5 p-1 rounded-lg transition-colors',
                outside && 'opacity-40',
                today ? 'bg-primary-500/15 ring-1 ring-primary-500/40' : 'hover:bg-surface-800/60',
                pasteActive && 'ring-1 ring-primary-500/50',
              )}
            >
              <span className={clsx(
                'text-[11px] font-semibold leading-none',
                today ? 'text-primary-300' : 'text-surface-300',
              )}>
                {format(day, 'd')}
              </span>

              <span className="flex flex-wrap items-center justify-center gap-0.5">
                {entries.slice(0, MAX_DOTS).map((entry, i) => (
                  <span key={i} className={clsx('w-1.5 h-1.5', dotClass(entry))} />
                ))}
              </span>

              {overflow > 0 && (
                <span className="text-[9px] leading-none text-surface-400 tabular-nums">
                  {t('month.more', { count: overflow })}
                </span>
              )}

              {unread && (
                <span className="absolute top-0.5 right-0.5 w-1.5 h-1.5 rounded-full bg-rose-500" />
              )}
            </button>
          )
        })}
      </div>
    </div>
  )
}
