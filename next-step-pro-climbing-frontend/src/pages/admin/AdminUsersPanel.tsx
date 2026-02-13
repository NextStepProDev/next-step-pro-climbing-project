import { useState, useMemo } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { format } from 'date-fns'
import { Shield, ShieldOff, Trash2, Search, ChevronLeft, ChevronRight } from 'lucide-react'
import { adminApi } from '../../api/client'
import { LoadingSpinner } from '../../components/ui/LoadingSpinner'
import { QueryError } from '../../components/ui/QueryError'
import { Button } from '../../components/ui/Button'

const PAGE_SIZE = 20

export function AdminUsersPanel() {
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(1)
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
          placeholder="Szukaj po imieniu, nazwisku lub e-mailu..."
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
                    Użytkownik
                  </th>
                  <th className="text-left px-4 py-3 text-sm font-medium text-dark-300">
                    Email
                  </th>
                  <th className="text-left px-4 py-3 text-sm font-medium text-dark-300">
                    Telefon
                  </th>
                  <th className="text-left px-4 py-3 text-sm font-medium text-dark-300">
                    Rola
                  </th>
                  <th className="text-left px-4 py-3 text-sm font-medium text-dark-300">
                    Data rejestracji
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
                          onClick={() => {
                            if (
                              window.confirm(
                                `Czy na pewno chcesz odebrać uprawnienia administratora użytkownikowi ${user.firstName} ${user.lastName}?`
                              )
                            ) {
                              removeAdminMutation.mutate(user.id)
                            }
                          }}
                          title="Odbierz uprawnienia administratora"
                          className="text-orange-400 hover:text-orange-300 hover:bg-orange-500/10"
                        >
                          <ShieldOff className="w-4 h-4" />
                        </Button>
                      ) : (
                        <>
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => {
                              if (
                                window.confirm(
                                  `Czy na pewno chcesz nadać uprawnienia administratora użytkownikowi ${user.firstName} ${user.lastName}?`
                                )
                              ) {
                                makeAdminMutation.mutate(user.id)
                              }
                            }}
                            title="Nadaj uprawnienia administratora"
                          >
                            <Shield className="w-4 h-4" />
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => {
                              if (
                                window.confirm(
                                  `Czy na pewno chcesz usunąć użytkownika ${user.firstName} ${user.lastName}? Ta operacja jest nieodwracalna.`
                                )
                              ) {
                                deleteUserMutation.mutate(user.id)
                              }
                            }}
                            title="Usuń użytkownika"
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
                  ? 'Brak wyników dla podanego wyszukiwania'
                  : 'Brak zarejestrowanych użytkowników'}
              </div>
            )}
          </div>

          {/* Pagination */}
          {filtered.length > PAGE_SIZE && (
            <div className="flex items-center justify-between mt-4">
              <span className="text-sm text-dark-400">
                {filtered.length} {filtered.length === 1 ? 'użytkownik' : 'użytkowników'}
                {search.trim() && users ? ` (z ${users.length})` : ''}
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
    </div>
  )
}
