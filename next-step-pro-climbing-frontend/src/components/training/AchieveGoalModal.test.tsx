import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AchieveGoalModal } from './AchieveGoalModal'
import { todayInWarsaw } from '../../utils/calendarDate'
import type { AthleteGoal } from '../../types'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'pl' } }),
}))

function makeGoal(id: string, content: string): AthleteGoal {
  return {
    id,
    kind: 'GENERAL',
    horizon: 'SHORT',
    content,
    targetDate: '2099-01-01',
    targetWeightKg: null,
    startWeightKg: null,
    achievedAutomatically: false,
    achievedAt: null,
    createdAt: '2026-01-01T00:00:00Z',
  }
}

const GOAL_A = makeGoal('a', 'Cel A')
const GOAL_B = makeGoal('b', 'Cel B')

/** Mirrors GoalsBanner: the modal is rendered unconditionally and switched by props. */
function renderModal() {
  const onConfirm = vi.fn()
  const view = render(
    <AchieveGoalModal isOpen={false} onClose={vi.fn()} goal={null} onConfirm={onConfirm} saving={false} />,
  )
  const open = (goal: AthleteGoal) =>
    view.rerender(
      <AchieveGoalModal isOpen onClose={vi.fn()} goal={goal} onConfirm={onConfirm} saving={false} />,
    )
  const close = () =>
    view.rerender(
      <AchieveGoalModal isOpen={false} onClose={vi.fn()} goal={null} onConfirm={onConfirm} saving={false} />,
    )
  const dateField = () => screen.getByLabelText('goals.achievedDate') as HTMLInputElement
  return { onConfirm, open, close, dateField }
}

describe('AchieveGoalModal', () => {
  it('defaults the achievement date to today', () => {
    const { open, dateField } = renderModal()

    open(GOAL_A)

    expect(dateField().value).toBe(todayInWarsaw())
  })

  /**
   * The state used to live in the outer component, which GoalsBanner renders unconditionally —
   * `return null` hides it without unmounting, so the backdated day survived into the NEXT goal
   * and one click awarded goal B on goal A's date.
   */
  it('forgets a backdated day when a different goal is opened', async () => {
    const user = userEvent.setup()
    const { open, close, dateField } = renderModal()

    open(GOAL_A)
    await user.clear(dateField())
    await user.type(dateField(), '2026-01-05')
    expect(dateField().value).toBe('2026-01-05')

    close()
    open(GOAL_B)

    expect(dateField().value).toBe(todayInWarsaw())
  })

  it('confirms with the day the coach picked', async () => {
    const user = userEvent.setup()
    const { onConfirm, open, dateField } = renderModal()

    open(GOAL_A)
    await user.clear(dateField())
    await user.type(dateField(), '2026-01-05')
    await user.click(screen.getByText('goals.markAchieved'))

    expect(onConfirm).toHaveBeenCalledWith('2026-01-05')
  })

  it('never offers a day in the future', () => {
    const { open, dateField } = renderModal()

    open(GOAL_A)

    expect(dateField().max).toBe(todayInWarsaw())
  })
})
