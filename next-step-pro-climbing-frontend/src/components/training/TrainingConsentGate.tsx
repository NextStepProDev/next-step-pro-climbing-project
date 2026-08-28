import { useState } from 'react'
import { Trans, useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { ShieldCheck, ExternalLink } from 'lucide-react'
import { Button } from '../ui/Button'
import { trainingCalendarApi } from '../../api/client'
import { getErrorMessage } from '../../utils/errors'

interface Props {
  /** Re-fetches the profile so the freshly stored consent flips the gate off. */
  onAccepted: () => Promise<void>
}

/**
 * One-time consent screen standing in front of the athlete's training calendar.
 *
 * The calendar collects weigh-ins, weight goals, RPE and post-session feedback — in this
 * context health data (GDPR art. 9), which needs EXPLICIT consent: a deliberate tick, never
 * implied from using the app. Hence a real checkbox that starts unticked and a submit button
 * that stays disabled until it is ticked. Shown to everyone whose consent is not on record,
 * including athletes who have used the calendar for months (V76 backfills nothing).
 *
 * Rendered INSTEAD of the calendar rather than as a modal on top of it: nothing may fetch
 * calendar content before consent (the API 409s anyway), and the athlete needs room to read
 * the policy — a dialog that cannot be dismissed would also make the consent look coerced.
 */
export function TrainingConsentGate({ onAccepted }: Props) {
  const { t } = useTranslation('training')
  const [checked, setChecked] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const accept = async () => {
    if (!checked) return
    setSaving(true)
    setError(null)
    try {
      await trainingCalendarApi.acceptConsent()
      await onAccepted()
    } catch (e) {
      setError(getErrorMessage(e))
    } finally {
      // Cleared on success too, not only on failure. Normally the refreshed profile unmounts this
      // screen and nobody notices — but if the flag does not flip (a cached profile, a refresh that
      // resolves without it), the button span forever with no message and no way forward.
      setSaving(false)
    }
  }

  const items = t('consent.items', { returnObjects: true }) as string[]

  return (
    <div className="max-w-2xl mx-auto py-8">
      <div className="bg-surface-900 border border-surface-800 rounded-2xl p-6 sm:p-8">
        <div className="flex items-center justify-center w-12 h-12 bg-primary-500/15 rounded-full mx-auto mb-4">
          <ShieldCheck className="w-6 h-6 text-primary-400" />
        </div>

        <h2 className="text-xl font-semibold text-surface-100 text-center mb-3">
          {t('consent.title')}
        </h2>
        <p className="text-sm text-surface-400 leading-relaxed text-center mb-6">
          {t('consent.intro')}
        </p>

        <div className="bg-surface-800/50 rounded-xl p-4 mb-4">
          <p className="text-xs font-semibold text-surface-300 uppercase tracking-wider mb-3">
            {t('consent.itemsTitle')}
          </p>
          <ul className="space-y-2">
            {items.map((item) => (
              <li key={item} className="flex items-start gap-2 text-sm text-surface-400 leading-relaxed">
                <span className="mt-1.5 w-1.5 h-1.5 rounded-full bg-primary-500 shrink-0" />
                {item}
              </li>
            ))}
          </ul>
        </div>

        <p className="text-sm text-surface-400 leading-relaxed mb-6">
          {t('consent.coachAccess')}
        </p>

        {/* The policy sits one click away, at the section describing exactly this processing —
            consent given without a chance to read what it covers is not informed consent. */}
        <Link
          to="/polityka-prywatnosci#kalendarz-treningowy"
          target="_blank"
          rel="noopener noreferrer"
          className="inline-flex items-center gap-1.5 text-sm text-primary-400 hover:text-primary-300 underline transition-colors mb-6"
        >
          {t('consent.readPolicy')}
          <ExternalLink className="w-3.5 h-3.5" />
        </Link>

        <label className="flex items-start gap-3 p-4 bg-surface-800/50 rounded-xl cursor-pointer hover:bg-surface-800/80 transition-colors">
          <input
            type="checkbox"
            checked={checked}
            onChange={(e) => setChecked(e.target.checked)}
            className="mt-0.5 w-4 h-4 shrink-0 rounded border-surface-600 bg-surface-900 text-primary-600 focus:ring-2 focus:ring-primary-500 focus:ring-offset-0 cursor-pointer"
          />
          <span className="text-sm text-surface-300 leading-relaxed">
            <Trans
              i18nKey="consent.checkbox"
              ns="training"
              components={{
                1: (
                  <Link
                    to="/polityka-prywatnosci#kalendarz-treningowy"
                    target="_blank"
                    rel="noopener noreferrer"
                    className="text-primary-400 hover:text-primary-300 underline transition-colors"
                    onClick={(e) => e.stopPropagation()}
                  />
                ),
              }}
            />
          </span>
        </label>

        {error && (
          <p className="mt-4 text-sm text-rose-400 text-center">{error}</p>
        )}

        <Button
          variant="primary"
          size="lg"
          className="w-full mt-6"
          disabled={!checked}
          loading={saving}
          onClick={accept}
        >
          {t('consent.accept')}
        </Button>

        <p className="mt-5 pt-4 border-t border-surface-800 text-xs text-surface-500 leading-relaxed text-center">
          {t('consent.withdrawNote')}
        </p>
      </div>
    </div>
  )
}
