import { useEffect, useRef, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { ChevronDown, Search } from 'lucide-react'
import clsx from 'clsx'
import {
  adminTabGroups,
  adminTabBadges,
  groupBadgeCount,
  isTabActive,
  type AdminTab,
} from '../../pages/admin/adminTabs'
import { isMacLike } from './adminPaletteSearch'
import type { AdminNotifications } from '../../types'

interface AdminNavProps {
  notifications: AdminNotifications | undefined
  onOpenPalette: () => void
}

function Badge({ count }: { count: number }) {
  if (count <= 0) return null
  return (
    <span className="ml-0.5 min-w-5 h-5 px-1.5 inline-flex items-center justify-center rounded-full bg-rose-500 text-white text-[11px] font-bold leading-none">
      {count}
    </span>
  )
}

/**
 * The panel's navigation: one row of four group menus plus the palette trigger.
 *
 * Replaces a wrapped wall of eighteen equally-weighted pills. Grouping was already there, but the
 * group headings were 11px labels above a flat field of links — nothing to aim at, so finding a
 * tab meant reading all eighteen. Four buttons are four targets.
 */
export function AdminNav({ notifications, onOpenPalette }: AdminNavProps) {
  const { t } = useTranslation('admin')
  const location = useLocation()
  // The open menu remembers which route it was opened on, so a navigation closes it by making the
  // record stale — no effect, no second render. Links close it themselves; this covers the ways
  // out that the nav does not own, such as jumping straight from the palette.
  const [opened, setOpened] = useState<{ group: string; at: string } | null>(null)
  const openGroup = opened && opened.at === location.pathname ? opened.group : null
  const containerRef = useRef<HTMLDivElement>(null)

  const badges = adminTabBadges(notifications)
  const activeTab: AdminTab | undefined = adminTabGroups
    .flatMap((group) => group.tabs)
    .find((tab) => isTabActive(location.pathname, tab.path))

  // Close on click outside — the same pattern as the public navbar's dropdowns and
  // `UserSearchSelect`.
  useEffect(() => {
    if (!openGroup) return
    function handleClickOutside(e: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpened(null)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [openGroup])

  return (
    <div
      ref={containerRef}
      // Escape is handled HERE, on the container, not on `document`. `Modal` and `ConfirmModal`
      // listen for Escape on the document without stopping it, and two listeners on the same node
      // cannot be separated with `stopPropagation`. Handling it while focus is inside the nav lets
      // the event stop bubbling before it ever reaches them.
      onKeyDown={(e) => {
        if (e.key === 'Escape' && openGroup) {
          e.stopPropagation()
          setOpened(null)
        }
      }}
      // `relative` is load-bearing on phones: below `sm` a group's panel has no positioned wrapper
      // of its own, so it resolves against this container and opens full-width under the whole row.
      className="relative flex flex-wrap items-center gap-1.5 mb-6 border-b border-surface-800 pb-4"
    >
      {adminTabGroups.map((group) => {
        const isOpen = openGroup === group.groupKey
        const holdsActive = !!activeTab && group.tabs.includes(activeTab)
        const groupCount = groupBadgeCount(group, badges)
        const panelId = `admin-nav-${group.groupKey.replace('.', '-')}`

        return (
          <div key={group.groupKey} className="sm:relative">
            <button
              type="button"
              aria-expanded={isOpen}
              aria-controls={panelId}
              onClick={() => setOpened(isOpen ? null : { group: group.groupKey, at: location.pathname })}
              className={clsx(
                'flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-medium transition-colors max-w-[16rem]',
                holdsActive
                  ? 'bg-primary-500/10 text-primary-400'
                  : 'text-surface-400 hover:text-surface-200 hover:bg-surface-800'
              )}
            >
              <span className="truncate">
                {/* The active tab's name rides along in its group's button. Without it "where am
                    I" would need a click, and `AdminSlotsPanel` is the one panel that renders no
                    heading of its own — so on Slots nothing on screen would answer. */}
                {holdsActive && activeTab
                  ? `${t(group.groupKey)}: ${t(activeTab.labelKey)}`
                  : t(group.groupKey)}
              </span>
              {/* The collapsed group carries its tabs' badges, so folding a list never hides a dot. */}
              <Badge count={groupCount} />
              <ChevronDown
                className={clsx('w-4 h-4 shrink-0 transition-transform', isOpen && 'rotate-180')}
              />
            </button>

            {isOpen && (
              <div
                id={panelId}
                className="absolute top-full left-0 mt-2 z-40 w-full sm:w-64 bg-surface-900 border border-surface-700 rounded-xl shadow-lg shadow-black/30 overflow-hidden py-1"
              >
                {group.tabs.map((tab) => {
                  const Icon = tab.icon
                  const isActive = tab === activeTab
                  return (
                    <Link
                      key={tab.path}
                      to={tab.path}
                      onClick={() => setOpened(null)}
                      aria-current={isActive ? 'page' : undefined}
                      className={clsx(
                        'flex items-center gap-2 px-4 py-2.5 text-sm transition-colors',
                        isActive
                          ? 'text-primary-400 bg-primary-500/10'
                          : 'text-surface-300 hover:bg-surface-800 hover:text-surface-100'
                      )}
                    >
                      <Icon className="w-4 h-4 shrink-0" />
                      <span className="truncate">{t(tab.labelKey)}</span>
                      <span className="ml-auto flex items-center">
                        <Badge count={badges[tab.path] ?? 0} />
                      </span>
                    </Link>
                  )
                })}
              </div>
            )}
          </div>
        )
      })}

      <button
        type="button"
        onClick={onOpenPalette}
        title={t('palette.open')}
        aria-label={t('palette.open')}
        className="ml-auto flex items-center gap-2 px-3 py-1.5 rounded-lg text-sm text-surface-400 hover:text-surface-200 hover:bg-surface-800 transition-colors"
      >
        <Search className="w-4 h-4 shrink-0" />
        <span className="max-sm:sr-only">{t('palette.open')}</span>
        {/* No shortcut hint on a phone — there is no keyboard to press it on. */}
        <kbd className="max-sm:hidden px-1.5 py-0.5 rounded border border-surface-700 text-[11px] text-surface-500 font-sans">
          {isMacLike() ? '⌘K' : 'Ctrl K'}
        </kbd>
      </button>
    </div>
  )
}
