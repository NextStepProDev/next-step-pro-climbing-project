import { trainingCalendarApi, adminTrainingCalendarApi } from '../../api/client'
import type {
  AthleteGoal,
  AthleteGoals,
  AthleteStats,
  AttachmentUpload,
  CreatePersonalTraining,
  PersonalTraining,
  SaveGoal,
  SaveWeight,
  TrainingCalendarRange,
  TrainingCommentItem,
  WeightRange,
  WeightSeries,
} from '../../types'

/** Goal mutations exist only on the coach adapter — the athlete's banner is read-only. */
export interface GoalMutations {
  create: (data: SaveGoal) => Promise<AthleteGoal>
  update: (goalId: string, data: SaveGoal) => Promise<AthleteGoal>
  remove: (goalId: string) => Promise<void>
  // achievedDate is backdatable (omit = today); the backend rejects future dates
  achieve: (goalId: string, achievedDate?: string) => Promise<AthleteGoal>
  // Only a goal a weigh-in closed can be reopened; a manual closure is refused (409)
  reopen: (goalId: string) => Promise<AthleteGoal>
}

/**
 * Recording weight exists only on the athlete adapter. The coach reads the series (and is the
 * only one shown the rapid-loss flag) but never writes somebody else's body weight.
 */
export interface WeightMutations {
  save: (data: SaveWeight) => Promise<WeightSeries>
  remove: (measuredOn: string) => Promise<WeightSeries>
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
  /**
   * Multipart sibling of addComment, chosen HERE rather than in the thread component: whether a
   * message goes as JSON or as multipart is a transport detail, and the thread must not branch on
   * which role it is rendering for.
   */
  addCommentWithFiles: (trainingId: string, body: string | null, files: File[]) => Promise<TrainingCommentItem>
  deleteCommentFile: (fileId: string) => Promise<void>
  markSeen: () => Promise<void>
  getStats: () => Promise<AthleteStats>
  getGoals: () => Promise<AthleteGoals>
  getWeights: (range?: WeightRange) => Promise<WeightSeries>
  uploadAttachment: (file: File) => Promise<AttachmentUpload>
  goalMutations?: GoalMutations
  weightMutations?: WeightMutations
}

export const athleteAdapter: TrainingCalendarAdapter = {
  getRange: trainingCalendarApi.getRange,
  createTraining: trainingCalendarApi.createTraining,
  updateTraining: trainingCalendarApi.updateTraining,
  deleteTraining: trainingCalendarApi.deleteTraining,
  getComments: trainingCalendarApi.getComments,
  addComment: trainingCalendarApi.addComment,
  addCommentWithFiles: trainingCalendarApi.addCommentWithFiles,
  deleteCommentFile: trainingCalendarApi.deleteCommentFile,
  markSeen: trainingCalendarApi.markSeen,
  getStats: trainingCalendarApi.getStats,
  getGoals: trainingCalendarApi.getGoals,
  getWeights: trainingCalendarApi.getWeights,
  uploadAttachment: trainingCalendarApi.uploadAttachment,
  weightMutations: {
    save: trainingCalendarApi.saveWeight,
    remove: trainingCalendarApi.deleteWeight,
  },
}

export function coachAdapter(athleteId: string): TrainingCalendarAdapter {
  return {
    getRange: (from, to) => adminTrainingCalendarApi.getRange(athleteId, from, to),
    createTraining: (data) => adminTrainingCalendarApi.createTraining(athleteId, data),
    updateTraining: adminTrainingCalendarApi.updateTraining,
    deleteTraining: adminTrainingCalendarApi.deleteTraining,
    getComments: adminTrainingCalendarApi.getComments,
    addComment: adminTrainingCalendarApi.addComment,
    addCommentWithFiles: adminTrainingCalendarApi.addCommentWithFiles,
    // Reading and deleting an attachment are single endpoints serving both roles, so the coach
    // adapter points at the same one — there is no admin twin to keep in step.
    deleteCommentFile: trainingCalendarApi.deleteCommentFile,
    markSeen: () => adminTrainingCalendarApi.markSeen(athleteId),
    getStats: () => adminTrainingCalendarApi.getStats(athleteId),
    getGoals: () => adminTrainingCalendarApi.getGoals(athleteId),
    getWeights: (range) => adminTrainingCalendarApi.getWeights(athleteId, range),
    uploadAttachment: adminTrainingCalendarApi.uploadAttachment,
    goalMutations: {
      create: (data) => adminTrainingCalendarApi.createGoal(athleteId, data),
      update: adminTrainingCalendarApi.updateGoal,
      remove: adminTrainingCalendarApi.deleteGoal,
      achieve: adminTrainingCalendarApi.achieveGoal,
      reopen: adminTrainingCalendarApi.reopenGoal,
    },
    // No weightMutations: the coach reads the series, never writes it
  }
}
