import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { athleteAdapter, coachAdapter } from './trainingCalendarAdapter'
import type { TrainingCalendarAdapter } from './trainingCalendarAdapter'

// The adapter is the only place that knows which role is looking at the calendar, so its
// contract is exercised end to end: adapter -> api/client -> the URL that actually goes out.
const ATHLETE_ID = 'athlete-42'
const TRAINING_ID = 'training-7'
const FROM = '2026-07-20'
const TO = '2026-07-26'

let fetchMock: ReturnType<typeof vi.fn>

beforeEach(() => {
  fetchMock = vi.fn().mockResolvedValue({
    ok: true,
    status: 200,
    json: async () => ({}),
  })
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  vi.unstubAllGlobals()
})

/** The path (and query) requested by the last call, without the /api prefix. */
function lastRequest(): { path: string; method: string; body: unknown } {
  const [url, init] = fetchMock.mock.calls.at(-1)!
  return {
    path: String(url).replace(/^\/api/, ''),
    method: (init?.method as string) ?? 'GET',
    body: init?.body,
  }
}

/** Every call the adapter makes, in order */
function allPaths(): string[] {
  return fetchMock.mock.calls.map(([url]) => String(url))
}

async function callEveryReadPath(api: TrainingCalendarAdapter) {
  await api.getRange(FROM, TO)
  await api.getComments(TRAINING_ID)
  await api.markSeen()
  await api.getStats()
  await api.getGoals()
}

describe('athleteAdapter — the athlete talks to their own calendar', () => {
  it('should read the range from the athlete endpoint', async () => {
    await athleteAdapter.getRange(FROM, TO)
    expect(lastRequest().path).toBe(`/training-calendar?from=${FROM}&to=${TO}`)
  })

  it('should create a training without an athlete id in the path', async () => {
    await athleteAdapter.createTraining({ date: FROM, startTime: '10:00', endTime: '11:00', title: 'Session' })
    const { path, method, body } = lastRequest()
    expect(path).toBe('/training-calendar/trainings')
    expect(method).toBe('POST')
    expect(JSON.parse(body as string)).toMatchObject({ title: 'Session' })
  })

  it('should update and delete by training id', async () => {
    await athleteAdapter.updateTraining(TRAINING_ID, { date: FROM, title: 'Renamed' })
    expect(lastRequest()).toMatchObject({ path: `/training-calendar/trainings/${TRAINING_ID}`, method: 'PUT' })

    await athleteAdapter.deleteTraining(TRAINING_ID)
    expect(lastRequest()).toMatchObject({ path: `/training-calendar/trainings/${TRAINING_ID}`, method: 'DELETE' })
  })

  it('should read and post comments on the athlete endpoint', async () => {
    await athleteAdapter.getComments(TRAINING_ID)
    expect(lastRequest().path).toBe(`/training-calendar/trainings/${TRAINING_ID}/comments`)

    await athleteAdapter.addComment(TRAINING_ID, 'felt strong')
    expect(lastRequest()).toMatchObject({
      path: `/training-calendar/trainings/${TRAINING_ID}/comments`,
      method: 'POST',
    })
  })

  it('should mark its own calendar as seen (no athlete id)', async () => {
    await athleteAdapter.markSeen()
    expect(lastRequest()).toMatchObject({ path: '/training-calendar/notifications/seen', method: 'POST' })
  })

  it('should read stats and goals for itself', async () => {
    await athleteAdapter.getStats()
    expect(lastRequest().path).toBe('/training-calendar/stats')

    await athleteAdapter.getGoals()
    expect(lastRequest().path).toBe('/training-calendar/goals')
  })

  it('should upload materials to the athlete endpoint as multipart', async () => {
    await athleteAdapter.uploadAttachment(new File(['x'], 'plan.pdf', { type: 'application/pdf' }))
    const { path, method, body } = lastRequest()
    expect(path).toBe('/training-calendar/attachments/upload')
    expect(method).toBe('POST')
    expect(body).toBeInstanceOf(FormData)
  })

  it('should never reach an admin endpoint', async () => {
    await callEveryReadPath(athleteAdapter)
    expect(allPaths().some((p) => p.includes('/admin/'))).toBe(false)
  })
})

describe('coachAdapter — the coach talks to the admin endpoints for one athlete', () => {
  const coach = coachAdapter(ATHLETE_ID)

  it('should read the range scoped to the athlete', async () => {
    await coach.getRange(FROM, TO)
    expect(lastRequest().path)
      .toBe(`/admin/training-calendar/athletes/${ATHLETE_ID}?from=${FROM}&to=${TO}`)
  })

  it('should create a training under the athlete', async () => {
    await coach.createTraining({ date: FROM, startTime: '10:00', endTime: '11:00', title: 'Session' })
    expect(lastRequest()).toMatchObject({
      path: `/admin/training-calendar/athletes/${ATHLETE_ID}/trainings`,
      method: 'POST',
    })
  })

  it('should update and delete by training id only (ownership comes from the record)', async () => {
    await coach.updateTraining(TRAINING_ID, { date: FROM, title: 'Renamed' })
    expect(lastRequest()).toMatchObject({
      path: `/admin/training-calendar/trainings/${TRAINING_ID}`,
      method: 'PUT',
    })

    await coach.deleteTraining(TRAINING_ID)
    expect(lastRequest()).toMatchObject({
      path: `/admin/training-calendar/trainings/${TRAINING_ID}`,
      method: 'DELETE',
    })
  })

  it('should mark that athlete\'s calendar as seen', async () => {
    await coach.markSeen()
    expect(lastRequest()).toMatchObject({
      path: `/admin/training-calendar/athletes/${ATHLETE_ID}/seen`,
      method: 'POST',
    })
  })

  it('should read stats and goals scoped to the athlete', async () => {
    await coach.getStats()
    expect(lastRequest().path).toBe(`/admin/training-calendar/athletes/${ATHLETE_ID}/stats`)

    await coach.getGoals()
    expect(lastRequest().path).toBe(`/admin/training-calendar/athletes/${ATHLETE_ID}/goals`)
  })

  it('should upload materials to the admin endpoint', async () => {
    await coach.uploadAttachment(new File(['x'], 'plan.pdf', { type: 'application/pdf' }))
    expect(lastRequest().path).toBe('/admin/training-calendar/attachments/upload')
  })

  it('should route every call through an admin endpoint', async () => {
    await callEveryReadPath(coach)
    expect(allPaths().every((p) => p.startsWith('/api/admin/training-calendar'))).toBe(true)
  })

  it('should not leak one athlete id into another coach adapter', async () => {
    await coachAdapter('athlete-A').getRange(FROM, TO)
    const first = lastRequest().path
    await coachAdapter('athlete-B').getRange(FROM, TO)
    const second = lastRequest().path

    expect(first).toContain('athlete-A')
    expect(second).toContain('athlete-B')
    expect(second).not.toContain('athlete-A')
  })
})

describe('adapter capabilities per role', () => {
  it('should give the coach goal mutations', () => {
    const coach = coachAdapter(ATHLETE_ID)
    expect(coach.goalMutations).toBeDefined()
    expect(Object.keys(coach.goalMutations!).sort()).toEqual(['achieve', 'create', 'remove', 'update'])
  })

  it('should leave the athlete goal banner read-only', () => {
    // The athlete may see goals and trophies but never create, edit or tick one off
    expect(athleteAdapter.goalMutations).toBeUndefined()
  })

  it('should create a goal under the athlete and mutate it by goal id', async () => {
    const goals = coachAdapter(ATHLETE_ID).goalMutations!

    await goals.create({ horizon: 'SHORT', content: 'Send 7a', targetDate: '2026-09-01' })
    expect(lastRequest()).toMatchObject({
      path: `/admin/training-calendar/athletes/${ATHLETE_ID}/goals`,
      method: 'POST',
    })

    await goals.update('goal-1', { horizon: 'SHORT', content: 'Send 7a+', targetDate: '2026-09-01' })
    expect(lastRequest()).toMatchObject({ path: '/admin/training-calendar/goals/goal-1', method: 'PUT' })

    await goals.remove('goal-1')
    expect(lastRequest()).toMatchObject({ path: '/admin/training-calendar/goals/goal-1', method: 'DELETE' })
  })

  it('should let the coach backdate an achievement, and default to today when omitted', async () => {
    const goals = coachAdapter(ATHLETE_ID).goalMutations!

    await goals.achieve('goal-1', '2026-07-01')
    expect(lastRequest()).toMatchObject({ path: '/admin/training-calendar/goals/goal-1/achieve', method: 'POST' })
    expect(JSON.parse(lastRequest().body as string)).toEqual({ achievedDate: '2026-07-01' })

    await goals.achieve('goal-1')
    expect(JSON.parse(lastRequest().body as string)).toEqual({ achievedDate: null })
  })

  it('should expose the same method surface for both roles', () => {
    const coachKeys = Object.keys(coachAdapter(ATHLETE_ID)).filter((k) => k !== 'goalMutations').sort()
    expect(Object.keys(athleteAdapter).sort()).toEqual(coachKeys)
  })
})
