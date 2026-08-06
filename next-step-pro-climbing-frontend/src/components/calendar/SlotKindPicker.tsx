import { useTranslation } from 'react-i18next'
import { Clock, Phone, Ban } from 'lucide-react'
import clsx from 'clsx'
import type { SlotKind } from '../../utils/slotKind'

const OPTIONS: Array<{ kind: SlotKind; Icon: typeof Clock; active: string }> = [
  { kind: 'REGULAR', Icon: Clock, active: 'border-primary-500 bg-primary-500/10 text-primary-300' },
  { kind: 'WINDOW', Icon: Phone, active: 'border-teal-500 bg-teal-500/10 text-teal-300' },
  { kind: 'UNAVAILABLE', Icon: Ban, active: 'border-slate-400 bg-slate-500/15 text-slate-200' },
]

/**
 * Three-way slot kind selector. Radios rather than a stack of checkboxes: the kinds exclude one
 * another, and a checkbox pair lets the admin tick both before the backend says no.
 */
export function SlotKindPicker({
  value,
  onChange,
  disabled = false,
}: {
  value: SlotKind
  onChange: (kind: SlotKind) => void
  /** Editing an existing slot the kind of which must not change (e.g. an event's own slot). */
  disabled?: boolean
}) {
  const { t } = useTranslation('common')

  return (
    <fieldset disabled={disabled} className={clsx(disabled && 'opacity-60')}>
      <legend className="block text-sm text-surface-400 mb-1">{t('slotKind.label')}</legend>
      <div className="grid grid-cols-3 gap-2">
        {OPTIONS.map(({ kind, Icon, active }) => (
          <button
            key={kind}
            type="button"
            role="radio"
            aria-checked={value === kind}
            onClick={() => onChange(kind)}
            className={clsx(
              'flex flex-col items-center gap-1 rounded-lg border px-2 py-2.5 text-xs font-medium transition-colors',
              value === kind
                ? active
                : 'border-surface-700 bg-surface-800 text-surface-400 hover:text-surface-200 hover:border-surface-600',
            )}
          >
            <Icon className="w-4 h-4" />
            <span className="text-center leading-tight">{t(`slotKind.${kind}`)}</span>
          </button>
        ))}
      </div>
      <p className="text-xs text-surface-400 mt-1.5">{t(`slotKind.${value}Hint`)}</p>
    </fieldset>
  )
}
