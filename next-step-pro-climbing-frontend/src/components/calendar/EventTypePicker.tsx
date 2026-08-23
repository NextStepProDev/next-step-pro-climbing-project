import { useTranslation } from 'react-i18next'
import { GraduationCap, Dumbbell, Users, Phone, Ban } from 'lucide-react'
import clsx from 'clsx'
import type { EventType } from '../../types'

/** Accents follow the per-type palette the calendar already uses (`EVENT_TYPE_COLORS`), so the
 *  tile the admin presses looks like the entry they are about to create. */
const OPTIONS: Array<{ type: EventType; Icon: typeof Ban; active: string }> = [
  { type: 'COURSE', Icon: GraduationCap, active: 'border-primary-500 bg-primary-500/10 text-primary-300' },
  { type: 'TRAINING', Icon: Dumbbell, active: 'border-orange-500 bg-orange-500/10 text-orange-300' },
  { type: 'WORKSHOP', Icon: Users, active: 'border-green-500 bg-green-500/10 text-green-300' },
  { type: 'CONTACT_DAY', Icon: Phone, active: 'border-indigo-500 bg-indigo-500/10 text-indigo-300' },
  { type: 'UNAVAILABLE', Icon: Ban, active: 'border-slate-400 bg-slate-500/15 text-slate-200' },
]

/**
 * Five-way event type selector — the counterpart of {@link SlotKindPicker}, so a day closed off as
 * an event is picked the same way as one closed off as a slot (same tiles, same `Ban`, same hint).
 *
 * A `<select>` listed all five as interchangeable names, while two of them change what the entry
 * IS: CONTACT_DAY and UNAVAILABLE take enrollment away, and UNAVAILABLE also zeroes the seats and
 * drops the invitations. Those consequences belong next to the choice, not behind a closed list.
 */
export function EventTypePicker({
  label,
  value,
  onChange,
}: {
  label: string
  value: EventType
  onChange: (type: EventType) => void
}) {
  const { t } = useTranslation('common')

  return (
    <fieldset>
      <legend className="block text-sm text-surface-400 mb-1">{label}</legend>
      <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
        {OPTIONS.map(({ type, Icon, active }) => (
          <button
            key={type}
            type="button"
            role="radio"
            aria-checked={value === type}
            onClick={() => onChange(type)}
            className={clsx(
              'flex flex-col items-center gap-1 rounded-lg border px-2 py-2.5 text-xs font-medium transition-colors',
              value === type
                ? active
                : 'border-surface-700 bg-surface-800 text-surface-400 hover:text-surface-200 hover:border-surface-600',
            )}
          >
            <Icon className="w-4 h-4" />
            <span className="text-center leading-tight">{t(`eventTypes.${type}`)}</span>
          </button>
        ))}
      </div>
      <p className="text-xs text-surface-400 mt-1.5">{t(`eventTypes.${value}Hint`)}</p>
    </fieldset>
  )
}
