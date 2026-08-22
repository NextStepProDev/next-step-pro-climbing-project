import { ascentApi, adminAscentApi } from '../../api/client'
import type { Ascent, AscentLog, AscentStats, AscentTerrain, SaveAscent } from '../../types'

/**
 * Writing exists only on the athlete adapter. The coach reads the logbook and exports it but
 * never records somebody else's ascent — expressed as an absent field rather than an `if`, so
 * a coach-side write is a compile error instead of a code path to remember not to take.
 */
export interface AscentMutations {
  create: (data: SaveAscent) => Promise<Ascent>
  update: (ascentId: string, data: SaveAscent) => Promise<Ascent>
  remove: (ascentId: string) => Promise<void>
}

/**
 * One logbook codebase, two consumers: the owner's tab talks to /api/ascents, the admin panel to
 * /api/admin/ascents/users/{id}.
 */
/**
 * Publication, not content — deliberately a separate field from `mutations` rather than another
 * method on it. The admin still cannot create, edit or delete somebody's ascent, and keeping that
 * in an absent `mutations` means the guarantee stays a type-level fact rather than a rule to
 * remember. What this can do is take one entry off the public list: the row is the author's, the
 * noticeboard is the site owner's.
 */
export interface AscentModeration {
  setPublicVisibility: (ascentId: string, hidden: boolean) => Promise<Ascent>
}

export interface AscentAdapter {
  getLog: (terrain: AscentTerrain, year?: string) => Promise<AscentLog>
  getStats: (terrain: AscentTerrain, year?: string) => Promise<AscentStats>
  mutations?: AscentMutations
  moderation?: AscentModeration
}

export const athleteAscentAdapter: AscentAdapter = {
  getLog: ascentApi.getLog,
  getStats: ascentApi.getStats,
  mutations: {
    create: ascentApi.create,
    update: ascentApi.update,
    remove: ascentApi.remove,
  },
}

export function coachAscentAdapter(userId: string): AscentAdapter {
  return {
    getLog: (terrain, year) => adminAscentApi.getLog(userId, terrain, year),
    getStats: (terrain, year) => adminAscentApi.getStats(userId, terrain, year),
    // No mutations: the admin reads the logbook, never writes its content
    moderation: {
      setPublicVisibility: adminAscentApi.setPublicVisibility,
    },
  }
}
