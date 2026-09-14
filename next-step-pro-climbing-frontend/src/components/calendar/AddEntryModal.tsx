import { useTranslation } from 'react-i18next'
import { addDays, format } from 'date-fns'
import { Clock, CalendarDays, CalendarRange } from 'lucide-react'
import { Modal } from '../ui/Modal'
import { parseCalendarDate } from '../../utils/calendarDate'
import { useDateLocale } from '../../utils/dateFnsLocale'

/** What the admin is about to add: which row, and how long it lasts. */
export type EntryShape = 'slotHours' | 'eventHours' | 'eventAllDay' | 'eventRange'

/** The answer, already turned into the dates and times the form will open with. */
export interface EntryChoice {
  shape: EntryShape
  startDate: string
  /** Same as `startDate` except for a range, where the form needs a second day to be a range. */
  endDate: string
  /** Set only for an event measured in hours, so its form opens with the clocks already showing. */
  startTime?: string
  endTime?: string
}

/**
 * The hours an event form falls back to when "all day" is unticked by hand.
 *
 * ⚠️ Copied deliberately rather than invented: the tile and the checkbox have to land on the same
 * form, or the same event gets different opening hours depending on which door was used — and the
 * difference would only ever be noticed by whoever had to correct it twice.
 */
const EVENT_DEFAULT_HOURS = { startTime: '10:00', endTime: '17:00' }

/* The "+" shows both things that decide the entry, instead of deriving one from the other.
 *
 * It used to ask "slot or event?", which is a question about the data model: the admin had to
 * already know that a three-day trip cannot be a slot. Asking only "how long?" was the first
 * attempt at fixing that, and it was wrong in a way the calendar makes obvious — recreational
 * climbing is an EVENT that lasts two hours. Duration settles the row only at the long end:
 *
 *   several days → has to be an event; a slot lives on one date
 *   a whole day  → has to be an event; a slot cannot say "the whole day"
 *   hours        → could be either, and nothing in the dates can tell them apart
 *
 * So the two questions are laid out side by side. The nouns survive as group headings only, which
 * is honest — they are real things in this calendar and the owner speaks in them; what they no
 * longer do is force a guess about which one can hold a given number of days.
 *
 * ⚠️ The tiles are a starting point, not a cage. The slot tile opens a form that keeps its own
 * handling of a multi-day ABSENCE, so the shortcut for closing a week off is untouched; an event
 * opened here can still have its hours changed or its range extended.
 */
export function AddEntryModal({
  isOpen,
  onClose,
  date,
  onPick,
}: {
  isOpen: boolean
  onClose: () => void
  /** The day the admin opened, 'yyyy-MM-dd'. A label, not a moment — hence parseCalendarDate. */
  date: string
  onPick: (choice: EntryChoice) => void
}) {
  const { t } = useTranslation('calendar')
  const locale = useDateLocale()

  // ⚠️ Props are evaluated before Modal decides to render nothing, and this one is mounted on
  // every day an admin opens. A hand-edited `?date=` arrives here as an unparseable label, and
  // `format` on Invalid Date **throws** — a throw in render takes the whole page, replacing the
  // graceful QueryError the day view falls back to. Same lesson as `datesFilled` in
  // CreateSlotModal, one component further out.
  if (!isOpen) return null
  const parsed = parseCalendarDate(date)
  const usable = !Number.isNaN(parsed.getTime())
  const day = usable ? format(parsed, 'd MMMM', { locale }) : date

  const groups = [
    { key: 'slot', options: [{ key: 'slotHours', Icon: Clock }] },
    {
      key: 'event',
      options: [
        { key: 'eventHours', Icon: Clock },
        { key: 'eventAllDay', Icon: CalendarDays },
        { key: 'eventRange', Icon: CalendarRange },
      ],
    },
  ] as const

  /* The dates are worked out here, next to the guard that already knows whether the label parses
     — a second caller doing its own arithmetic would need its own copy of that guard, and the one
     that forgot it would throw on `format(Invalid Date)` and take the page down with it. */
  const choose = (shape: EntryShape) => {
    const endDate = shape === 'eventRange' && usable
      ? format(addDays(parsed, 1), 'yyyy-MM-dd')
      : date
    onPick({
      shape,
      startDate: date,
      endDate,
      ...(shape === 'eventHours' ? EVENT_DEFAULT_HOURS : {}),
    })
  }

  return (
    <Modal isOpen={isOpen} onClose={onClose} title={t('addEntry.title', { day })}>
      <div className="space-y-4">
        <p className="text-sm text-surface-400">{t('addEntry.question')}</p>

        {groups.map(({ key: group, options }) => (
          <div key={group}>
            <h3 className="text-xs font-semibold uppercase tracking-wider text-surface-400">
              {t(`addEntry.group.${group}`)}
            </h3>
            <p className="text-xs text-surface-500 mb-2">{t(`addEntry.group.${group}Hint`)}</p>

            <div className={`grid gap-3 ${options.length > 1 ? 'grid-cols-1 sm:grid-cols-3' : 'grid-cols-1'}`}>
              {options.map(({ key, Icon }) => (
                <button
                  key={key}
                  type="button"
                  onClick={() => choose(key)}
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
          </div>
        ))}
      </div>
    </Modal>
  )
}
