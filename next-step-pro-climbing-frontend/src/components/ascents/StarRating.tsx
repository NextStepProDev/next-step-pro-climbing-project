import { Star } from 'lucide-react'
import { useTranslation } from 'react-i18next'

interface StarRatingProps {
  value: number | null
  onChange?: (value: number | null) => void
  max?: number
  size?: 'sm' | 'md'
}

/**
 * Route quality, 0-5. Clicking the star that is already selected clears the rating — an unrated
 * route and a one-star route are different statements, and without a way back the first click
 * would be irreversible.
 *
 * Read-only when `onChange` is absent, which is how the coach's table renders it.
 */
export function StarRating({ value, onChange, max = 5, size = 'md' }: StarRatingProps) {
  const { t } = useTranslation('ascents')
  const readOnly = !onChange
  const starClass = size === 'sm' ? 'w-3.5 h-3.5' : 'w-5 h-5'

  if (readOnly) {
    if (value === null) return <span className="text-surface-600">—</span>
    return (
      <span className="inline-flex items-center gap-0.5" aria-label={`${value}/${max}`}>
        {Array.from({ length: max }, (_, index) => (
          <Star
            key={index}
            className={`${starClass} ${index < value ? 'fill-amber-400 text-amber-400' : 'text-surface-700'}`}
            aria-hidden="true"
          />
        ))}
      </span>
    )
  }

  return (
    <div className="flex items-center gap-1">
      <div className="flex items-center gap-0.5" role="group" aria-label={t('form.stars')}>
        {Array.from({ length: max }, (_, index) => {
          const starValue = index + 1
          const filled = value !== null && starValue <= value
          return (
            <button
              key={starValue}
              type="button"
              // Clicking the current value clears it, so the first click is not a one-way door
              onClick={() => onChange(value === starValue ? null : starValue)}
              aria-pressed={filled}
              aria-label={`${starValue}/${max}`}
              className="p-0.5 rounded transition hover:scale-110 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-1"
            >
              <Star
                className={`${starClass} ${filled ? 'fill-amber-400 text-amber-400' : 'text-surface-600'}`}
                aria-hidden="true"
              />
            </button>
          )
        })}
      </div>
      {value !== null && (
        <button
          type="button"
          onClick={() => onChange(null)}
          className="text-xs text-surface-500 hover:text-surface-300 transition"
        >
          {t('form.starsClear')}
        </button>
      )}
    </div>
  )
}
