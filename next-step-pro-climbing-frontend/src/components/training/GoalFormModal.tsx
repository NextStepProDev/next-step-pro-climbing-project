import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Modal } from '../ui/Modal'
import { Button } from '../ui/Button'
import { decodeHtmlEntities } from '../../utils/htmlEntities'
import type { AthleteGoal, GoalHorizon, SaveGoal } from '../../types'

interface GoalFormModalProps {
  isOpen: boolean
  onClose: () => void
  // The slot the coach clicked; fixed for the goal's lifetime (edit keeps it too)
  horizon: GoalHorizon
  // Editing an existing active goal, or creating (null)
  goal?: AthleteGoal | null
  onSubmit: (data: SaveGoal) => void
  saving: boolean
  submitError?: string | null
}

export function GoalFormModal({ isOpen, onClose, horizon, goal, onSubmit, saving, submitError }: GoalFormModalProps) {
  const { t } = useTranslation('training')

  return (
    <Modal isOpen={isOpen} onClose={onClose} title={goal ? t('goals.form.editTitle') : t('goals.form.addTitle')}>
      {/* Mounted only while open — form state resets naturally on every open */}
      {isOpen && (
        <GoalForm
          horizon={horizon}
          goal={goal}
          onClose={onClose}
          onSubmit={onSubmit}
          saving={saving}
          submitError={submitError}
        />
      )}
    </Modal>
  )
}

function GoalForm({ horizon, goal, onClose, onSubmit, saving, submitError }: {
  horizon: GoalHorizon
  goal?: AthleteGoal | null
  onClose: () => void
  onSubmit: (data: SaveGoal) => void
  saving: boolean
  submitError?: string | null
}) {
  const { t } = useTranslation('training')

  const [content, setContent] = useState(goal ? decodeHtmlEntities(goal.content) : '')
  const [targetDate, setTargetDate] = useState(goal?.targetDate ?? '')
  const [error, setError] = useState<string | null>(null)

  const submit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!content.trim()) {
      setError(t('goals.form.contentRequired'))
      return
    }
    setError(null)
    onSubmit({ horizon, content: content.trim(), targetDate })
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
          placeholder={t('goals.form.contentPlaceholder')}
          required
          className="w-full bg-surface-800 border border-surface-700 rounded-lg px-4 py-2 text-surface-100 resize-none"
        />
      </div>

      <div>
        <label className="block text-sm text-surface-400 mb-1">{t('goals.form.targetDate')}</label>
        <input
          type="date"
          value={targetDate}
          onChange={(e) => setTargetDate(e.target.value)}
          required
          className="w-full bg-surface-800 border border-surface-700 rounded-lg px-4 py-2 text-surface-100"
        />
      </div>

      {(error || submitError) && <p className="text-sm text-rose-400/80">{error ?? submitError}</p>}

      <div className="flex justify-end gap-3 pt-2">
        <Button type="button" variant="secondary" onClick={onClose}>
          {t('form.cancel')}
        </Button>
        <Button type="submit" variant="primary" loading={saving}>
          {t('form.save')}
        </Button>
      </div>
    </form>
  )
}
