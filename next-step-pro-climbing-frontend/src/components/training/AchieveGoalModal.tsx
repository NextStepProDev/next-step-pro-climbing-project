import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { format } from 'date-fns'
import { Trophy } from 'lucide-react'
import { Modal } from '../ui/Modal'
import { Button } from '../ui/Button'
import { decodeHtmlEntities } from '../../utils/htmlEntities'
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
  const today = format(new Date(), 'yyyy-MM-dd')
  const [achievedDate, setAchievedDate] = useState(today)

  if (!goal) return null

  return (
    <Modal isOpen={isOpen} onClose={onClose} title={t('goals.achieveConfirmTitle')}>
      {/* Remount on open resets the date to today for the next goal */}
      {isOpen && (
        <div className="space-y-4">
          <div className="flex items-start gap-3">
            <Trophy className="w-5 h-5 text-yellow-400 shrink-0 mt-0.5" />
            <div>
              <p className="text-sm font-medium text-surface-100">{decodeHtmlEntities(goal.content)}</p>
              <p className="text-sm text-surface-400 mt-1">{t('goals.achieveConfirmMessage')}</p>
            </div>
          </div>

          <div>
            <label className="block text-sm text-surface-400 mb-1">{t('goals.achievedDate')}</label>
            <input
              type="date"
              value={achievedDate}
              max={today}
              onChange={(e) => setAchievedDate(e.target.value)}
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
      )}
    </Modal>
  )
}
