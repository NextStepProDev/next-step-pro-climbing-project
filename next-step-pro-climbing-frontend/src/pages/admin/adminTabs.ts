import {
  Calendar,
  CalendarPlus,
  Users,
  Clock,
  ClipboardList,
  Activity,
  User,
  Image,
  Newspaper,
  BookOpen,
  Library,
  Mail,
  HardDrive,
  Video,
  Home,
  Dumbbell,
  Wallet,
  type LucideIcon,
} from 'lucide-react'
import type { AdminNotifications } from '../../types'

/**
 * The admin panel's map: what tabs exist, where they live, and how they group.
 *
 * Deliberately a module without a component. Three places read it — the group menus, the hub
 * tiles and the command palette — and `react-refresh/only-export-components` (eslint.config.js)
 * forbids exporting a constant alongside a component. Same reason `components/ui/modalClose.ts`
 * and `components/admin/activityActionConfig.ts` live on their own.
 */
export interface AdminTab {
  path: string
  /** `tabs.*` in the `admin` namespace — the short label on a pill, a tile and a palette row. */
  labelKey: string
  /** `hub.hints.*` — one sentence under the tile: what this tab is for. */
  hintKey: string
  /**
   * `palette.keywords.*` — words the palette matches that the label does not contain ("hero",
   * "cennik"). Translated, not shared: a Polish admin searches in Polish.
   */
  keywordsKey: string
  icon: LucideIcon
}

export interface AdminTabGroup {
  groupKey: string
  tabs: AdminTab[]
}

function tab(name: string, path: string, icon: LucideIcon): AdminTab {
  return {
    path,
    labelKey: `tabs.${name}`,
    hintKey: `hub.hints.${name}`,
    keywordsKey: `palette.keywords.${name}`,
    icon,
  }
}

export const adminTabGroups: AdminTabGroup[] = [
  {
    groupKey: 'tabGroups.calendar',
    tabs: [
      tab('slots', '/admin/slots', Clock),
      tab('reservations', '/admin/reservations', ClipboardList),
      tab('events', '/admin/events', Calendar),
      tab('requests', '/admin/requests', CalendarPlus),
      tab('trainingCalendars', '/admin/training-calendars', Dumbbell),
      tab('settlements', '/admin/settlements', Wallet),
    ],
  },
  {
    groupKey: 'tabGroups.content',
    tabs: [
      tab('news', '/admin/news', Newspaper),
      tab('courses', '/admin/courses', BookOpen),
      tab('gallery', '/admin/gallery', Image),
      tab('videos', '/admin/videos', Video),
    ],
  },
  {
    groupKey: 'tabGroups.team',
    tabs: [
      tab('instructors', '/admin/instructors', User),
      tab('competitors', '/admin/competitors', Users),
    ],
  },
  {
    groupKey: 'tabGroups.system',
    tabs: [
      tab('users', '/admin/users', Users),
      tab('mail', '/admin/mail', Mail),
      tab('activity', '/admin/activity', Activity),
      tab('assets', '/admin/assets', Library),
      tab('storage', '/admin/storage', HardDrive),
      tab('site', '/admin/site', Home),
    ],
  },
]

/** Every tab, flattened — the palette searches one list, not four. */
export const adminTabs: AdminTab[] = adminTabGroups.flatMap((group) => group.tabs)

/**
 * Notification counters keyed by tab path. A pure function rather than an inline record because
 * the group buttons, the hub tiles and the tab rows all need the same numbers.
 */
export function adminTabBadges(notifications: AdminNotifications | undefined): Record<string, number> {
  return {
    '/admin/requests': notifications?.pendingRequests ?? 0,
    // Waitlist joins count together with new reservations — both views (the "Waitlists"
    // section and the reservation list) live in this tab, and entering it clears both
    '/admin/reservations': (notifications?.newReservations ?? 0) + (notifications?.newWaitlistEntries ?? 0),
    // Unread athlete activity (new trainings/completions/comments) across all athletes
    '/admin/training-calendars': notifications?.athleteActivity ?? 0,
    // Accounts confirmed since this admin last opened the Users list
    '/admin/users': notifications?.newUsers ?? 0,
  }
}

/**
 * Is `pathname` inside `tabPath`?
 *
 * The boundary slash matters: `/admin/slots` must not light up a tab at `/admin/slot`, and the
 * hub at `/admin` must not light up for every route in the panel. The old inline check compared
 * `/admin` for equality and everything else with a bare `startsWith` — safe only as long as no
 * tab path was a prefix of another, which adding `/admin/slots` was about to break.
 */
export function isTabActive(pathname: string, tabPath: string): boolean {
  return pathname === tabPath || pathname.startsWith(tabPath + '/')
}

/** The badge on a collapsed group is the sum of its tabs' — folding a list must not hide a dot. */
export function groupBadgeCount(group: AdminTabGroup, badges: Record<string, number>): number {
  return group.tabs.reduce((sum, t) => sum + (badges[t.path] ?? 0), 0)
}
