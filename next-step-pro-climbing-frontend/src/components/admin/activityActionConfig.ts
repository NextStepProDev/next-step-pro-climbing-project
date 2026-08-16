import {
  Activity,
  CalendarPlus,
  CalendarX,
  CalendarCheck,
  ShieldAlert,
  Settings,
  Trash2,
  Lock,
  Unlock,
  UserCheck,
  UserX,
  UserMinus,
  LogOut,
  Target,
  Trophy,
  RotateCcw,
} from 'lucide-react'
import type { ActivityActionType } from '../../types'

/**
 * Icon and colour per activity action, shared by the Activity panel and the admin user card's
 * timeline.
 *
 * <p>Its own module rather than an export from the panel: exporting a constant next to a
 * component trips the react-refresh lint rule, and a second copy of a 29-entry map is exactly
 * how the two views would end up styling the same action differently.
 *
 * <p>⚠️ The backend gate ActivityActionTypeParityTest parses this literal out of THIS file by
 * name. Moving or renaming it fails that gate loudly — intended, but the gate moves with it.
 */
/** Neutral styling for an action_type the frontend has not been taught yet. See the lookup below. */
export const UNKNOWN_ACTION_CONFIG = {
  icon: Activity,
  color: 'text-surface-400',
  bgColor: 'bg-surface-500/10',
}

/**
 * Exported so the admin user card's timeline styles log rows identically without a second copy
 * of this map. A new ActivityActionType already costs four files (enum, the union in types, this
 * map, three locale files); a duplicate here would quietly make it five, and the one nobody
 * updated is the one that renders the wrong icon.
 *
 * ⚠️ The `ActivityActionTypeParityTest` gate parses this literal out of THIS file by name. Moving
 * or renaming it fails that gate loudly — which is intended, but means the gate moves with it.
 */
export const ACTION_CONFIG: Record<
  ActivityActionType,
  { icon: typeof CalendarPlus; color: string; bgColor: string }
> = {
  RESERVATION_CREATED: {
    icon: CalendarPlus,
    color: 'text-green-400',
    bgColor: 'bg-green-500/10',
  },
  RESERVATION_REACTIVATED: {
    icon: CalendarCheck,
    color: 'text-green-400',
    bgColor: 'bg-green-500/10',
  },
  RESERVATION_CANCELLED: {
    icon: CalendarX,
    color: 'text-rose-400',
    bgColor: 'bg-rose-500/10',
  },
  EVENT_RESERVATION_CREATED: {
    icon: CalendarPlus,
    color: 'text-blue-400',
    bgColor: 'bg-blue-500/10',
  },
  EVENT_RESERVATION_UPDATED: {
    icon: CalendarCheck,
    color: 'text-blue-400',
    bgColor: 'bg-blue-500/10',
  },
  EVENT_RESERVATION_CANCELLED: {
    icon: CalendarX,
    color: 'text-rose-400',
    bgColor: 'bg-rose-500/10',
  },
  RESERVATION_UPDATED: {
    icon: CalendarCheck,
    color: 'text-green-400',
    bgColor: 'bg-green-500/10',
  },
  RESERVATION_CANCELLED_BY_ADMIN: {
    icon: ShieldAlert,
    color: 'text-amber-400',
    bgColor: 'bg-amber-500/10',
  },
  ADMIN_SLOT_CREATED: {
    icon: CalendarPlus,
    color: 'text-indigo-400',
    bgColor: 'bg-indigo-500/10',
  },
  ADMIN_SLOT_UPDATED: {
    icon: Settings,
    color: 'text-indigo-400',
    bgColor: 'bg-indigo-500/10',
  },
  ADMIN_SLOT_DELETED: {
    icon: Trash2,
    color: 'text-indigo-400',
    bgColor: 'bg-indigo-500/10',
  },
  ADMIN_SLOT_BLOCKED: {
    icon: Lock,
    color: 'text-indigo-400',
    bgColor: 'bg-indigo-500/10',
  },
  ADMIN_SLOT_UNBLOCKED: {
    icon: Unlock,
    color: 'text-indigo-400',
    bgColor: 'bg-indigo-500/10',
  },
  ADMIN_EVENT_CREATED: {
    icon: CalendarPlus,
    color: 'text-indigo-400',
    bgColor: 'bg-indigo-500/10',
  },
  ADMIN_EVENT_UPDATED: {
    icon: Settings,
    color: 'text-indigo-400',
    bgColor: 'bg-indigo-500/10',
  },
  ADMIN_EVENT_DELETED: {
    icon: Trash2,
    color: 'text-indigo-400',
    bgColor: 'bg-indigo-500/10',
  },
  ADMIN_USER_MAKE_ADMIN: {
    icon: UserCheck,
    color: 'text-orange-400',
    bgColor: 'bg-orange-500/10',
  },
  ADMIN_USER_ADMIN_REMOVED: {
    icon: UserMinus,
    color: 'text-orange-400',
    bgColor: 'bg-orange-500/10',
  },
  ADMIN_USER_DELETED: {
    icon: UserX,
    color: 'text-red-400',
    bgColor: 'bg-red-500/10',
  },
  ADMIN_USER_FORCE_LOGOUT: {
    icon: LogOut,
    color: 'text-orange-400',
    bgColor: 'bg-orange-500/10',
  },
  ADMIN_USER_ATHLETE_TOGGLED: {
    icon: UserCheck,
    color: 'text-indigo-400',
    bgColor: 'bg-indigo-500/10',
  },
  ADMIN_TRAINING_CREATED: {
    icon: CalendarPlus,
    color: 'text-indigo-400',
    bgColor: 'bg-indigo-500/10',
  },
  ADMIN_TRAINING_UPDATED: {
    icon: Settings,
    color: 'text-indigo-400',
    bgColor: 'bg-indigo-500/10',
  },
  ADMIN_TRAINING_DELETED: {
    icon: Trash2,
    color: 'text-indigo-400',
    bgColor: 'bg-indigo-500/10',
  },
  ADMIN_GOAL_CREATED: {
    icon: Target,
    color: 'text-amber-400',
    bgColor: 'bg-amber-500/10',
  },
  ADMIN_GOAL_UPDATED: {
    icon: Target,
    color: 'text-amber-400',
    bgColor: 'bg-amber-500/10',
  },
  ADMIN_GOAL_DELETED: {
    icon: Trash2,
    color: 'text-amber-400',
    bgColor: 'bg-amber-500/10',
  },
  ADMIN_GOAL_ACHIEVED: {
    icon: Trophy,
    color: 'text-amber-400',
    bgColor: 'bg-amber-500/10',
  },
  ADMIN_GOAL_REOPENED: {
    icon: RotateCcw,
    color: 'text-amber-400',
    bgColor: 'bg-amber-500/10',
  },
}
