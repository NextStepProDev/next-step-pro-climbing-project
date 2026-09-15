import { useTranslation } from 'react-i18next'
import { Clock, Phone, Ban, Building2 } from 'lucide-react'
import clsx from 'clsx'
import type { CreateSlotKind, SlotKind } from '../../utils/slotKind'

const STYLES: Record<CreateSlotKind, { Icon: typeof Clock; active: string }> = {
  REGULAR: { Icon: Clock, active: 'border-primary-500 bg-primary-500/10 text-primary-300' },
  WINDOW: { Icon: Phone, active: 'border-teal-500 bg-teal-500/10 text-teal-300' },
  UNAVAILABLE: { Icon: Ban, active: 'border-slate-400 bg-slate-500/15 text-slate-200' },
  CONTRACTOR: { Icon: Building2, active: 'border-amber-500 bg-amber-500/10 text-amber-300' },
}

/** The three shapes a slot can have. Creating offers a fourth choice — see `CreateSlotKind`. */
const SHAPES: SlotKind[] = ['REGULAR', 'WINDOW', 'UNAVAILABLE']

/**
 * Slot kind selector. Radios rather than a stack of checkboxes: the kinds exclude one another, and
 * a checkbox pair lets the admin tick both before the backend says no.
 *
 * `options` defaults to the three stored shapes, because that is all an EDIT form can offer: a
 * contractor session is a regular slot plus an assignment, so it does not come back from the wire
 * as a kind of its own and reopening one would silently show it as the fourth tile. Creating passes
 * the fourth in explicitly.
 */
export function SlotKindPicker<T extends CreateSlotKind = SlotKind>({
  value,
  onChange,
  disabled = false,
  // The cast is the price of the default: an edit form binds T to `SlotKind` and must not be handed
  // back a value its own state cannot hold, which is exactly what a plain `CreateSlotKind[]` here
  // would allow. The list itself is narrower than T in every real call.
  options = SHAPES as T[],
}: {
  value: T
  onChange: (kind: T) => void
  /** Editing an existing slot the kind of which must not change (e.g. an event's own slot). */
  disabled?: boolean
  options?: T[]
}) {
  const { t } = useTranslation('common')

  return (
    <fieldset disabled={disabled} className={clsx(disabled && 'opacity-60')}>
      <legend className="block text-sm text-surface-400 mb-1">{t('slotKind.label')}</legend>
      {/* Class names spelled out rather than built from `options.length`: Tailwind scans the source
          for literals, so a computed `grid-cols-${n}` is a class that never gets generated. Four
          tiles go two-by-two on a phone — "Zajęcia dla kontrahenta" has no chance at a quarter of
          390px. */}
      <div
        className={clsx(
          'grid gap-2',
          options.length > 3 ? 'grid-cols-2 sm:grid-cols-4' : 'grid-cols-3',
        )}
      >
        {options.map((kind) => {
          const { Icon, active } = STYLES[kind]
          return (
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
          )
        })}
      </div>
      <p className="text-xs text-surface-400 mt-1.5">{t(`slotKind.${value}Hint`)}</p>
    </fieldset>
  )
}
