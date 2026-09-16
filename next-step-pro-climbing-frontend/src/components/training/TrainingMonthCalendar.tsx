import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { format, isSameMonth } from 'date-fns'
import { Plus } from 'lucide-react'
import clsx from 'clsx'
import { TrainingBlock, ReservationBlock, InvitationBlock } from './TrainingBlock'
import { MonthNavHeader } from './MonthNavHeader'
import { monthGridDays } from './monthGrid'
import { isTodayInWarsaw } from '../../utils/calendarDate'
import type { InvitationOverlayItem, PersonalTraining, ReservationOverlayItem } from '../../types'

interface TrainingMonthCalendarProps {
  currentMonth: Date
  onMonthChange: (date: Date) => void
  trainings: PersonalTraining[]
  reservations: ReservationOverlayItem[]
  invitations: InvitationOverlayItem[]
  invitationLabel: string
  onTrainingClick: (training: PersonalTraining) => void
  onReservationClick: (reservation: ReservationOverlayItem) => void
  onInvitationClick: (invitation: InvitationOverlayItem) => void
  // Click on a day cell -> add-training prefilled with that date
  onDayClick: (date: string) => void
  // "+N" -> the day sheet, the only way to reach what the cell could not show
  onDayExpand: (date: string) => void
  // Clipboard: while armed every cell is a paste target instead of an add target
  pasteActive?: boolean
  onPasteAt?: (date: string) => void
  cutTrainingId?: string | null
  copiedTrainingId?: string | null
  isCoachView?: boolean
}

/**
 * Four is where a cell stops being scannable. Beyond it the tiles are shorter than the
 * day number and "+N" says more than a fifth sliver would.
 */
const MAX_TILES = 4

export function TrainingMonthCalendar({
  currentMonth, onMonthChange, trainings, reservations, invitations, invitationLabel,
  onTrainingClick, onReservationClick, onInvitationClick, onDayClick, onDayExpand,
  pasteActive, onPasteAt, cutTrainingId, copiedTrainingId, isCoachView,
}: TrainingMonthCalendarProps) {
  const { t } = useTranslation('training')
  const { t: tCal } = useTranslation('calendar')

  const weekdays = [
    tCal('weekdays.mon'), tCal('weekdays.tue'), tCal('weekdays.wed'),
    tCal('weekdays.thu'), tCal('weekdays.fri'), tCal('weekdays.sat'), tCal('weekdays.sun'),
  ]

  const calendarDays = useMemo(() => monthGridDays(currentMonth), [currentMonth])

  const trainingsByDay = useMemo(() => {
    const map = new Map<string, PersonalTraining[]>()
    trainings.forEach((tr) => {
      const list = map.get(tr.date) ?? []
      list.push(tr)
      map.set(tr.date, list)
    })
    return map
  }, [trainings])

  const reservationsByDay = useMemo(() => {
    const map = new Map<string, ReservationOverlayItem[]>()
    reservations.forEach((r) => {
      const list = map.get(r.date) ?? []
      list.push(r)
      map.set(r.date, list)
    })
    return map
  }, [reservations])

  const invitationsByDay = useMemo(() => {
    const map = new Map<string, InvitationOverlayItem[]>()
    invitations.forEach((inv) => {
      const list = map.get(inv.date) ?? []
      list.push(inv)
      map.set(inv.date, list)
    })
    return map
  }, [invitations])

  return (
    <div className="bg-surface-900 rounded-xl border border-surface-800 overflow-hidden">
      <MonthNavHeader currentMonth={currentMonth} onMonthChange={onMonthChange} />

      {/* Weekday headers: once above the grid, not repeated in 42 cells */}
      <div className="grid grid-cols-7 border-b border-surface-800">
        {weekdays.map((day) => (
          <div key={day} className="py-2 text-center text-sm font-medium text-surface-500">
            {day}
          </div>
        ))}
      </div>

      {/* Calendar grid: always six rows of seven, so its height never changes as you page */}
      <div className="grid grid-cols-7">
        {calendarDays.map((day) => {
          const dateStr = format(day, 'yyyy-MM-dd')
          const today = isTodayInWarsaw(day)
          // Padding days stay live — reaching 31 July from the August grid is the point
          const outside = !isSameMonth(day, currentMonth)
          const dayTrainings = trainingsByDay.get(dateStr) ?? []
          const dayReservations = reservationsByDay.get(dateStr) ?? []
          const dayInvitations = invitationsByDay.get(dateStr) ?? []
          type Entry = { training?: PersonalTraining; reservation?: ReservationOverlayItem; invitation?: InvitationOverlayItem }
          // Invitations first — the action-needed item must never hide behind "+N"
          const entries: Entry[] = [
            ...dayInvitations.map((inv) => ({ invitation: inv })),
            ...dayTrainings.map((tr) => ({ training: tr })),
            ...dayReservations.map((r) => ({ reservation: r })),
          ]
          const overflow = entries.length - MAX_TILES

          return (
            <div
              key={dateStr}
              onClick={(e) => {
                // A real control inside the cell handles its own click.
                if ((e.target as HTMLElement).closest('button')) return
                // ⚠️ The cell never pastes, armed or not — only the strip at its foot does.
                // A whole cell that pastes makes every entry in it a near-miss away from an
                // unwanted copy, which is exactly how a calendar turns into a trap.
                onDayClick(dateStr)
              }}
              className={clsx(
                'group flex flex-col min-h-32 min-w-0 p-1 border-b border-l border-surface-800/50 transition-colors',
                outside && 'opacity-40',
                today && 'bg-primary-500/10',
                'cursor-pointer hover:bg-surface-800/40',
                // Still marked while armed, so the eye can find the day it is aiming at —
                // but the mark is a hint, not a hit area.
                pasteActive && 'ring-1 ring-inset ring-primary-500/40',
              )}
            >
              <div className={clsx(
                'text-xs font-semibold mb-1 px-1',
                today ? 'text-primary-400' : 'text-surface-300',
              )}>
                {format(day, 'd')}
              </div>
              <div className="space-y-0.5">
                {entries.slice(0, MAX_TILES).map((entry, ei) =>
                  entry.training ? (
                    <TrainingBlock
                      key={entry.training.id}
                      training={entry.training}
                      onClick={() => onTrainingClick(entry.training!)}
                      density="tile"
                      isCut={cutTrainingId === entry.training.id}
                      isCopied={copiedTrainingId === entry.training.id}
                    />
                  ) : entry.invitation ? (
                    <InvitationBlock
                      key={`inv-${ei}`}
                      invitation={entry.invitation}
                      label={invitationLabel}
                      onClick={() => onInvitationClick(entry.invitation!)}
                      density="tile"
                    />
                  ) : (
                    <ReservationBlock
                      key={entry.reservation!.id}
                      reservation={entry.reservation!}
                      label={t('overlay.reservation')}
                      onClick={() => onReservationClick(entry.reservation!)}
                      density="tile"
                      isCoachView={isCoachView}
                    />
                  ),
                )}
                {overflow > 0 && (
                  <button
                    // A real control, not the dead text it used to be: without it the hidden
                    // entries were unreachable. It opens the day whether or not something is
                    // on the clipboard — "+2 more" is a way in, never a paste target.
                    onClick={() => onDayExpand(dateStr)}
                    aria-label={t('month.moreAria', { count: overflow })}
                    className="w-full px-1.5 py-0.5 text-left text-[10px] text-surface-400 hover:text-primary-300 transition-colors"
                  >
                    {t('month.more', { count: overflow })}
                  </button>
                )}
              </div>

              {pasteActive ? (
                // The cell's one paste target, and a real button now: it used to be dead text
                // that relied on the cell underneath to catch the click.
                <button
                  onClick={() => onPasteAt?.(dateStr)}
                  aria-label={t('clipboard.pasteHere')}
                  className="mt-auto px-1 py-1 text-center text-[10px] text-primary-300 border border-dashed border-primary-500/50 rounded hover:bg-primary-500/10 transition-colors"
                >
                  {t('clipboard.pasteHere')}
                </button>
              ) : (
                <button
                  onClick={() => onDayClick(dateStr)}
                  aria-label={t('month.addOnDate', { date: dateStr })}
                  className={clsx(
                    // ⚠️ The BASE height is the touch one (44px, the floor a thumb needs); the
                    // compact 24px hides behind `pointer-fine:`, same direction as the fade
                    // below. On touch this button is fully visible and is the only discoverable
                    // way to add anything to the day, so its default size has to be usable there.
                    'mt-auto flex items-center justify-center h-11 pointer-fine:h-6 rounded border border-dashed border-surface-700',
                    // One `transition` for colour and opacity alike: two transition-* utilities
                    // fight over the same property and the winner depends on stylesheet order
                    // rather than on the order written here.
                    'text-surface-500 transition hover:border-primary-500/50 hover:text-primary-400',
                    // Faded out only where a pointer can bring it back. The base state has to be
                    // gated on the input device, not on hover: `hover:` alone leaves the hidden
                    // base state standing on a touch screen, where nothing can reveal it again.
                    // Opacity rather than display so the slot keeps its height — 42 cells must not
                    // twitch as the cursor crosses them — and so the button stays tabbable, which
                    // group-focus-within then reveals.
                    'pointer-fine:opacity-0',
                    'pointer-fine:group-hover:opacity-100 pointer-fine:group-focus-within:opacity-100',
                  )}
                >
                  <Plus className="w-3.5 h-3.5" />
                </button>
              )}
            </div>
          )
        })}
      </div>
    </div>
  )
}
