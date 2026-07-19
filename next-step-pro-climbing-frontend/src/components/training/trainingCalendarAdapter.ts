import { trainingCalendarApi, adminTrainingCalendarApi } from '../../api/client'
import type {
  AthleteGoal,
  AthleteGoals,
  AthleteStats,
  CreatePersonalTraining,
  PersonalTraining,
  SaveGoal,
  TrainingCalendarRange,
  TrainingCommentItem,
} from '../../types'

/** Goal mutations exist only on the coach adapter — the athlete's banner is read-only. */
export interface GoalMutations {
  create: (data: SaveGoal) => Promise<AthleteGoal>
  update: (goalId: string, data: SaveGoal) => Promise<AthleteGoal>
  remove: (goalId: string) => Promise<void>
  achieve: (goalId: string) => Promise<AthleteGoal>
}

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
  getStats: () => Promise<AthleteStats>
  getGoals: () => Promise<AthleteGoals>
  goalMutations?: GoalMutations
}

export const athleteAdapter: TrainingCalendarAdapter = {
  getRange: trainingCalendarApi.getRange,
  createTraining: trainingCalendarApi.createTraining,
  updateTraining: trainingCalendarApi.updateTraining,
  deleteTraining: trainingCalendarApi.deleteTraining,
  getComments: trainingCalendarApi.getComments,
  addComment: trainingCalendarApi.addComment,
  markSeen: trainingCalendarApi.markSeen,
  getStats: trainingCalendarApi.getStats,
  getGoals: trainingCalendarApi.getGoals,
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
    getStats: () => adminTrainingCalendarApi.getStats(athleteId),
    getGoals: () => adminTrainingCalendarApi.getGoals(athleteId),
    goalMutations: {
      create: (data) => adminTrainingCalendarApi.createGoal(athleteId, data),
      update: adminTrainingCalendarApi.updateGoal,
      remove: adminTrainingCalendarApi.deleteGoal,
      achieve: adminTrainingCalendarApi.achieveGoal,
    },
  }
}
