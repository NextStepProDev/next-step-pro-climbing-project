import { Search, X } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import type { AscentFilterState } from './ascentFiltering'
import { hasActiveFilters } from './ascentFiltering'
import type { AscentDiscipline, AscentOptions, AscentStyle, AscentTerrain } from '../../types'

interface AscentFiltersProps {
  filters: AscentFilterState
  onChange: (filters: AscentFilterState) => void
  /** Year the server filtered on; null means "all years". */
  year: string
  onYearChange: (year: string) => void
  availableYears: number[]
  areas: string[]
  options: AscentOptions
  terrain: AscentTerrain
  matchCount: number
}

const CONTROL_CLASS = 'px-3 py-2 bg-surface-800 border border-surface-700 rounded-lg text-sm text-surface-100 focus:outline-none focus:ring-2 focus:ring-primary-500/50'

/**
 * The year is the only filter the server sees — it bounds what travels over the wire. Everything
 * else slices the year already in memory, which is why typing in the search box costs nothing.
 */
export function AscentFilters({
  filters, onChange, year, onYearChange, availableYears, areas, options, terrain, matchCount,
}: AscentFiltersProps) {
  const { t } = useTranslation('ascents')

  // Mountains and crags allow different styles, so the filter offers what this terrain has
  const allStyles = terrain === 'MOUNTAIN'
    ? options.mountainStyles
    : [...new Set(options.disciplines.flatMap(option => option.styles))]

  return (
    <div className="flex flex-wrap items-center gap-2">
      <select
        value={year}
        onChange={event => onYearChange(event.target.value)}
        className={CONTROL_CLASS}
        aria-label={t('filters.year')}
      >
        {availableYears.map(available => (
          <option key={available} value={String(available)}>{available}</option>
        ))}
        <option value="all">{t('filters.allYears')}</option>
      </select>

      {terrain === 'ROCK' && (
      <select
        value={filters.discipline}
        onChange={event => onChange({ ...filters, discipline: event.target.value as AscentDiscipline | 'all' })}
        className={CONTROL_CLASS}
        aria-label={t('filters.discipline')}
      >
        <option value="all">{t('filters.discipline')}: {t('filters.all')}</option>
        {options.disciplines.map(option => (
          <option key={option.value} value={option.value}>{t(`discipline.${option.value}`)}</option>
        ))}
      </select>
      )}

      <select
        value={filters.style}
        onChange={event => onChange({ ...filters, style: event.target.value as AscentStyle | 'all' })}
        className={CONTROL_CLASS}
        aria-label={t('filters.style')}
      >
        <option value="all">{t('filters.style')}: {t('filters.all')}</option>
        {allStyles.map(style => <option key={style} value={style}>{t(`style.${style}`)}</option>)}
      </select>

      {areas.length > 1 && (
        <select
          value={filters.area}
          onChange={event => onChange({ ...filters, area: event.target.value })}
          className={CONTROL_CLASS}
          aria-label={t('filters.area')}
        >
          <option value="all">{t('filters.area')}: {t('filters.all')}</option>
          {areas.map(area => <option key={area} value={area}>{area}</option>)}
        </select>
      )}

      <div className="relative">
        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-surface-500" aria-hidden="true" />
        <input
          type="search"
          value={filters.search}
          onChange={event => onChange({ ...filters, search: event.target.value })}
          placeholder={t('filters.searchPlaceholder')}
          aria-label={t('filters.search')}
          className={`${CONTROL_CLASS} pl-9 w-52`}
        />
      </div>

      {hasActiveFilters(filters) && (
        <button
          type="button"
          onClick={() => onChange({ discipline: 'all', style: 'all', area: 'all', search: '' })}
          className="inline-flex items-center gap-1 px-2 py-2 text-sm text-surface-400 hover:text-surface-100 transition"
        >
          <X className="w-4 h-4" aria-hidden="true" />
          {t('filters.clear')}
        </button>
      )}

      <span className="ml-auto text-sm text-surface-500 tabular-nums">
        {t('filters.counted', { count: matchCount })}
      </span>
    </div>
  )
}
