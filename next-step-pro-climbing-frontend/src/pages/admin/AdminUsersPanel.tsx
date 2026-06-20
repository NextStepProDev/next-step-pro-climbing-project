import { useState, useMemo } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { format } from 'date-fns'
import { Shield, ShieldOff, Trash2, Search, ChevronLeft, ChevronRight, Mail, ArrowUp, ArrowDown, ArrowUpDown } from 'lucide-react'
import { adminApi } from '../../api/client'
import { LoadingSpinner } from '../../components/ui/LoadingSpinner'
import { QueryError } from '../../components/ui/QueryError'
import { Button } from '../../components/ui/Button'
import { ConfirmModal } from '../../components/ui/ConfirmModal'
import { getErrorMessage } from '../../utils/errors'

const PAGE_SIZE = 50

type NewsletterFilter = 'all' | 'subscribed' | 'unsubscribed'
type SortKey = 'user' | 'email' | 'role' | 'createdAt'
type SortDir = 'asc' | 'desc'

export function AdminUsersPanel() {
  const { t } = useTranslation('admin')
  const [search, setSearch] = useState('')
  const [newsletterFilter, setNewsletterFilter] = useState<NewsletterFilter>('all')
  const [sortKey, setSortKey] = useState<SortKey>('createdAt')
  const [sortDir, setSortDir] = useState<SortDir>('desc')
  const [page, setPage] = useState(1)
  const [confirmAction, setConfirmAction] = useState<{
    type: 'makeAdmin' | 'removeAdmin' | 'delete'
    userId: string
    userName: string
  } | null>(null)
  const queryClient = useQueryClient()
  const [actionError, setActionError] = useState<string | null>(null)

  const { data: users, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['admin', 'users'],
    queryFn: adminApi.getAllUsers,
  })

  const makeAdminMutation = useMutation({
    mutationFn: adminApi.makeAdmin,
    onSuccess: () => { setActionError(null); queryClient.invalidateQueries({ queryKey: ['admin', 'users'] }) },
    onError: (err) => setActionError(getErrorMessage(err)),
  })

  const removeAdminMutation = useMutation({
    mutationFn: adminApi.removeAdmin,
    onSuccess: () => { setActionError(null); queryClient.invalidateQueries({ queryKey: ['admin', 'users'] }) },
    onError: (err) => setActionError(getErrorMessage(err)),
  })

  const deleteUserMutation = useMutation({
    mutationFn: adminApi.deleteUser,
    onSuccess: () => { setActionError(null); queryClient.invalidateQueries({ queryKey: ['admin', 'users'] }) },
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

    if (!search.trim()) return byNewsletter

    const q = search.toLowerCase().trim()
    return byNewsletter.filter(
      (u) =>
        u.firstName.toLowerCase().includes(q) ||
        u.lastName.toLowerCase().includes(q) ||
        u.email.toLowerCase().includes(q) ||
        `${u.firstName} ${u.lastName}`.toLowerCase().includes(q),
    )
  }, [users, search, newsletterFilter])

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

  return (
    <div>
      {actionError && (
        <div className="mb-4 p-3 bg-rose-500/10 border border-rose-500/20 rounded-lg text-sm text-rose-400">
          {actionError}
        </div>
      )}

      {/* Newsletter filter */}
      <div className="flex gap-1.5 mb-3 p-1 bg-surface-800 border border-surface-700 rounded-lg w-fit">
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
                    <td className="px-4 py-3 text-surface-100">
                      {user.firstName} {user.lastName}
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-1.5">
                        <span className="text-surface-300">{user.email}</span>
                        <span title={user.newsletterSubscribed ? t('users.newsletterYes') : t('users.newsletterNo')}>
                          <Mail className={`w-3 h-3 shrink-0 ${user.newsletterSubscribed ? 'text-green-400/60' : 'text-surface-600/50'}`} />
                        </span>
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
                      {user.role === 'ADMIN' ? (
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
          } else {
            deleteUserMutation.mutate(confirmAction.userId)
          }
        }}
        title={
          confirmAction?.type === 'makeAdmin'
            ? t('users.grantAdminTitle')
            : confirmAction?.type === 'removeAdmin'
              ? t('users.revokeAdminTitle')
              : t('users.deleteUserTitle')
        }
        message={
          confirmAction?.type === 'makeAdmin'
            ? t('users.grantAdminMessage', { name: confirmAction.userName })
            : confirmAction?.type === 'removeAdmin'
              ? t('users.revokeAdminMessage', { name: confirmAction?.userName })
              : t('users.deleteUserMessage', { name: confirmAction?.userName })
        }
        confirmText={
          confirmAction?.type === 'makeAdmin'
            ? t('users.confirmGrant')
            : confirmAction?.type === 'removeAdmin'
              ? t('users.confirmRevoke')
              : t('users.confirmDelete')
        }
        variant={confirmAction?.type === 'makeAdmin' ? 'primary' : 'danger'}
      />
    </div>
  )
}
