import { useState, useMemo } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { format } from 'date-fns'
import { Shield, ShieldOff, Trash2, Search, ChevronLeft, ChevronRight } from 'lucide-react'
import { adminApi } from '../../api/client'
import { LoadingSpinner } from '../../components/ui/LoadingSpinner'
import { QueryError } from '../../components/ui/QueryError'
import { Button } from '../../components/ui/Button'
import { ConfirmModal } from '../../components/ui/ConfirmModal'

const PAGE_SIZE = 20

export function AdminUsersPanel() {
  const { t } = useTranslation('admin')
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(1)
  const [confirmAction, setConfirmAction] = useState<{
    type: 'makeAdmin' | 'removeAdmin' | 'delete'
    userId: string
    userName: string
  } | null>(null)
  const queryClient = useQueryClient()

  const { data: users, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['admin', 'users'],
    queryFn: adminApi.getAllUsers,
  })

  const makeAdminMutation = useMutation({
    mutationFn: adminApi.makeAdmin,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'users'] }),
  })

  const removeAdminMutation = useMutation({
    mutationFn: adminApi.removeAdmin,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'users'] }),
  })

  const deleteUserMutation = useMutation({
    mutationFn: adminApi.deleteUser,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'users'] }),
  })

  const filtered = useMemo(() => {
    if (!users) return []
    if (!search.trim()) return users

    const q = search.toLowerCase().trim()
    return users.filter(
      (u) =>
        u.firstName.toLowerCase().includes(q) ||
        u.lastName.toLowerCase().includes(q) ||
        u.email.toLowerCase().includes(q) ||
        `${u.firstName} ${u.lastName}`.toLowerCase().includes(q),
    )
  }, [users, search])

  const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE))
  const safePage = Math.min(page, totalPages)
  const paged = filtered.slice((safePage - 1) * PAGE_SIZE, safePage * PAGE_SIZE)

  const handleSearchChange = (value: string) => {
    setSearch(value)
    setPage(1)
  }

  return (
    <div>
      {/* Search */}
      <div className="relative mb-4">
        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-dark-500" />
        <input
          type="text"
          value={search}
          onChange={(e) => handleSearchChange(e.target.value)}
          placeholder={t('users.searchPlaceholder')}
          className="w-full bg-dark-800 border border-dark-700 rounded-lg pl-10 pr-4 py-2 text-dark-100 placeholder:text-dark-500 focus:outline-none focus:ring-2 focus:ring-primary-500"
        />
      </div>

      {isLoading ? (
        <LoadingSpinner />
      ) : isError ? (
        <QueryError error={error} onRetry={() => refetch()} />
      ) : (
        <>
          <div className="bg-dark-900 rounded-lg border border-dark-800 overflow-hidden">
            <table className="w-full">
              <thead className="bg-dark-800">
                <tr>
                  <th className="text-left px-4 py-3 text-sm font-medium text-dark-300">
                    {t('users.userColumn')}
                  </th>
                  <th className="text-left px-4 py-3 text-sm font-medium text-dark-300">
                    {t('users.emailColumn')}
                  </th>
                  <th className="text-left px-4 py-3 text-sm font-medium text-dark-300">
                    {t('users.phoneColumn')}
                  </th>
                  <th className="text-left px-4 py-3 text-sm font-medium text-dark-300">
                    {t('users.roleColumn')}
                  </th>
                  <th className="text-left px-4 py-3 text-sm font-medium text-dark-300">
                    {t('users.registrationDate')}
                  </th>
                  <th className="px-4 py-3"></th>
                </tr>
              </thead>
              <tbody className="divide-y divide-dark-800">
                {paged.map((user) => (
                  <tr key={user.id} className="hover:bg-dark-800/50">
                    <td className="px-4 py-3 text-dark-100">
                      {user.firstName} {user.lastName}
                    </td>
                    <td className="px-4 py-3 text-dark-300">{user.email}</td>
                    <td className="px-4 py-3 text-dark-300">{user.phone || '-'}</td>
                    <td className="px-4 py-3">
                      <span
                        className={`px-2 py-1 text-xs rounded ${
                          user.role === 'ADMIN'
                            ? 'bg-primary-500/20 text-primary-400'
                            : 'bg-dark-700 text-dark-300'
                        }`}
                      >
                        {user.role}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-dark-400 text-sm">
                      {format(new Date(user.createdAt), 'dd.MM.yyyy')}
                    </td>
                    <td className="px-4 py-3 flex gap-1">
                      {user.role === 'ADMIN' ? (
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => setConfirmAction({ type: 'removeAdmin', userId: user.id, userName: `${user.firstName} ${user.lastName}` })}
                          title={t('users.revokeAdmin')}
                          className="text-orange-400 hover:text-orange-300 hover:bg-orange-500/10"
                        >
                          <ShieldOff className="w-4 h-4" />
                        </Button>
                      ) : (
                        <>
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => setConfirmAction({ type: 'makeAdmin', userId: user.id, userName: `${user.firstName} ${user.lastName}` })}
                            title={t('users.grantAdmin')}
                          >
                            <Shield className="w-4 h-4" />
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
              <div className="p-8 text-center text-dark-400">
                {search.trim()
                  ? t('users.noSearchResults')
                  : t('users.noUsers')}
              </div>
            )}
          </div>

          {/* Pagination */}
          {filtered.length > PAGE_SIZE && (
            <div className="flex items-center justify-between mt-4">
              <span className="text-sm text-dark-400">
                {t('users.userCount', { count: filtered.length })}
                {search.trim() && users ? ` (${t('users.ofTotal', { count: users.length })})` : ''}
              </span>

              <div className="flex items-center gap-2">
                <button
                  onClick={() => setPage((p) => Math.max(1, p - 1))}
                  disabled={safePage <= 1}
                  className="p-2 text-dark-400 hover:text-dark-100 hover:bg-dark-800 rounded-lg transition-colors disabled:opacity-30 disabled:cursor-not-allowed"
                >
                  <ChevronLeft className="w-4 h-4" />
                </button>

                <span className="text-sm text-dark-300 min-w-[80px] text-center">
                  {safePage} / {totalPages}
                </span>

                <button
                  onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
                  disabled={safePage >= totalPages}
                  className="p-2 text-dark-400 hover:text-dark-100 hover:bg-dark-800 rounded-lg transition-colors disabled:opacity-30 disabled:cursor-not-allowed"
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
