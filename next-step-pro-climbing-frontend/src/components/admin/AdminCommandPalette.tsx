import { useEffect, useMemo, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Search } from 'lucide-react'
import clsx from 'clsx'
import { adminTabGroups, adminTabs } from '../../pages/admin/adminTabs'
import { filterAdminTabs, type PaletteEntry } from './adminPaletteSearch'
import { useFocusTrap } from '../../utils/useFocusTrap'

interface AdminCommandPaletteProps {
  onClose: () => void
}

/**
 * Type-to-jump over the panel's eighteen tabs.
 *
 * Built on `createPortal` + `useFocusTrap` rather than on `Modal`, following `ConfirmModal`:
 * `Modal` owns a title bar and the `confirmClose` handshake, neither of which a search box wants.
 *
 * Deliberately static — no request, no user lookup, no actions. It answers "where is the tab I
 * mean", which is the whole problem eighteen tabs created.
 *
 * Mounted only while open (the caller gates it), so every opening starts on an empty query and a
 * highlight at the top without a single line of reset code.
 */
export function AdminCommandPalette({ onClose }: AdminCommandPaletteProps) {
  const { t } = useTranslation('admin')
  const navigate = useNavigate()
  const [query, setQuery] = useState('')
  // The highlight remembers which query it belongs to. Deriving the index this way resets it on
  // every keystroke without an effect — and an effect here would be a second render per character.
  const [aim, setAim] = useState<{ query: string; index: number }>({ query: '', index: 0 })
  const trapRef = useFocusTrap(true)
  const listRef = useRef<HTMLUListElement>(null)

  const entries: PaletteEntry[] = useMemo(
    () => adminTabs.map((tab) => ({ tab, label: t(tab.labelKey), keywords: t(tab.keywordsKey) })),
    [t]
  )
  const groupOf = useMemo(
    () => new Map(adminTabGroups.flatMap((g) => g.tabs.map((tab) => [tab.path, g.groupKey] as const))),
    []
  )
  const results = useMemo(() => filterAdminTabs(entries, query), [entries, query])

  const highlight = aim.query === query && aim.index < results.length ? aim.index : 0

  /**
   * Rows with a group heading attached wherever the group changes. With an empty query the groups
   * are contiguous, so this reproduces the panel's own four sections; while filtering it simply
   * labels each run of results.
   */
  const rows = useMemo(
    () =>
      results.map((entry, index) => {
        const groupKey = groupOf.get(entry.tab.path) ?? null
        const previous = index > 0 ? groupOf.get(results[index - 1].tab.path) ?? null : null
        return { entry, header: groupKey && groupKey !== previous ? groupKey : null }
      }),
    [results, groupOf]
  )

  // Keep the highlighted row inside the scroll box as the arrows walk past its edge.
  useEffect(() => {
    listRef.current
      ?.querySelector(`[data-index="${highlight}"]`)
      ?.scrollIntoView({ block: 'nearest' })
  }, [highlight, results])

  function go(path: string) {
    navigate(path)
    onClose()
  }

  function move(delta: number) {
    if (results.length === 0) return
    setAim({ query, index: (highlight + delta + results.length) % results.length })
  }

  function handleKeyDown(e: React.KeyboardEvent) {
    if (e.key === 'Escape') {
      // Stop it here: `Modal` listens for Escape on `document` without stopping it, and while the
      // palette refuses to open over a dialog, nothing should rely on that staying true.
      e.stopPropagation()
      onClose()
      return
    }
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      move(1)
      return
    }
    if (e.key === 'ArrowUp') {
      e.preventDefault()
      move(-1)
      return
    }
    if (e.key === 'Enter') {
      e.preventDefault()
      const picked = results[highlight]
      if (picked) go(picked.tab.path)
    }
  }

  return createPortal(
    <div className="fixed inset-0 z-50 flex items-start justify-center p-4 pt-[12vh]">
      <div
        className="absolute inset-0 bg-black/60 backdrop-blur-sm"
        onClick={onClose}
        aria-hidden="true"
      />
      <div
        ref={trapRef}
        role="dialog"
        aria-modal="true"
        aria-label={t('palette.open')}
        onKeyDown={handleKeyDown}
        className="relative w-full max-w-lg bg-surface-900 border border-surface-700 rounded-xl shadow-lg shadow-black/40 overflow-hidden"
      >
        <div className="relative border-b border-surface-800">
          <Search className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-surface-500 pointer-events-none" />
          <input
            type="text"
            role="combobox"
            aria-expanded="true"
            aria-controls="admin-palette-list"
            aria-activedescendant={results[highlight] ? `admin-palette-option-${highlight}` : undefined}
            autoComplete="off"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder={t('palette.placeholder')}
            className="w-full bg-transparent pl-11 pr-4 py-3 text-surface-100 placeholder:text-surface-500 focus:outline-none"
          />
        </div>

        {rows.length === 0 ? (
          <div className="px-4 py-6 text-sm text-surface-500 text-center">{t('palette.empty')}</div>
        ) : (
          <ul
            ref={listRef}
            id="admin-palette-list"
            role="listbox"
            className="max-h-80 overflow-y-auto py-1"
          >
            {rows.map(({ entry, header }, index) => {
              const Icon = entry.tab.icon
              return (
                <li key={entry.tab.path}>
                  {header && (
                    <div className="px-4 pt-3 pb-1 text-[11px] font-semibold uppercase tracking-wider text-surface-500">
                      {t(header)}
                    </div>
                  )}
                  <button
                    type="button"
                    id={`admin-palette-option-${index}`}
                    data-index={index}
                    role="option"
                    aria-selected={index === highlight}
                    // `onMouseDown` + `preventDefault` keeps the input focused, so the highlight
                    // and the arrow keys survive a hover-then-click.
                    onMouseDown={(e) => e.preventDefault()}
                    onClick={() => go(entry.tab.path)}
                    onMouseEnter={() => setAim({ query, index })}
                    className={clsx(
                      'w-full flex items-start gap-3 px-4 py-2 text-left transition-colors',
                      index === highlight ? 'bg-primary-500/10' : 'hover:bg-surface-800'
                    )}
                  >
                    <Icon
                      className={clsx(
                        'w-4 h-4 mt-0.5 shrink-0',
                        index === highlight ? 'text-primary-400' : 'text-surface-400'
                      )}
                    />
                    <span className="min-w-0">
                      <span
                        className={clsx(
                          'block text-sm font-medium truncate',
                          index === highlight ? 'text-primary-300' : 'text-surface-100'
                        )}
                      >
                        {entry.label}
                      </span>
                      <span className="block text-xs text-surface-500 truncate">{entry.keywords}</span>
                    </span>
                  </button>
                </li>
              )
            })}
          </ul>
        )}

        <div className="px-4 py-2 border-t border-surface-800 text-[11px] text-surface-500">
          {t('palette.hintKeys')}
        </div>
      </div>
    </div>,
    document.body
  )
}
