import type {
  InvitationOverlayItem,
  PersonalTraining,
  ReservationOverlayItem,
  TrainingAttachment,
} from '../types'

// Minimal, valid domain objects for tests. Every field has a boring default so a test
// only spells out what it actually cares about.

let idSeq = 0
const nextId = (prefix: string) => `${prefix}-${++idSeq}`

export function makeTraining(overrides: Partial<PersonalTraining> = {}): PersonalTraining {
  return {
    id: nextId('training'),
    kind: 'TRAINING',
    date: '2026-07-20',
    startTime: '10:00',
    endTime: '11:00',
    title: 'Bouldering session',
    description: null,
    targetCalories: null,
    createdByAdmin: false,
    status: 'PLANNED',
    completedAt: null,
    feedback: null,
    rpe: null,
    hasUnreadActivity: false,
    createdAt: '2026-07-01T09:00:00Z',
    attachments: [],
    ...overrides,
  }
}

// A task is always untimed (V77) and never rated, so it never takes times or an RPE
export function makeTask(overrides: Partial<PersonalTraining> = {}): PersonalTraining {
  return makeTraining({
    kind: 'TASK',
    startTime: null,
    endTime: null,
    title: 'Stay under 2200 kcal',
    ...overrides,
  })
}

export function makeAttachment(overrides: Partial<TrainingAttachment> = {}): TrainingAttachment {
  return {
    id: nextId('attachment'),
    kind: 'LINK',
    url: 'https://example.com/plan',
    label: null,
    embedUrl: null,
    filename: null,
    fileName: null,
    mimeType: null,
    sizeBytes: null,
    ...overrides,
  }
}

export function makeReservation(overrides: Partial<ReservationOverlayItem> = {}): ReservationOverlayItem {
  return {
    id: nextId('reservation'),
    slotId: nextId('slot'),
    date: '2026-07-20',
    startTime: '10:00',
    endTime: '11:00',
    title: 'Individual training',
    isNew: false,
    rpe: null,
    rpeNote: null,
    canRate: false,
    ...overrides,
  }
}

export function makeInvitation(overrides: Partial<InvitationOverlayItem> = {}): InvitationOverlayItem {
  return {
    slotId: nextId('slot'),
    eventId: null,
    date: '2026-07-20',
    startTime: '10:00',
    endTime: '11:00',
    title: 'Reserved seat',
    ...overrides,
  }
}
