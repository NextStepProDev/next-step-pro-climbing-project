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
export interface AscentAdapter {
  getLog: (terrain: AscentTerrain, year?: string) => Promise<AscentLog>
  getStats: (terrain: AscentTerrain, year?: string) => Promise<AscentStats>
  mutations?: AscentMutations
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
    // No mutations: the admin reads the logbook, never writes it
  }
}
