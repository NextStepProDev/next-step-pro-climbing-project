import { useTranslation } from 'react-i18next'
import { format } from 'date-fns'
import { Clock, CalendarDays } from 'lucide-react'
import { Modal } from '../ui/Modal'
import { parseCalendarDate } from '../../utils/calendarDate'
import { useDateLocale } from '../../utils/dateFnsLocale'

/* Which of the two rows the admin is about to add — asked once, before either form opens.
 *
 * A slot lives on exactly one date, so until now the "+" in the day view could not produce a
 * course, a workshop or a trip spanning a week; the only door to those was the events panel,
 * which means leaving the calendar and losing the day you were looking at.
 *
 * ⚠️ This is a different question from the one CreateSlotModal answers for itself. There, a date
 * range or "all day" silently swaps a slot for an UNAVAILABLE event and the admin never picks a
 * noun — because for an absence the noun carries no meaning. Here it does: an event has a type, a
 * public title, a description and a location, and none of that can be guessed from the dates.
 */
export function AddEntryModal({
  isOpen,
  onClose,
  date,
  onPickSlot,
  onPickEvent,
}: {
  isOpen: boolean
  onClose: () => void
  /** The day the admin opened, 'yyyy-MM-dd'. A label, not a moment — hence parseCalendarDate. */
  date: string
  onPickSlot: () => void
  onPickEvent: () => void
}) {
  const { t } = useTranslation('calendar')
  const locale = useDateLocale()

  const options = [
    { key: 'slot', Icon: Clock, onPick: onPickSlot },
    { key: 'event', Icon: CalendarDays, onPick: onPickEvent },
  ] as const

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={t('addEntry.title', { day: format(parseCalendarDate(date), 'd MMMM', { locale }) })}
    >
      <div className="grid grid-cols-2 gap-3">
        {options.map(({ key, Icon, onPick }) => (
          <button
            key={key}
            type="button"
            onClick={onPick}
            className="flex flex-col items-center gap-1.5 rounded-lg border border-surface-700 bg-surface-800 px-3 py-4 text-surface-300 transition-colors hover:border-primary-500 hover:bg-primary-500/10 hover:text-primary-300 focus:outline-none focus:ring-2 focus:ring-primary-500"
          >
            <Icon className="w-6 h-6" />
            <span className="text-sm font-medium">{t(`addEntry.${key}`)}</span>
            <span className="text-xs text-surface-400 text-center leading-tight">
              {t(`addEntry.${key}Hint`)}
            </span>
          </button>
        ))}
      </div>
    </Modal>
  )
}
