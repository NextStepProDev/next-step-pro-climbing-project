import { useState, useMemo } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { format } from 'date-fns'
import { Shield, ShieldOff, Trash2, Search, ChevronLeft, ChevronRight, Mail, MailWarning, ArrowUp, ArrowDown, ArrowUpDown, LogOut, Dumbbell } from 'lucide-react'
import { adminApi } from '../../api/client'
import { LoadingSpinner } from '../../components/ui/LoadingSpinner'
import { QueryError } from '../../components/ui/QueryError'
import { Button } from '../../components/ui/Button'
import { ConfirmModal } from '../../components/ui/ConfirmModal'
import { UserStatsView } from '../../components/admin/userstats/UserStatsView'
import { getErrorMessage } from '../../utils/errors'

const PAGE_SIZE = 50

type ViewTab = 'list' | 'stats'
type NewsletterFilter = 'all' | 'subscribed' | 'unsubscribed'
type SortKey = 'user' | 'email' | 'role' | 'createdAt'
type SortDir = 'asc' | 'desc'

export function AdminUsersPanel() {
  const { t } = useTranslation('admin')
  const [searchParams, setSearchParams] = useSearchParams()
  const view: ViewTab = searchParams.get('view') === 'stats' ? 'stats' : 'list'
  const switchView = (next: ViewTab) => {
    const params = new URLSearchParams(searchParams)
    if (next === 'stats') params.set('view', 'stats')
    else params.delete('view')
    setSearchParams(params, { replace: true })
  }
  const [search, setSearch] = useState('')
  const [newsletterFilter, setNewsletterFilter] = useState<NewsletterFilter>('all')
  // Its own toggle rather than a fourth pill in the newsletter group: verification and newsletter
  // are different questions, and one row of buttons answering both cannot express "subscribed AND
  // unverified".
  const [unverifiedOnly, setUnverifiedOnly] = useState(false)
  const [sortKey, setSortKey] = useState<SortKey>('createdAt')
  const [sortDir, setSortDir] = useState<SortDir>('desc')
  const [page, setPage] = useState(1)
  const [confirmAction, setConfirmAction] = useState<{
    type: 'makeAdmin' | 'removeAdmin' | 'delete' | 'forceLogout' | 'unsetAthlete'
    userId: string
    userName: string
  } | null>(null)
  const queryClient = useQueryClient()
  const [actionError, setActionError] = useState<string | null>(null)

  // Not fetched on the statistics view: this component stays mounted there, and nothing on that
  // screen reads the list — the aggregate comes from its own endpoint. Without the guard, opening
  // Statistics pulls every account down a second time for nobody.
  const { data: users, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['admin', 'users'],
    queryFn: adminApi.getAllUsers,
    enabled: view === 'list',
  })

  // Both keys, always: the list renders the accounts and the statistics count them, so every
  // action here changes both screens. The statistics endpoint refuses to cache on the server for
  // exactly this reason — but React Query's global staleTime is five minutes, so without the
  // second invalidation the panel would keep serving the pre-action snapshot from memory and the
  // deliberate absence of a server cache would buy nothing.
  const refreshUserData = () => {
    queryClient.invalidateQueries({ queryKey: ['admin', 'users'] })
    queryClient.invalidateQueries({ queryKey: ['admin', 'userStats'] })
  }

  const makeAdminMutation = useMutation({
    mutationFn: adminApi.makeAdmin,
    onSuccess: () => { setActionError(null); refreshUserData() },
    onError: (err) => setActionError(getErrorMessage(err)),
  })

  const removeAdminMutation = useMutation({
    mutationFn: adminApi.removeAdmin,
    onSuccess: () => { setActionError(null); refreshUserData() },
    onError: (err) => setActionError(getErrorMessage(err)),
  })

  const deleteUserMutation = useMutation({
    mutationFn: adminApi.deleteUser,
    onSuccess: () => { setActionError(null); refreshUserData() },
    onError: (err) => setActionError(getErrorMessage(err)),
  })

  const forceLogoutMutation = useMutation({
    mutationFn: adminApi.forceLogout,
    onSuccess: () => setActionError(null),
    onError: (err) => setActionError(getErrorMessage(err)),
  })

  // Athlete flag toggle (personal training calendar access). Turning it ON is harmless; turning it
  // OFF hides the whole calendar from the athlete AND drops them from the coach roster (data is
  // kept, not deleted) — so that direction goes through a confirm.
  const setAthleteMutation = useMutation({
    mutationFn: ({ userId, isAthlete }: { userId: string; isAthlete: boolean }) =>
      adminApi.setAthlete(userId, isAthlete),
    onSuccess: () => {
      setActionError(null)
      refreshUserData()
      queryClient.invalidateQueries({ queryKey: ['admin', 'trainingCalendar', 'athletes'] })
    },
    onError: (err) => setActionError(getErrorMessage(err)),
  })

  const filtered = useMemo(() => {
    if (!users) return []

    const byNewsletter =
      newsletterFilter === 'all'
        ? users
        : users.filter((u) =>
            newsletterFilter === 'subscribed' ? u.newsletterSubscribed : !u.newsletterSubscribed,
          )

    const byVerification = unverifiedOnly
      ? byNewsletter.filter((u) => !u.emailVerified)
      : byNewsletter

    if (!search.trim()) return byVerification

    const q = search.toLowerCase().trim()
    return byVerification.filter(
      (u) =>
        u.firstName.toLowerCase().includes(q) ||
        u.lastName.toLowerCase().includes(q) ||
        u.email.toLowerCase().includes(q) ||
        `${u.firstName} ${u.lastName}`.toLowerCase().includes(q),
    )
  }, [users, search, newsletterFilter, unverifiedOnly])

  const sorted = useMemo(() => {
    const arr = [...filtered]
    const dir = sortDir === 'asc' ? 1 : -1
    arr.sort((a, b) => {
      let cmp = 0
      switch (sortKey) {
        case 'user':
          cmp = `${a.firstName} ${a.lastName}`.localeCompare(`${b.firstName} ${b.lastName}`, 'pl', { sensitivity: 'base' })
          break
        case 'email':
          cmp = a.email.localeCompare(b.email, 'pl', { sensitivity: 'base' })
          break
        case 'role':
          cmp = a.role.localeCompare(b.role)
          break
        case 'createdAt':
          cmp = new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()
          break
      }
      return cmp * dir
    })
    return arr
  }, [filtered, sortKey, sortDir])

  const totalPages = Math.max(1, Math.ceil(sorted.length / PAGE_SIZE))
  const safePage = Math.min(page, totalPages)
  const paged = sorted.slice((safePage - 1) * PAGE_SIZE, safePage * PAGE_SIZE)

  const toggleSort = (key: SortKey) => {
    if (sortKey === key) {
      setSortDir((d) => (d === 'asc' ? 'desc' : 'asc'))
    } else {
      setSortKey(key)
      setSortDir(key === 'createdAt' ? 'desc' : 'asc')
    }
    setPage(1)
  }

  const handleSearchChange = (value: string) => {
    setSearch(value)
    setPage(1)
  }

  const handleFilterChange = (value: NewsletterFilter) => {
    setNewsletterFilter(value)
    setPage(1)
  }

  const handleUnverifiedToggle = () => {
    setUnverifiedOnly((on) => !on)
    setPage(1)
  }

  const unverifiedCount = useMemo(
    () => (users ?? []).filter((u) => !u.emailVerified).length,
    [users],
  )

  const sortHeader = (key: SortKey, label: string) => (
    <th className="text-left px-4 py-3 text-sm font-medium text-surface-300">
      <button
        onClick={() => toggleSort(key)}
        className="flex items-center gap-1 hover:text-surface-100 transition-colors"
      >
        {label}
        {sortKey === key ? (
          sortDir === 'asc' ? <ArrowUp className="w-3.5 h-3.5" /> : <ArrowDown className="w-3.5 h-3.5" />
        ) : (
          <ArrowUpDown className="w-3.5 h-3.5 text-surface-600" />
        )}
      </button>
    </th>
  )

  const filterOptions: { value: NewsletterFilter; label: string }[] = [
    { value: 'all', label: t('users.newsletterFilterAll') },
    { value: 'subscribed', label: t('users.newsletterFilterSubscribed') },
    { value: 'unsubscribed', label: t('users.newsletterFilterUnsubscribed') },
  ]

  const viewTabs: { value: ViewTab; label: string }[] = [
    { value: 'list', label: t('users.listTab') },
    { value: 'stats', label: t('users.stats.tab') },
  ]

  return (
    <div>
      {/* In the URL, like the athlete panel and the user card: a link sent to oneself comes back to
          the same view. The list is the default and carries no parameter. */}
      <div className="flex gap-1.5 p-1 mb-3 bg-surface-800 border border-surface-700 rounded-lg w-fit">
        {viewTabs.map((tab) => (
          <button
            key={tab.value}
            onClick={() => switchView(tab.value)}
            aria-pressed={view === tab.value}
            className={`px-3 py-1.5 text-sm font-medium rounded-md transition-colors ${
              view === tab.value
                ? 'bg-primary-600 text-white'
                : 'text-surface-400 hover:text-surface-200'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {view === 'stats' ? (
        <UserStatsView />
      ) : (
      <>
      {actionError && (
        <div className="mb-4 p-3 bg-rose-500/10 border border-rose-500/20 rounded-lg text-sm text-rose-400">
          {actionError}
        </div>
      )}

      {/* Newsletter filter + verification filter (separate controls: two questions, two answers) */}
      <div className="flex flex-wrap items-center gap-3 mb-3">
        <div className="flex gap-1.5 p-1 bg-surface-800 border border-surface-700 rounded-lg w-fit">
          {filterOptions.map((opt) => (
            <button
              key={opt.value}
              onClick={() => handleFilterChange(opt.value)}
              className={`px-3 py-1.5 text-sm rounded-md transition-colors ${
                newsletterFilter === opt.value
                  ? 'bg-primary-500/20 text-primary-300'
                  : 'text-surface-400 hover:text-surface-200 hover:bg-surface-700/50'
              }`}
            >
              {opt.label}
            </button>
          ))}
        </div>

        <button
          onClick={handleUnverifiedToggle}
          aria-pressed={unverifiedOnly}
          className={`flex items-center gap-1.5 px-3 py-2 text-sm rounded-lg border transition-colors ${
            unverifiedOnly
              ? 'bg-amber-500/15 border-amber-500/40 text-amber-300'
              : 'bg-surface-800 border-surface-700 text-surface-400 hover:text-surface-200 hover:bg-surface-700/50'
          }`}
        >
          <MailWarning className="w-4 h-4" />
          {t('users.unverifiedFilter')}
          {unverifiedCount > 0 && (
            <span className="ml-0.5 px-1.5 py-0.5 rounded bg-amber-500/20 text-amber-300 text-xs">
              {unverifiedCount}
            </span>
          )}
        </button>
      </div>

      {/* Search */}
      <div className="relative mb-4">
        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-surface-500" />
        <input
          type="text"
          value={search}
          onChange={(e) => handleSearchChange(e.target.value)}
          placeholder={t('users.searchPlaceholder')}
          className="w-full bg-surface-800 border border-surface-700 rounded-lg pl-10 pr-4 py-2 text-surface-100 placeholder:text-surface-500 focus:outline-none focus:ring-2 focus:ring-primary-500"
        />
      </div>

      {isLoading ? (
        <LoadingSpinner />
      ) : isError ? (
        <QueryError error={error} onRetry={() => refetch()} />
      ) : (
        <>
          <div className="bg-surface-900 rounded-lg border border-surface-800 overflow-x-auto">
            <table className="w-full min-w-[700px]">
              <thead className="bg-surface-800">
                <tr>
                  {sortHeader('user', t('users.userColumn'))}
                  {sortHeader('email', t('users.emailColumn'))}
                  <th className="text-left px-4 py-3 text-sm font-medium text-surface-300">
                    {t('users.phoneColumn')}
                  </th>
                  {sortHeader('role', t('users.roleColumn'))}
                  {sortHeader('createdAt', t('users.registrationDate'))}
                  <th className="px-4 py-3"></th>
                </tr>
              </thead>
              <tbody className="divide-y divide-surface-800">
                {paged.map((user) => (
                  <tr key={user.id} className="hover:bg-surface-800/50">
                    {/* The name cell is the link, not the whole row: the last cell holds five
                        buttons, and a button inside a link is a nested interactive element — one
                        tap would both navigate and fire the action. */}
                    <td className="px-4 py-3">
                      <Link
                        to={`/admin/users/${user.id}`}
                        className="inline-flex items-center gap-1 text-surface-100 hover:text-primary-400 transition-colors group/name"
                        title={t('users.detail.open')}
                      >
                        {user.firstName} {user.lastName}
                        <ChevronRight className="w-3.5 h-3.5 shrink-0 text-surface-600 group-hover/name:text-primary-400" />
                      </Link>
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-1.5 flex-wrap">
                        <span className="text-surface-300">{user.email}</span>
                        <span title={user.newsletterSubscribed ? t('users.newsletterYes') : t('users.newsletterNo')}>
                          <Mail className={`w-3 h-3 shrink-0 ${user.newsletterSubscribed ? 'text-green-400/60' : 'text-surface-600/50'}`} />
                        </span>
                        {!user.emailVerified && (
                          <span
                            title={t('users.unverifiedHint')}
                            className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded bg-amber-500/15 border border-amber-500/30 text-amber-300 text-xs whitespace-nowrap"
                          >
                            <MailWarning className="w-3 h-3 shrink-0" />
                            {t('users.unverifiedBadge')}
                          </span>
                        )}
                      </div>
                    </td>
                    <td className="px-4 py-3 text-surface-300">{user.phone || '-'}</td>
                    <td className="px-4 py-3">
                      <span
                        className={`px-2 py-1 text-xs rounded ${
                          user.role === 'ADMIN'
                            ? 'bg-primary-500/20 text-primary-400'
                            : 'bg-surface-700 text-surface-300'
                        }`}
                      >
                        {user.role}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-surface-400 text-sm">
                      {format(new Date(user.createdAt), 'dd.MM.yyyy')}
                    </td>
                    <td className="px-4 py-3 flex gap-1">
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() =>
                          user.isAthlete
                            ? setConfirmAction({ type: 'unsetAthlete', userId: user.id, userName: `${user.firstName} ${user.lastName}` })
                            : setAthleteMutation.mutate({ userId: user.id, isAthlete: true })
                        }
                        title={user.isAthlete ? t('users.unsetAthlete') : t('users.setAthlete')}
                        className={user.isAthlete
                          ? '!text-indigo-400 hover:bg-indigo-500/10'
                          : 'text-surface-500 hover:text-indigo-300'}
                      >
                        <Dumbbell className="w-4 h-4" />
                      </Button>
                      {user.role === 'ADMIN' ? (
                        <>
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => setConfirmAction({ type: 'removeAdmin', userId: user.id, userName: `${user.firstName} ${user.lastName}` })}
                            title={t('users.revokeAdmin')}
                            className="group !text-amber-400 hover:bg-orange-500/10"
                          >
                            <Shield className="w-4 h-4 group-hover:hidden" />
                            <ShieldOff className="w-4 h-4 hidden group-hover:block text-surface-400" />
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => setConfirmAction({ type: 'forceLogout', userId: user.id, userName: `${user.firstName} ${user.lastName}` })}
                            title={t('users.forceLogout')}
                            className="text-surface-500 hover:text-surface-200 hover:bg-surface-700/50"
                          >
                            <LogOut className="w-4 h-4" />
                          </Button>
                        </>
                      ) : (
                        <>
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => setConfirmAction({ type: 'makeAdmin', userId: user.id, userName: `${user.firstName} ${user.lastName}` })}
                            title={t('users.grantAdmin')}
                            className="group text-surface-500"
                          >
                            <ShieldOff className="w-4 h-4 group-hover:hidden" />
                            <Shield className="w-4 h-4 hidden group-hover:block !text-amber-400" />
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => setConfirmAction({ type: 'forceLogout', userId: user.id, userName: `${user.firstName} ${user.lastName}` })}
                            title={t('users.forceLogout')}
                            className="text-surface-500 hover:text-surface-200 hover:bg-surface-700/50"
                          >
                            <LogOut className="w-4 h-4" />
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => setConfirmAction({ type: 'delete', userId: user.id, userName: `${user.firstName} ${user.lastName}` })}
                            title={t('users.deleteUser')}
                            className="text-rose-400/70 hover:text-rose-300/80 hover:bg-rose-500/10"
                          >
                            <Trash2 className="w-4 h-4" />
                          </Button>
                        </>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>

            {paged.length === 0 && (
              <div className="p-8 text-center text-surface-400">
                {search.trim() || newsletterFilter !== 'all'
                  ? t('users.noSearchResults')
                  : t('users.noUsers')}
              </div>
            )}
          </div>

          {/* Pagination */}
          {filtered.length > PAGE_SIZE && (
            <div className="flex items-center justify-between mt-4">
              <span className="text-sm text-surface-400">
                {t('users.userCount', { count: filtered.length })}
                {search.trim() && users ? ` (${t('users.ofTotal', { count: users.length })})` : ''}
              </span>

              <div className="flex items-center gap-2">
                <button
                  onClick={() => setPage((p) => Math.max(1, p - 1))}
                  disabled={safePage <= 1}
                  className="p-2 text-surface-400 hover:text-surface-100 hover:bg-surface-800 rounded-lg transition-colors disabled:opacity-30 disabled:cursor-not-allowed"
                >
                  <ChevronLeft className="w-4 h-4" />
                </button>

                <span className="text-sm text-surface-300 min-w-[80px] text-center">
                  {safePage} / {totalPages}
                </span>

                <button
                  onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
                  disabled={safePage >= totalPages}
                  className="p-2 text-surface-400 hover:text-surface-100 hover:bg-surface-800 rounded-lg transition-colors disabled:opacity-30 disabled:cursor-not-allowed"
                >
                  <ChevronRight className="w-4 h-4" />
                </button>
              </div>
            </div>
          )}
        </>
      )}

      <ConfirmModal
        isOpen={!!confirmAction}
        onClose={() => setConfirmAction(null)}
        onConfirm={() => {
          if (!confirmAction) return
          if (confirmAction.type === 'makeAdmin') {
            makeAdminMutation.mutate(confirmAction.userId)
          } else if (confirmAction.type === 'removeAdmin') {
            removeAdminMutation.mutate(confirmAction.userId)
          } else if (confirmAction.type === 'forceLogout') {
            forceLogoutMutation.mutate(confirmAction.userId)
          } else if (confirmAction.type === 'unsetAthlete') {
            setAthleteMutation.mutate({ userId: confirmAction.userId, isAthlete: false })
          } else {
            deleteUserMutation.mutate(confirmAction.userId)
          }
        }}
        title={
          confirmAction?.type === 'makeAdmin'
            ? t('users.grantAdminTitle')
            : confirmAction?.type === 'removeAdmin'
              ? t('users.revokeAdminTitle')
              : confirmAction?.type === 'forceLogout'
                ? t('users.forceLogoutTitle')
                : confirmAction?.type === 'unsetAthlete'
                  ? t('users.unsetAthleteTitle')
                  : t('users.deleteUserTitle')
        }
        message={
          confirmAction?.type === 'makeAdmin'
            ? t('users.grantAdminMessage', { name: confirmAction.userName })
            : confirmAction?.type === 'removeAdmin'
              ? t('users.revokeAdminMessage', { name: confirmAction?.userName })
              : confirmAction?.type === 'forceLogout'
                ? t('users.forceLogoutMessage', { name: confirmAction?.userName })
                : confirmAction?.type === 'unsetAthlete'
                  ? t('users.unsetAthleteMessage', { name: confirmAction?.userName })
                  : t('users.deleteUserMessage', { name: confirmAction?.userName })
        }
        confirmText={
          confirmAction?.type === 'makeAdmin'
            ? t('users.confirmGrant')
            : confirmAction?.type === 'removeAdmin'
              ? t('users.confirmRevoke')
              : confirmAction?.type === 'forceLogout'
                ? t('users.confirmForceLogout')
                : confirmAction?.type === 'unsetAthlete'
                  ? t('users.confirmUnsetAthlete')
                  : t('users.confirmDelete')
        }
        variant={confirmAction?.type === 'makeAdmin' || confirmAction?.type === 'forceLogout' ? 'primary' : 'danger'}
      />
      </>
      )}
    </div>
  )
}
