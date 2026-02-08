import { format } from 'date-fns'
import { pl } from 'date-fns/locale'
import { ArrowLeft, Clock } from 'lucide-react'
import clsx from 'clsx'
import type { TimeSlot, EventSummary } from '../../types'

interface DayViewProps {
  date: string
  slots: TimeSlot[]
  events: EventSummary[]
  onBack: () => void
  onSlotClick: (slotId: string) => void
}

export function DayView({ date, slots, events, onBack, onSlotClick }: DayViewProps) {
  const dateObj = new Date(date)

  return (
    <div className="bg-dark-900 rounded-xl border border-dark-800 overflow-hidden">
      {/* Header */}
      <div className="flex items-center gap-4 p-4 border-b border-dark-800">
        <button
          onClick={onBack}
          className="p-2 text-dark-400 hover:text-dark-100 hover:bg-dark-800 rounded-lg transition-colors"
        >
          <ArrowLeft className="w-5 h-5" />
        </button>
        <div>
          <h2 className="text-lg font-semibold text-dark-100 capitalize">
            {format(dateObj, 'EEEE, d MMMM yyyy', { locale: pl })}
          </h2>
          {events.length > 0 && (
            <p className="text-sm text-primary-400">
              {events.map((e) => e.title).join(', ')}
            </p>
          )}
        </div>
      </div>

      {/* Time slots */}
      <div className="p-4 space-y-3">
        {slots.length === 0 ? (
          <div className="text-center py-8 text-dark-400">
            Brak dostępnych godzin w tym dniu
          </div>
        ) : (
          slots.map((slot) => (
            <button
              key={slot.id}
              onClick={() => onSlotClick(slot.id)}
              disabled={slot.status === 'BLOCKED' || slot.status === 'PAST'}
              className={clsx(
                'w-full p-4 rounded-lg border transition-all text-left',
                slot.status === 'AVAILABLE' && 'border-dark-700 hover:border-primary-500 hover:bg-dark-800',
                slot.status === 'FULL' && 'border-dark-700 hover:border-amber-500 hover:bg-dark-800',
                slot.status === 'BLOCKED' && 'border-dark-800 bg-dark-900/50 cursor-not-allowed opacity-50',
                slot.status === 'PAST' && 'border-dark-800 bg-dark-900/50 cursor-not-allowed opacity-40',
                slot.isUserRegistered && 'border-primary-500 bg-primary-500/10'
              )}
            >
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <Clock className="w-5 h-5 text-dark-400" />
                  <span className="font-medium text-dark-100">
                    {slot.startTime.slice(0, 5)} - {slot.endTime.slice(0, 5)}
                  </span>
                </div>

                <div className="flex items-center gap-2">
                  {slot.isUserRegistered && (
                    <span className="px-2 py-1 text-xs font-medium bg-primary-500/20 text-primary-400 rounded">
                      Twoja rezerwacja
                    </span>
                  )}
                  {slot.status === 'AVAILABLE' && !slot.isUserRegistered && (
                    <span className="px-2 py-1 text-xs font-medium bg-green-500/20 text-green-400 rounded">
                      Dostępne
                    </span>
                  )}
                  {slot.status === 'FULL' && !slot.isUserRegistered && (
                    <span className="px-2 py-1 text-xs font-medium bg-amber-500/20 text-amber-400 rounded">
                      Pełne
                    </span>
                  )}
                  {slot.status === 'BLOCKED' && (
                    <span className="px-2 py-1 text-xs font-medium bg-dark-700 text-dark-400 rounded">
                      Zarezerwowane
                    </span>
                  )}
                  {slot.status === 'PAST' && (
                    <span className="px-2 py-1 text-xs font-medium bg-dark-700 text-dark-500 rounded">
                      Zakończone
                    </span>
                  )}
                </div>
              </div>

              {slot.eventTitle && (
                <div className="mt-2 text-sm text-dark-400">
                  {slot.eventTitle}
                </div>
              )}
            </button>
          ))
        )}
      </div>
    </div>
  )
}
