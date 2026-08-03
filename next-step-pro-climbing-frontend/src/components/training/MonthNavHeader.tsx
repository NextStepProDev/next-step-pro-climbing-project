import { ChevronLeft, ChevronRight } from 'lucide-react'
import { format } from 'date-fns'
import { useTranslation } from 'react-i18next'
import { useDateLocale } from '../../utils/dateFnsLocale'

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

  const changeMonth = (delta: number) => {
    const d = new Date(currentMonth)
    d.setMonth(d.getMonth() + delta)
    onMonthChange(d)
  }

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
          onClick={() => onMonthChange(new Date())}
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
