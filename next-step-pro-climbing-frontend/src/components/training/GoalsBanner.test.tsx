import { describe, it, expect, vi, beforeEach } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { GoalsBanner } from './GoalsBanner'
import type { TrainingCalendarAdapter } from './trainingCalendarAdapter'
import type { AthleteGoal, GoalHorizon, GoalKind, WeightSeries } from '../../types'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'pl' } }),
  initReactI18next: { type: '3rdParty', init: () => {} },
}))

function makeGoal(overrides: Partial<AthleteGoal> & { kind: GoalKind; horizon: GoalHorizon }): AthleteGoal {
  return {
    id: `${overrides.kind}-${overrides.horizon}`,
    content: 'Cel',
    targetDate: '2099-01-01',
    targetWeightKg: null,
    startWeightKg: null,
    achievedAutomatically: false,
    achievedAt: null,
    createdAt: '2026-01-01T00:00:00Z',
    ...overrides,
  }
}

const weightSeries: WeightSeries = {
  entries: [],
  currentTrendKg: 69.5,
  trendSampleCount: 4,
  trendConfirmed: true,
  weeklyChangePercent: -0.5,
  rapidLoss: false,
  latestWeightKg: 69.4,
  latestMeasuredOn: '2026-08-01',
  backfillDays: 120,
}

const reopen = vi.fn().mockResolvedValue(undefined)

function makeApi(active: AthleteGoal[], achieved: AthleteGoal[] = [], isCoach = false): TrainingCalendarAdapter {
  return {
    getGoals: vi.fn().mockResolvedValue({ active, achieved }),
    getWeights: vi.fn().mockResolvedValue(weightSeries),
    ...(isCoach
      ? {
          goalMutations: {
            create: vi.fn(),
            update: vi.fn(),
            remove: vi.fn(),
            achieve: vi.fn(),
            reopen,
          },
        }
      : {}),
  } as unknown as TrainingCalendarAdapter
}

function renderBanner(api: TrainingCalendarAdapter, isCoachView = false) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <GoalsBanner api={api} scopeKey="me" isCoachView={isCoachView} />
    </QueryClientProvider>,
  )
}

describe('GoalsBanner', () => {
  // Goal ids repeat across cases (kind-horizon), so a stale call could green a test falsely
  beforeEach(() => {
    reopen.mockClear()
  })

  it('renders both a training goal and a weight goal on the same horizon', async () => {
    // Regression: keying the card map by horizon alone silently dropped one of the two
    const api = makeApi([
      makeGoal({ kind: 'GENERAL', horizon: 'SHORT', content: 'Przejść 7a' }),
      makeGoal({
        kind: 'WEIGHT',
        horizon: 'SHORT',
        content: 'Zejść do 67 kg',
        targetWeightKg: 67,
        startWeightKg: 70,
      }),
    ])

    renderBanner(api)

    expect(await screen.findByText('Przejść 7a')).toBeInTheDocument()
    expect(screen.getByText('Zejść do 67 kg')).toBeInTheDocument()
  })

  it('hides the weight row from an athlete who has no weight goals', async () => {
    const api = makeApi([makeGoal({ kind: 'GENERAL', horizon: 'SHORT', content: 'Przejść 7a' })])

    renderBanner(api)

    expect(await screen.findByText('goals.section.general')).toBeInTheDocument()
    expect(screen.queryByText('goals.section.weight')).not.toBeInTheDocument()
  })

  it('always offers the coach both rows, so an empty slot is an invitation', async () => {
    const api = makeApi([], [], true)

    renderBanner(api, true)

    expect(await screen.findByText('goals.section.general')).toBeInTheDocument()
    expect(screen.getByText('goals.section.weight')).toBeInTheDocument()
  })

  it('shows the progress bar from the start weight to the target', async () => {
    const api = makeApi([
      makeGoal({
        kind: 'WEIGHT',
        horizon: 'MEDIUM',
        content: 'Zejść do 67 kg',
        targetWeightKg: 67,
        startWeightKg: 70,
      }),
    ])

    renderBanner(api)

    // Trend 69.5 of a 70 -> 67 goal: 0.5 of 3 kg done, 2.5 kg left
    expect(await screen.findByText('70,0 → 67,0 kg')).toBeInTheDocument()
    expect(screen.getByText(/17%/)).toBeInTheDocument()
  })

  it('offers the undo only for a goal a weigh-in closed', async () => {
    const autoClosed = makeGoal({
      kind: 'WEIGHT',
      horizon: 'SHORT',
      content: 'Zejść do 67 kg',
      targetWeightKg: 67,
      startWeightKg: 70,
      achievedAutomatically: true,
      achievedAt: new Date().toISOString(),
    })

    renderBanner(makeApi([], [autoClosed], true), true)

    const undo = await screen.findByText('goals.reopen')
    await userEvent.click(undo)
    await userEvent.click(screen.getByRole('button', { name: /confirm|potwierd|tak/i }))

    await waitFor(() => expect(reopen).toHaveBeenCalledWith(autoClosed.id))
  })

  it('still offers the undo from the trophy chest long after the celebration ends', async () => {
    // The 7-day celebration window is where the card lives, not a deadline on undoing:
    // a phantom weigh-in is usually spotted weeks later, and the server never time-limited it
    const oldAutoClosed = makeGoal({
      kind: 'WEIGHT',
      horizon: 'SHORT',
      content: 'Zejść do 67 kg',
      targetWeightKg: 67,
      startWeightKg: 70,
      achievedAutomatically: true,
      achievedAt: '2026-01-15T10:00:00Z',
    })

    renderBanner(makeApi([], [oldAutoClosed], true), true)

    // No celebration card this old — the only way in is the chest
    expect(screen.queryByText('goals.reopen')).not.toBeInTheDocument()
    await userEvent.click(await screen.findByText(/goals.trophies/))
    await userEvent.click(await screen.findByText('goals.reopen'))
    await userEvent.click(screen.getByRole('button', { name: /confirm|potwierd|tak/i }))

    await waitFor(() => expect(reopen).toHaveBeenCalledWith(oldAutoClosed.id))
  })

  it('leaves the chest read-only for the athlete', async () => {
    const oldAutoClosed = makeGoal({
      kind: 'WEIGHT',
      horizon: 'SHORT',
      content: 'Zejść do 67 kg',
      targetWeightKg: 67,
      startWeightKg: 70,
      achievedAutomatically: true,
      achievedAt: '2026-01-15T10:00:00Z',
    })

    renderBanner(makeApi([], [oldAutoClosed]))

    await userEvent.click(await screen.findByText(/goals.trophies/))
    expect(await screen.findByText('Zejść do 67 kg')).toBeInTheDocument()
    expect(screen.queryByText('goals.reopen')).not.toBeInTheDocument()
  })

  it('never offers the undo for a goal the coach closed by hand', async () => {
    const handClosed = makeGoal({
      kind: 'GENERAL',
      horizon: 'SHORT',
      content: '7a zrobione',
      achievedAutomatically: false,
      achievedAt: new Date().toISOString(),
    })

    renderBanner(makeApi([], [handClosed], true), true)

    expect(await screen.findByText('7a zrobione')).toBeInTheDocument()
    expect(screen.queryByText('goals.reopen')).not.toBeInTheDocument()
  })

  it('never offers a weight goal the manual achieve tick — it closes itself', async () => {
    const api = makeApi(
      [
        makeGoal({
          kind: 'WEIGHT',
          horizon: 'SHORT',
          content: 'Zejść do 67 kg',
          targetWeightKg: 67,
          startWeightKg: 70,
        }),
      ],
      [],
      true,
    )

    renderBanner(api, true)

    await screen.findByText('Zejść do 67 kg')
    expect(screen.queryByTitle('goals.markAchieved')).not.toBeInTheDocument()
    expect(screen.getByTitle('goals.edit')).toBeInTheDocument()
  })
})
