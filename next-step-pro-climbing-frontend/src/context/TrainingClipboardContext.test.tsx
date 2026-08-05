import { describe, it, expect, vi } from 'vitest'
import { useState } from 'react'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {
  TrainingClipboardProvider,
  useTrainingClipboard,
  type TrainingClipboardEntry,
} from './TrainingClipboardContext'

// The provider only reads user?.id, to clear the clipboard when the account changes
const currentUser = { value: { id: 'coach-1' } as { id: string } | null }
vi.mock('./AuthContext', () => ({
  useAuth: () => ({ user: currentUser.value }),
}))

function makeEntry(overrides: Partial<TrainingClipboardEntry> = {}): TrainingClipboardEntry {
  return {
    mode: 'copy',
    scopeKey: 'athlete-kasia',
    scopeLabel: 'Kasia Nowak',
    trainingId: 'tr-1',
    kind: 'TRAINING',
    title: 'Siła — blok 1',
    startTime: '17:00',
    durationMin: 90,
    attachments: [],
    ...overrides,
  }
}

/** Stands in for a calendar section: arms the clipboard and reports what it can see. */
function Calendar({ scopeKey }: { scopeKey: string }) {
  const { clipboard, setClipboard } = useTrainingClipboard()
  return (
    <div>
      <button onClick={() => setClipboard(makeEntry({ scopeKey }))}>arm-copy</button>
      <p data-testid="visible">{clipboard ? `${clipboard.mode}:${clipboard.title}` : 'empty'}</p>
      <p data-testid="foreign">{clipboard && clipboard.scopeKey !== scopeKey ? 'foreign' : 'own'}</p>
    </div>
  )
}

/**
 * The coach panel remounts the calendar per athlete (key={athleteId}); this harness reproduces
 * that remount so the test proves the clipboard outlives it — which is the whole feature.
 */
function CoachPanel() {
  const [athlete, setAthlete] = useState('athlete-kasia')
  return (
    <TrainingClipboardProvider>
      <button onClick={() => setAthlete('athlete-marek')}>go-to-marek</button>
      <Calendar key={athlete} scopeKey={athlete} />
    </TrainingClipboardProvider>
  )
}

describe('TrainingClipboardContext', () => {
  it('should keep a copy armed across a change of athlete', async () => {
    render(<CoachPanel />)

    await userEvent.click(screen.getByText('arm-copy'))
    expect(screen.getByTestId('visible')).toHaveTextContent('copy:Siła — blok 1')

    await userEvent.click(screen.getByText('go-to-marek'))

    // Same clipboard, now recognised as arriving from somewhere else
    expect(screen.getByTestId('visible')).toHaveTextContent('copy:Siła — blok 1')
    expect(screen.getByTestId('foreign')).toHaveTextContent('foreign')
  })

  it('should report the clipboard as its own while still in the source calendar', async () => {
    render(<CoachPanel />)

    await userEvent.click(screen.getByText('arm-copy'))

    expect(screen.getByTestId('foreign')).toHaveTextContent('own')
  })

  it('should drop the clipboard when the signed-in account changes', async () => {
    // A clipboard outlives every remount by design, which includes the one after a logout —
    // one coach's copied plan must not arm itself under the next person on this browser
    function Harness() {
      const [, force] = useState(0)
      return (
        <TrainingClipboardProvider>
          <button onClick={() => { currentUser.value = { id: 'coach-2' }; force((n) => n + 1) }}>
            switch-account
          </button>
          <Calendar scopeKey="athlete-kasia" />
        </TrainingClipboardProvider>
      )
    }
    currentUser.value = { id: 'coach-1' }
    render(<Harness />)

    await userEvent.click(screen.getByText('arm-copy'))
    expect(screen.getByTestId('visible')).toHaveTextContent('copy:Siła — blok 1')

    await userEvent.click(screen.getByText('switch-account'))

    expect(screen.getByTestId('visible')).toHaveTextContent('empty')
  })
})
