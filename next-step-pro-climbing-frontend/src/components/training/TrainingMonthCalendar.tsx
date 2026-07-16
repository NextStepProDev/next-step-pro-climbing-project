import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { format, startOfMonth, endOfMonth, eachDayOfInterval, isToday } from 'date-fns'
import clsx from 'clsx'
import { TrainingBlock, ReservationBlock, InvitationBlock } from './TrainingBlock'
import { useDateLocale } from '../../utils/dateFnsLocale'
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
}

const MAX_CHIPS = 3

export function TrainingMonthCalendar({
  currentMonth, onMonthChange, trainings, reservations, invitations, invitationLabel,
  onTrainingClick, onReservationClick, onInvitationClick, onDayClick,
}: TrainingMonthCalendarProps) {
  const { t } = useTranslation('training')
  const { t: tCal } = useTranslation('calendar')
  const locale = useDateLocale()

  const weekdays = [
    tCal('weekdays.mon'), tCal('weekdays.tue'), tCal('weekdays.wed'),
    tCal('weekdays.thu'), tCal('weekdays.fri'), tCal('weekdays.sat'), tCal('weekdays.sun'),
  ]

  const calendarDays = useMemo(() => {
    const start = startOfMonth(currentMonth)
    const end = endOfMonth(currentMonth)
    const daysInMonth = eachDayOfInterval({ start, end })
    let startDayOfWeek = start.getDay()
    startDayOfWeek = startDayOfWeek === 0 ? 6 : startDayOfWeek - 1
    return [...Array(startDayOfWeek).fill(null) as null[], ...daysInMonth]
  }, [currentMonth])

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

  const changeMonth = (delta: number) => {
    const d = new Date(currentMonth)
    d.setMonth(d.getMonth() + delta)
    onMonthChange(d)
  }

  return (
    <div className="bg-surface-900 rounded-xl border border-surface-800 overflow-hidden">
      {/* Header */}
      <div className="flex items-center justify-between p-4 border-b border-surface-800">
        <button
          aria-label={t('nav.prevMonth')}
          onClick={() => changeMonth(-1)}
          className="p-2 text-surface-400 hover:text-surface-100 hover:bg-surface-800 rounded-lg transition-colors"
        >
          <ChevronLeft className="w-5 h-5" />
        </button>
        <h2 className="text-lg font-semibold text-surface-100 capitalize">
          {format(currentMonth, 'LLLL yyyy', { locale })}
        </h2>
        <button
          aria-label={t('nav.nextMonth')}
          onClick={() => changeMonth(1)}
          className="p-2 text-surface-400 hover:text-surface-100 hover:bg-surface-800 rounded-lg transition-colors"
        >
          <ChevronRight className="w-5 h-5" />
        </button>
      </div>

      {/* Weekday headers */}
      <div className="grid grid-cols-7 border-b border-surface-800">
        {weekdays.map((day) => (
          <div key={day} className="py-2 text-center text-sm font-medium text-surface-500">
            {day}
          </div>
        ))}
      </div>

      {/* Calendar grid */}
      <div className="grid grid-cols-7">
        {calendarDays.map((day, index) => {
          if (!day) {
            return <div key={`empty-${index}`} className="min-h-20 bg-surface-950/50 border-b border-l border-surface-800/50" />
          }
          const dateStr = format(day, 'yyyy-MM-dd')
          const today = isToday(day)
          const dayTrainings = trainingsByDay.get(dateStr) ?? []
          const dayReservations = reservationsByDay.get(dateStr) ?? []
          const dayInvitations = invitationsByDay.get(dateStr) ?? []
          type Chip = { training?: PersonalTraining; reservation?: ReservationOverlayItem; invitation?: InvitationOverlayItem }
          // Invitations first — the action-needed item must never hide behind "+N"
          const chips: Chip[] = [
            ...dayInvitations.map((inv) => ({ invitation: inv })),
            ...dayTrainings.map((tr) => ({ training: tr })),
            ...dayReservations.map((r) => ({ reservation: r })),
          ]
          const overflow = chips.length - MAX_CHIPS

          return (
            <div
              key={dateStr}
              onClick={(e) => {
                if ((e.target as HTMLElement).closest('button')) return
                onDayClick(dateStr)
              }}
              className={clsx(
                'min-h-20 p-1 border-b border-l border-surface-800/50 cursor-pointer transition-colors hover:bg-surface-800/40',
                today && 'bg-primary-500/10',
              )}
            >
              <div className={clsx(
                'text-xs font-semibold mb-1 px-1',
                today ? 'text-primary-400' : 'text-surface-300',
              )}>
                {format(day, 'd')}
              </div>
              <div className="space-y-0.5">
                {chips.slice(0, MAX_CHIPS).map((chip, ci) =>
                  chip.training ? (
                    <TrainingBlock
                      key={chip.training.id}
                      training={chip.training}
                      onClick={() => onTrainingClick(chip.training!)}
                      compact
                    />
                  ) : chip.invitation ? (
                    <InvitationBlock
                      key={`inv-${ci}`}
                      invitation={chip.invitation}
                      label={invitationLabel}
                      onClick={() => onInvitationClick(chip.invitation!)}
                      compact
                    />
                  ) : (
                    <ReservationBlock
                      key={chip.reservation!.id}
                      reservation={chip.reservation!}
                      label={t('overlay.reservation')}
                      onClick={() => onReservationClick(chip.reservation!)}
                      compact
                    />
                  ),
                )}
                {overflow > 0 && (
                  <div className="px-1.5 text-[10px] text-surface-500">+{overflow}</div>
                )}
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
