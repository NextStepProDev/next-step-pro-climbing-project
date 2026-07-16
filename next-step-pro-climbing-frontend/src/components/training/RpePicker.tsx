import { useTranslation } from 'react-i18next'
import clsx from 'clsx'

interface RpePickerProps {
  value: number | null
  onChange: (value: number | null) => void
}

// Green -> amber -> red ramp for effort levels 1-10
function rpeColor(rpe: number, selected: boolean): string {
  const base =
    rpe <= 3 ? 'border-green-500/60 text-green-300' :
    rpe <= 6 ? 'border-amber-500/60 text-amber-300' :
    rpe <= 8 ? 'border-orange-500/60 text-orange-300' :
    'border-rose-500/60 text-rose-300'
  const fill =
    rpe <= 3 ? 'bg-green-500/30' :
    rpe <= 6 ? 'bg-amber-500/30' :
    rpe <= 8 ? 'bg-orange-500/30' :
    'bg-rose-500/30'
  return clsx(base, selected ? fill : 'bg-surface-800 hover:bg-surface-700')
}

export function RpePicker({ value, onChange }: RpePickerProps) {
  const { t } = useTranslation('training')

  return (
    <div>
      <label className="block text-sm text-surface-400 mb-1">{t('completion.rpe')}</label>
      <div className="flex gap-1">
        {Array.from({ length: 10 }, (_, i) => i + 1).map((rpe) => (
          <button
            key={rpe}
            type="button"
            onClick={() => onChange(value === rpe ? null : rpe)}
            aria-pressed={value === rpe}
            className={clsx(
              'flex-1 min-w-0 py-2 text-sm font-semibold rounded-lg border transition-colors',
              rpeColor(rpe, value === rpe),
            )}
          >
            {rpe}
          </button>
        ))}
      </div>
      <p className="text-xs text-surface-500 mt-1">{t('completion.rpeHint')}</p>
    </div>
  )
}
