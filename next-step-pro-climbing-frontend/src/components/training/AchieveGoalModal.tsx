import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Trophy } from 'lucide-react'
import { Modal } from '../ui/Modal'
import { Button } from '../ui/Button'
import { DateInput } from '../ui/DateInput'
import { decodeHtmlEntities } from '../../utils/htmlEntities'
import { todayInWarsaw } from '../../utils/calendarDate'
import type { AthleteGoal } from '../../types'

interface AchieveGoalModalProps {
  isOpen: boolean
  onClose: () => void
  goal: AthleteGoal | null
  // achievedDate is a yyyy-MM-dd string (defaults to today, backdatable, not future)
  onConfirm: (achievedDate: string) => void
  saving: boolean
  submitError?: string | null
}

/**
 * Marking a goal achieved with a pickable achievement date — the coach often records it
 * days later, so it must not always land on "today". Defaults to today, max = today.
 */
export function AchieveGoalModal({ isOpen, onClose, goal, onConfirm, saving, submitError }: AchieveGoalModalProps) {
  const { t } = useTranslation('training')

  if (!goal) return null

  return (
    <Modal isOpen={isOpen} onClose={onClose} title={t('goals.achieveConfirmTitle')}>
      {/* Mounted only while open, so the date resets to today on every open — and that is why the
          state lives in the inner component. Held out here it did NOT reset: GoalsBanner renders
          this shell unconditionally, so `return null` hides it without unmounting, and the day
          backdated for one goal was still in the picker when the next one opened — one click from
          awarding goal B on goal A's date. Same shape as TrainingFormModal / GoalFormModal. */}
      {isOpen && (
        <AchieveGoalForm
          goal={goal}
          onClose={onClose}
          onConfirm={onConfirm}
          saving={saving}
          submitError={submitError}
        />
      )}
    </Modal>
  )
}

function AchieveGoalForm({ goal, onClose, onConfirm, saving, submitError }: {
  goal: AthleteGoal
  onClose: () => void
  onConfirm: (achievedDate: string) => void
  saving: boolean
  submitError?: string | null
}) {
  const { t } = useTranslation('training')
  // Read on mount — and mount now means "the modal just opened", so a tab left open overnight
  // no longer proposes yesterday.
  const today = todayInWarsaw()
  const [achievedDate, setAchievedDate] = useState(today)

  return (
    <div className="space-y-4">
      <div className="flex items-start gap-3">
        <Trophy className="w-5 h-5 text-yellow-400 shrink-0 mt-0.5" />
        <div>
          <p className="text-sm font-medium text-surface-100">{decodeHtmlEntities(goal.content)}</p>
          <p className="text-sm text-surface-400 mt-1">{t('goals.achieveConfirmMessage')}</p>
        </div>
      </div>

      <div>
        <label htmlFor="goal-achieved-date" className="block text-sm text-surface-400 mb-1">
          {t('goals.achievedDate')}
        </label>
        <DateInput
          id="goal-achieved-date"
          value={achievedDate}
          max={today}
          onChange={setAchievedDate}
          className="w-full bg-surface-800 border border-surface-700 rounded-lg px-4 py-2 text-surface-100"
        />
      </div>

      {submitError && <p className="text-sm text-rose-400/80">{submitError}</p>}

      <div className="flex justify-end gap-3 pt-2">
        <Button type="button" variant="secondary" onClick={onClose}>
          {t('form.cancel')}
        </Button>
        <Button
          type="button"
          variant="primary"
          loading={saving}
          disabled={!achievedDate}
          onClick={() => onConfirm(achievedDate)}
        >
          {t('goals.markAchieved')}
        </Button>
      </div>
    </div>
  )
}
