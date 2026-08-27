import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Modal } from '../ui/Modal'
import { useModalClose } from '../ui/modalClose'
import { Button } from '../ui/Button'
import { DateInput } from '../ui/DateInput'
import { decodeHtmlEntities } from '../../utils/htmlEntities'
import { useDirty } from '../../hooks/useDirty'
import { useChildDirty } from '../../hooks/useChildDirty'
import { useUnsavedChangesWarning } from '../../hooks/useUnsavedChangesWarning'
import type { AthleteGoal, GoalHorizon, GoalKind, SaveGoal } from '../../types'

interface GoalFormModalProps {
  isOpen: boolean
  onClose: () => void
  // The slot the coach clicked; both are fixed for the goal's lifetime (edit keeps them too)
  kind: GoalKind
  horizon: GoalHorizon
  // Editing an existing active goal, or creating (null)
  goal?: AthleteGoal | null
  // WEIGHT only: shown as a hint, and the value the server will snapshot as the start weight
  currentTrendKg?: number | null
  onSubmit: (data: SaveGoal) => void
  saving: boolean
  submitError?: string | null
}

export function GoalFormModal({
  isOpen,
  onClose,
  kind,
  horizon,
  goal,
  currentTrendKg,
  onSubmit,
  saving,
  submitError,
}: GoalFormModalProps) {
  const { t } = useTranslation('training')
  // Reported up by the form below, so closing a half-written goal asks first
  const [isDirty, reportDirty] = useChildDirty(isOpen)

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={goal ? t('goals.form.editTitle') : t('goals.form.addTitle')}
      confirmClose={isDirty}
    >
      {/* Mounted only while open — form state resets naturally on every open */}
      {isOpen && (
        <GoalForm
          onDirtyChange={reportDirty}
          kind={kind}
          horizon={horizon}
          goal={goal}
          currentTrendKg={currentTrendKg}
          onClose={onClose}
          onSubmit={onSubmit}
          saving={saving}
          submitError={submitError}
        />
      )}
    </Modal>
  )
}

function GoalForm({ onDirtyChange, kind, horizon, goal, currentTrendKg, onClose, onSubmit, saving, submitError }: {
  onDirtyChange: (dirty: boolean) => void
  kind: GoalKind
  horizon: GoalHorizon
  goal?: AthleteGoal | null
  currentTrendKg?: number | null
  onClose: () => void
  onSubmit: (data: SaveGoal) => void
  saving: boolean
  submitError?: string | null
}) {
  const { t, i18n } = useTranslation('training')

  const [content, setContent] = useState(goal ? decodeHtmlEntities(goal.content) : '')
  const [targetDate, setTargetDate] = useState(goal?.targetDate ?? '')
  const [targetWeight, setTargetWeight] = useState(goal?.targetWeightKg != null ? String(goal.targetWeightKg) : '')
  const [error, setError] = useState<string | null>(null)

  // `error` stays out of the snapshot — validation output, not anything the coach typed
  const isDirty = useDirty({ content, targetDate, targetWeight })
  onDirtyChange(isDirty)  // during render on purpose — see useChildDirty
  useUnsavedChangesWarning(isDirty)
  // Cancel sits next to Save, so a mis-click there is both the likeliest and the costliest.
  // Route it through the modal's guard rather than closing outright — see modalClose.
  const guardedClose = useModalClose()
  const cancel = () => (guardedClose ?? onClose)()

  const isWeight = kind === 'WEIGHT'
  const hasTrend = currentTrendKg != null

  const submit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!content.trim()) {
      setError(t('goals.form.contentRequired'))
      return
    }
    let parsedWeight: number | null = null
    if (isWeight) {
      // Polish keyboards produce a comma
      parsedWeight = Number.parseFloat(targetWeight.replace(',', '.'))
      if (Number.isNaN(parsedWeight) || parsedWeight < 20 || parsedWeight > 300) {
        setError(t('goals.form.targetWeightInvalid'))
        return
      }
    }
    setError(null)
    onSubmit({ kind, horizon, content: content.trim(), targetDate, targetWeightKg: parsedWeight })
  }

  return (
    <form onSubmit={submit} className="space-y-4">
      <div className="flex items-center gap-2">
        <span className="text-sm text-surface-400">{t('goals.form.horizon')}:</span>
        <span className="px-2 py-0.5 text-xs font-medium uppercase tracking-wide rounded-full bg-primary-500/15 text-primary-300 border border-primary-500/30">
          {t(`goals.horizon.${horizon.toLowerCase()}`)}
        </span>
      </div>

      <div>
        <label className="block text-sm text-surface-400 mb-1">{t('goals.form.content')}</label>
        <textarea
          value={content}
          onChange={(e) => setContent(e.target.value)}
          maxLength={500}
          rows={3}
          placeholder={isWeight ? t('goals.form.contentPlaceholderWeight') : t('goals.form.contentPlaceholder')}
          required
          className="w-full bg-surface-800 border border-surface-700 rounded-lg px-4 py-2 text-surface-100 resize-none"
        />
      </div>

      {isWeight && (
        <div>
          <label htmlFor="goal-target-weight" className="block text-sm text-surface-400 mb-1">
            {t('goals.form.targetWeight')}
          </label>
          <input
            id="goal-target-weight"
            inputMode="decimal"
            value={targetWeight}
            onChange={(e) => setTargetWeight(e.target.value)}
            placeholder="67,0"
            required
            // The start weight is never typed — the server snapshots the measured trend
            disabled={goal != null}
            className="w-32 bg-surface-800 border border-surface-700 rounded-lg px-4 py-2 text-surface-100 tabular-nums disabled:opacity-50"
          />
          <p className="text-xs text-surface-500 mt-1">
            {goal != null
              ? t('goals.form.targetWeightLocked')
              : hasTrend
                ? t('goals.form.weightAutoHint', {
                    value: currentTrendKg.toLocaleString(i18n.language, {
                      minimumFractionDigits: 1,
                      maximumFractionDigits: 1,
                    }),
                  })
                : t('goals.form.weightNeedsStart')}
          </p>
        </div>
      )}

      <div>
        <label className="block text-sm text-surface-400 mb-1">{t('goals.form.targetDate')}</label>
        <DateInput
          value={targetDate}
          onChange={setTargetDate}
          required
          className="w-full bg-surface-800 border border-surface-700 rounded-lg px-4 py-2 text-surface-100"
        />
      </div>

      {(error || submitError) && <p className="text-sm text-rose-400/80">{error ?? submitError}</p>}

      <div className="flex justify-end gap-3 pt-2">
        <Button type="button" variant="secondary" onClick={cancel}>
          {t('form.cancel')}
        </Button>
        <Button type="submit" variant="primary" loading={saving}>
          {t('form.save')}
        </Button>
      </div>
    </form>
  )
}
