import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Download, Globe, Lock, Mountain, Plus } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Link, useSearchParams } from 'react-router-dom'
import { AscentFilters } from './AscentFilters'
import { AscentFormModal } from './AscentFormModal'
import { AscentStatsPanel } from './AscentStatsPanel'
import { AscentTable } from './AscentTable'
import { ascentApi } from '../../api/client'
import { useAuth } from '../../context/AuthContext'
import { Button } from '../ui/Button'
import { ConfirmModal } from '../ui/ConfirmModal'
import { LoadingSpinner } from '../ui/LoadingSpinner'
import { QueryError } from '../ui/QueryError'
import { getErrorMessage } from '../../utils/errors'
import { keepWithinEntity } from '../../utils/queryEntity'
import {
  areasIn,
  EMPTY_FILTERS,
  filterAscents,
  hasActiveFilters,
  sortAscents,
  type AscentFilterState,
  type AscentSortKey,
  type SortDirection,
} from './ascentFiltering'
import type { AscentAdapter } from './ascentAdapter'
import type { Ascent, AscentTerrain, SaveAscent } from '../../types'

interface AscentsSectionProps {
  api: AscentAdapter
  /** 'me' for the athlete, the athlete id in the coach panel. */
  scopeKey: string
  /** Name shown in the exported file when the coach is looking at somebody else's logbook. */
  scopeLabel?: string
  isCoachView?: boolean
}

/**
 * The climbing logbook, one component for both roles.
 *
 * The coach's read-only view is not a flag checked at each button — it falls out of the adapter
 * having no `mutations`, so there is no render in which a write control exists without a way to
 * perform the write.
 */
export function AscentsSection({ api, scopeKey, scopeLabel, isCoachView }: AscentsSectionProps) {
  const { t } = useTranslation('ascents')
  const queryClient = useQueryClient()
  const { user } = useAuth()
  const isPublic = user?.ascentsPublic ?? true

  // In the URL: rock and mountain are two different logbooks, and a link to one should come
  // back to the same one. `?ter=` rather than `terrain=` to stay short next to ?tab= and ?cal=
  const [searchParams, setSearchParams] = useSearchParams()
  const terrain: AscentTerrain = searchParams.get('ter') === 'mountain' ? 'MOUNTAIN' : 'ROCK'
  const [year, setYear] = useState('')
  const [filters, setFilters] = useState<AscentFilterState>(EMPTY_FILTERS)
  const [sortKey, setSortKey] = useState<AscentSortKey>('date')
  const [sortDirection, setSortDirection] = useState<SortDirection>('desc')
  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<Ascent | null>(null)
  const [deleting, setDeleting] = useState<Ascent | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [exporting, setExporting] = useState(false)

  const logKey = ['ascents', 'log', scopeKey, terrain, year] as const
  const logQuery = useQuery({
    queryKey: logKey,
    queryFn: () => api.getLog(terrain, year || undefined),
    placeholderData: (previous, previousQuery) => keepWithinEntity(previous, previousQuery, logKey, 1),
  })

  // The catalogue is the same for everybody and never changes at runtime
  const optionsQuery = useQuery({
    queryKey: ['ascents', 'options'],
    queryFn: ascentApi.getOptions,
    staleTime: Infinity,
    gcTime: Infinity,
  })

  const log = logQuery.data
  const entries = useMemo(() => log?.entries ?? [], [log])
  const visible = useMemo(
    () => sortAscents(filterAscents(entries, filters), sortKey, sortDirection),
    [entries, filters, sortKey, sortDirection],
  )
  const areas = useMemo(() => areasIn(entries), [entries])

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['ascents', 'log', scopeKey] })
    queryClient.invalidateQueries({ queryKey: ['ascents', 'stats', scopeKey] })
    // The public feed mixes both terrains, so any write can change it
    queryClient.invalidateQueries({ queryKey: ['ascents', 'public'] })
  }

  const switchTerrain = (next: AscentTerrain) => {
    const params = new URLSearchParams(searchParams)
    // Rock is the default, so it stays out of the URL — the same shape as ?tab= on the page above
    if (next === 'MOUNTAIN') params.set('ter', 'mountain')
    else params.delete('ter')
    setSearchParams(params, { replace: true })
    // The two logbooks have different years and different filters; carrying either across
    // would show "no results" for a filter the athlete cannot see
    setYear('')
    setFilters(EMPTY_FILTERS)
  }

  const saveMutation = useMutation({
    mutationFn: (data: SaveAscent) => {
      const mutations = api.mutations
      if (!mutations) throw new Error('read-only')
      return editing ? mutations.update(editing.id, data) : mutations.create(data)
    },
    onSuccess: () => {
      setActionError(null)
      setFormOpen(false)
      setEditing(null)
      invalidate()
    },
    onError: (error) => setActionError(getErrorMessage(error)),
  })

  const deleteMutation = useMutation({
    mutationFn: (ascentId: string) => {
      const mutations = api.mutations
      if (!mutations) throw new Error('read-only')
      return mutations.remove(ascentId)
    },
    onSuccess: () => {
      setActionError(null)
      setDeleting(null)
      invalidate()
    },
    onError: (error) => { setActionError(getErrorMessage(error)); setDeleting(null) },
  })

  const handleSort = (key: AscentSortKey) => {
    if (key === sortKey) {
      setSortDirection(current => (current === 'asc' ? 'desc' : 'asc'))
    } else {
      setSortKey(key)
      setSortDirection(key === 'date' || key === 'grade' || key === 'stars' ? 'desc' : 'asc')
    }
  }

  const handleExport = async (formatKind: 'xlsx' | 'pdf') => {
    if (visible.length === 0) return
    setExporting(true)
    setActionError(null)
    try {
      // Two levels of laziness: this module carries the embedded font, and the libraries
      // inside it are imported later still — neither belongs in the tab's own chunk
      const exportModule = await import('./ascentExport')
      await exportModule.exportAscents(formatKind, {
        entries: visible,
        terrain,
        year: log?.selectedYear ?? null,
        filters,
        athleteName: isCoachView ? scopeLabel : undefined,
        labels: {
          title: t('export.fileTitle'),
          summary: t('export.summary', {
            year: log?.selectedYear ?? t('filters.allYears'),
            discipline: filters.discipline === 'all'
              ? t('filters.all')
              : t(`discipline.${filters.discipline}`),
            style: filters.style === 'all' ? t('filters.all') : t(`style.${filters.style}`),
            count: visible.length,
          }),
          // The two terrains export different columns, because they hold different facts
          columns: terrain === 'MOUNTAIN'
            ? [
              t('table.date'), t('table.season'), t('table.area'), t('table.summit'),
              t('table.route'), t('table.grade'), t('table.originalGrade'), t('table.style'),
              t('table.lengthMeters'), t('table.pitches'), t('table.duration'),
              t('table.led'), t('table.ledPitches'), t('table.partners'), t('table.comment'),
            ]
            : [
              t('table.date'), t('table.discipline'), t('table.area'), t('table.crag'),
              t('table.route'), t('table.grade'), t('table.style'), t('table.attempts'),
              t('table.stars'), t('table.comment'),
            ],
          disciplines: {
            SPORT: t('discipline.SPORT'),
            BOULDER: t('discipline.BOULDER'),
            TRAD: t('discipline.TRAD'),
          },
          styles: {
            OS: t('style.OS'), FLASH: t('style.FLASH'), RP: t('style.RP'),
            TR: t('style.TR'), SOLO: t('style.SOLO'), FREE_SOLO: t('style.FREE_SOLO'),
            A0: t('style.A0'), OS_GU: t('style.OS_GU'), FLASH_GU: t('style.FLASH_GU'),
            GU: t('style.GU'), HP: t('style.HP'),
          },
          seasons: { summer: t('season.summer'), winter: t('season.winter') },
        },
      })
    } catch (error) {
      setActionError(getErrorMessage(error) || t('export.failed'))
    } finally {
      setExporting(false)
    }
  }

  if (logQuery.isLoading || optionsQuery.isLoading) {
    return <div className="flex justify-center py-12"><LoadingSpinner size="lg" /></div>
  }

  if (logQuery.isError) {
    return <QueryError error={logQuery.error} onRetry={() => logQuery.refetch()} />
  }

  const options = optionsQuery.data
  const canWrite = Boolean(api.mutations)
  const availableYears = log?.availableYears ?? []

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex flex-wrap items-center gap-3">
          <h2 className="flex items-center gap-2 text-lg font-semibold text-surface-100">
            <Mountain className="w-5 h-5 text-surface-400" aria-hidden="true" />
            {t('title')}
          </h2>
          <div className="flex gap-1 p-0.5 bg-surface-800 border border-surface-700 rounded-lg"
               role="group" aria-label={t('terrain.label')}>
            {(['ROCK', 'MOUNTAIN'] as const).map(option => (
              <button
                key={option}
                type="button"
                onClick={() => switchTerrain(option)}
                aria-pressed={terrain === option}
                className={`px-3 py-1 text-xs font-medium rounded-md transition-colors ${
                  terrain === option ? 'bg-primary-600 text-white' : 'text-surface-400 hover:text-surface-200'
                }`}
              >
                {t(`terrain.${option}`)}
              </button>
            ))}
          </div>
        </div>
        <div className="flex items-center gap-2">
          {visible.length > 0 && (
            <>
              <Button variant="secondary" size="sm" onClick={() => handleExport('xlsx')} loading={exporting}>
                <Download className="w-4 h-4 mr-1.5" aria-hidden="true" />
                {t('export.xlsx')}
              </Button>
              <Button variant="secondary" size="sm" onClick={() => handleExport('pdf')} loading={exporting}>
                <Download className="w-4 h-4 mr-1.5" aria-hidden="true" />
                {t('export.pdf')}
              </Button>
            </>
          )}
          {canWrite && options && (
            <Button size="sm" onClick={() => { setEditing(null); setFormOpen(true) }}>
              <Plus className="w-4 h-4 mr-1.5" aria-hidden="true" />
              {t('add')}
            </Button>
          )}
        </div>
      </div>

      {actionError && (
        <p className="text-sm text-rose-400/90" role="alert">{actionError}</p>
      )}

      {/* The other half of an opt-out: it only works if people know it exists. Shown on the
          athlete's own logbook, never on the coach's view of somebody else's. */}
      {!isCoachView && canWrite && (
        <p className="flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-surface-500">
          {isPublic ? (
            <><Globe className="w-3.5 h-3.5" aria-hidden="true" />{t('privacy.public')}</>
          ) : (
            <><Lock className="w-3.5 h-3.5" aria-hidden="true" />{t('privacy.hidden')}</>
          )}
          <Link to="/settings" className="underline hover:text-surface-300 transition">
            {t('privacy.manage')}
          </Link>
          <Link
            to="/polityka-prywatnosci#lista-przejsc"
            className="underline hover:text-surface-300 transition"
          >
            {t('privacy.policy')}
          </Link>
        </p>
      )}

      {log && log.totalCount === 0 ? (
        <div className="bg-surface-900 rounded-xl border border-surface-800 p-8 text-center">
          <Mountain className="w-8 h-8 mx-auto mb-3 text-surface-600" aria-hidden="true" />
          <h3 className="text-surface-200 font-medium mb-1">{t('empty.title')}</h3>
          <p className="text-sm text-surface-500">
            {isCoachView ? t('empty.coach') : t(terrain === 'MOUNTAIN' ? 'empty.hintMountain' : 'empty.hint')}
          </p>
        </div>
      ) : (
        <>
          {options && (
            <AscentFilters
              filters={filters}
              onChange={setFilters}
              year={year || String(log?.selectedYear ?? 'all')}
              onYearChange={setYear}
              availableYears={availableYears}
              areas={areas}
              options={options}
              terrain={terrain}
              matchCount={visible.length}
            />
          )}

          {visible.length === 0 ? (
            <div className="bg-surface-900 rounded-xl border border-surface-800 p-8 text-center">
              <p className="text-sm text-surface-500">
                {hasActiveFilters(filters) ? t('empty.noResults') : t('empty.year')}
              </p>
            </div>
          ) : (
            <AscentTable
              terrain={terrain}
              entries={visible}
              sortKey={sortKey}
              sortDirection={sortDirection}
              onSort={handleSort}
              onEdit={canWrite ? (ascent) => { setEditing(ascent); setFormOpen(true) } : undefined}
              onDelete={canWrite ? setDeleting : undefined}
            />
          )}

          <AscentStatsPanel
            api={api}
            scopeKey={scopeKey}
            terrain={terrain}
            year={year}
            selectedYear={log?.selectedYear ?? null}
          />
        </>
      )}

      {options && canWrite && (
        <AscentFormModal
          isOpen={formOpen}
          onClose={() => { setFormOpen(false); setEditing(null); setActionError(null) }}
          onSubmit={(data) => saveMutation.mutate(data)}
          saving={saveMutation.isPending}
          error={saveMutation.isError ? getErrorMessage(saveMutation.error) : null}
          options={options}
          places={log?.places ?? []}
          terrain={terrain}
          editing={editing}
        />
      )}

      <ConfirmModal
        isOpen={deleting !== null}
        onClose={() => setDeleting(null)}
        onConfirm={() => deleting && deleteMutation.mutate(deleting.id)}
        title={t('deleteConfirm.title')}
        message={t('deleteConfirm.message', { route: deleting?.routeName ?? '' })}
        confirmText={t('delete')}
        variant="danger"
      />
    </div>
  )
}
