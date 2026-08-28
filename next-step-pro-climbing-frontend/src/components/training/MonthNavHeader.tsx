import { ChevronLeft, ChevronRight } from 'lucide-react'
import { addMonths, format } from 'date-fns'
import { useTranslation } from 'react-i18next'
import { useDateLocale } from '../../utils/dateFnsLocale'
import { nowInWarsaw } from '../../utils/calendarDate'

interface MonthNavHeaderProps {
  currentMonth: Date
  onMonthChange: (date: Date) => void
}

/**
 * Month paging, shared by the desktop tile grid and the phone dot grid. Extracted because
 * the two views are different components rendering the same month: a nav that lives in
 * each of them drifts the first time one is touched.
 */
export function MonthNavHeader({ currentMonth, onMonthChange }: MonthNavHeaderProps) {
  const { t } = useTranslation('training')
  const locale = useDateLocale()

  /**
   * `addMonths`, never `Date.setMonth` — the anchor keeps its day of the month, and `setMonth`
   * OVERFLOWS instead of clamping: from 31 January it produced "31 February", i.e. 3 March, so
   * pressing "next" skipped February whole and there was no way to reach it going forward.
   * Only bites on the 29th–31st, which is why it survived. date-fns clamps to the last valid day.
   */
  const changeMonth = (delta: number) => onMonthChange(addMonths(currentMonth, delta))

  return (
    <div className="flex items-center justify-between p-4 border-b border-surface-800">
      <button
        aria-label={t('nav.prevMonth')}
        onClick={() => changeMonth(-1)}
        className="p-2 text-surface-400 hover:text-surface-100 hover:bg-surface-800 rounded-lg transition-colors"
      >
        <ChevronLeft className="w-5 h-5" />
      </button>
      <div className="flex items-center gap-3">
        <h2 className="text-lg font-semibold text-surface-100 capitalize">
          {format(currentMonth, 'LLLL yyyy', { locale })}
        </h2>
        <button
          onClick={() => onMonthChange(nowInWarsaw())}
          className="px-2 py-1 text-xs font-medium text-surface-400 border border-surface-700 rounded-md hover:text-surface-100 hover:border-surface-500 transition-colors"
        >
          {t('nav.today')}
        </button>
      </div>
      <button
        aria-label={t('nav.nextMonth')}
        onClick={() => changeMonth(1)}
        className="p-2 text-surface-400 hover:text-surface-100 hover:bg-surface-800 rounded-lg transition-colors"
      >
        <ChevronRight className="w-5 h-5" />
      </button>
    </div>
  )
}
