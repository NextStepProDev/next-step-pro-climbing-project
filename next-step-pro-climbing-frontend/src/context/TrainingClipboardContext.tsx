import { createContext, useContext, useEffect, useRef, useState } from 'react'
import { useAuth } from './AuthContext'
import type { AttachmentInput, TrainingKind } from '../types'

/**
 * One armed copy/cut, held ABOVE the router.
 *
 * It used to live inside TrainingCalendarSection, which the coach panel remounts per athlete
 * (key={athleteId}) — so copying in one athlete's calendar and walking to another's threw the
 * clipboard away, and giving the same session to three people meant typing it three times.
 * Hoisting it here is the whole feature: the calendar the paste lands in is decided by the
 * adapter of whichever section is on screen, so a surviving clipboard pastes into the athlete
 * you are currently looking at.
 *
 * Deliberately in memory only: a reload is a fresh start, and persisting an armed paste across
 * sessions would resurrect an intent nobody remembers forming.
 */
export interface TrainingClipboardEntry {
  mode: 'copy' | 'cut'
  /** Which calendar it was armed in ('me' or an athleteId) — see the cut rule below. */
  scopeKey: string
  /** Whose calendar, for the banner. Absent on the athlete's own tab, where it would say "yours". */
  scopeLabel?: string
  trainingId: string
  // Carried so a copied task pastes back as a task; a paste with no kind creates a training
  kind: TrainingKind
  targetCalories?: number | null
  // Snapshotted (already decoded) at copy time, so pasting keeps working after the source
  // scrolls out of the fetched range — or after the source calendar is left entirely.
  title: string
  description?: string
  // The source's own hour, used when a paste lands somewhere with no hour axis (the month grid,
  // the day sheet). null = the source was untimed, and the paste stays untimed — inventing an
  // hour would turn "do it on Wednesday" into an appointment.
  startTime: string | null
  durationMin: number
  // Carried so a copy→paste recreates the source's materials (cut/move keeps them via null)
  attachments: AttachmentInput[]
}

interface TrainingClipboardValue {
  clipboard: TrainingClipboardEntry | null
  setClipboard: (entry: TrainingClipboardEntry | null) => void
}

const TrainingClipboardContext = createContext<TrainingClipboardValue | null>(null)

export function TrainingClipboardProvider({ children }: { children: React.ReactNode }) {
  const [clipboard, setClipboard] = useState<TrainingClipboardEntry | null>(null)
  const { user } = useAuth()

  // A clipboard outlives every remount by design, which includes the remount after a logout.
  // Clearing on a change of account keeps one coach's copied plan from arming itself under the
  // next person to sign in on the same browser.
  const lastUserId = useRef(user?.id)
  useEffect(() => {
    if (lastUserId.current !== user?.id) {
      lastUserId.current = user?.id
      setClipboard(null)
    }
  }, [user?.id])

  return (
    <TrainingClipboardContext.Provider value={{ clipboard, setClipboard }}>
      {children}
    </TrainingClipboardContext.Provider>
  )
}

// eslint-disable-next-line react-refresh/only-export-components
export function useTrainingClipboard() {
  const context = useContext(TrainingClipboardContext)
  if (!context) {
    throw new Error('useTrainingClipboard must be used within a TrainingClipboardProvider')
  }
  return context
}
