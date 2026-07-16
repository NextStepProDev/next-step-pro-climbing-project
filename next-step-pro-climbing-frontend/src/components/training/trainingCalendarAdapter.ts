import { trainingCalendarApi, adminTrainingCalendarApi } from '../../api/client'
import type {
  CreatePersonalTraining,
  PersonalTraining,
  TrainingCalendarRange,
  TrainingCommentItem,
} from '../../types'

/**
 * One calendar codebase, two consumers: the athlete tab talks to /api/training-calendar,
 * the coach panel to /api/admin/training-calendar/... for a chosen athlete.
 * The adapter hides that difference from the calendar components.
 */
export interface TrainingCalendarAdapter {
  getRange: (from: string, to: string) => Promise<TrainingCalendarRange>
  createTraining: (data: CreatePersonalTraining) => Promise<PersonalTraining>
  updateTraining: (trainingId: string, data: CreatePersonalTraining) => Promise<PersonalTraining>
  deleteTraining: (trainingId: string) => Promise<void>
  getComments: (trainingId: string) => Promise<TrainingCommentItem[]>
  addComment: (trainingId: string, body: string) => Promise<TrainingCommentItem>
  markSeen: () => Promise<void>
}

export const athleteAdapter: TrainingCalendarAdapter = {
  getRange: trainingCalendarApi.getRange,
  createTraining: trainingCalendarApi.createTraining,
  updateTraining: trainingCalendarApi.updateTraining,
  deleteTraining: trainingCalendarApi.deleteTraining,
  getComments: trainingCalendarApi.getComments,
  addComment: trainingCalendarApi.addComment,
  markSeen: trainingCalendarApi.markSeen,
}

export function coachAdapter(athleteId: string): TrainingCalendarAdapter {
  return {
    getRange: (from, to) => adminTrainingCalendarApi.getRange(athleteId, from, to),
    createTraining: (data) => adminTrainingCalendarApi.createTraining(athleteId, data),
    updateTraining: adminTrainingCalendarApi.updateTraining,
    deleteTraining: adminTrainingCalendarApi.deleteTraining,
    getComments: adminTrainingCalendarApi.getComments,
    addComment: adminTrainingCalendarApi.addComment,
    markSeen: () => adminTrainingCalendarApi.markSeen(athleteId),
  }
}
