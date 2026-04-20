import { useEffect, useRef, useState } from 'react'
import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { Newspaper, Search, Star } from 'lucide-react'
import { newsApi } from '../api/client'
import { LoadingSpinner } from '../components/ui/LoadingSpinner'
import { QueryError } from '../components/ui/QueryError'
import { useAuth } from '../context/AuthContext'
import clsx from 'clsx'

export function NewsPage() {
  const { t } = useTranslation('common')
  const { isAuthenticated } = useAuth()
  const queryClient = useQueryClient()

  const [searchInput, setSearchInput] = useState('')
  const [q, setQ] = useState('')
  const [starredOnly, setStarredOnly] = useState(false)
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  // Debounce: update q 400ms after typing stops
  useEffect(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current)
    debounceRef.current = setTimeout(() => setQ(searchInput), 400)
    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current)
    }
  }, [searchInput])

  // Reset starred filter when user logs out
  useEffect(() => {
    if (!isAuthenticated) setStarredOnly(false)
  }, [isAuthenticated])

  const queryKey = ['news', q, starredOnly]

  const {
    data,
    isLoading,
    isFetching,
    error,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
  } = useInfiniteQuery({
    queryKey,
    queryFn: ({ pageParam }) =>
      newsApi.getAll(pageParam as number, 12, q || undefined, starredOnly || undefined),
    initialPageParam: 0,
    getNextPageParam: (lastPage) => lastPage.hasNext ? lastPage.page + 1 : undefined,
    staleTime: 0,
    // Zachowaj poprzednie wyniki podczas zmiany zapytania — input nigdy nie traci fokusu
    placeholderData: (previousData) => previousData,
  })

  const starMutation = useMutation({
    mutationFn: ({ id, isStarred }: { id: string; isStarred: boolean }) =>
      isStarred ? newsApi.unstar(id) : newsApi.star(id),
    onMutate: async ({ id, isStarred }) => {
      await queryClient.cancelQueries({ queryKey })
      const previous = queryClient.getQueryData(queryKey)
      queryClient.setQueryData(queryKey, (old: typeof data) => {
        if (!old) return old
        return {
          ...old,
          pages: old.pages.map((page) => ({
            ...page,
            content: page.content.map((a) =>
              a.id === id ? { ...a, starred: !isStarred } : a
            ),
          })),
        }
      })
      return { previous }
    },
    onError: (_err, _vars, context) => {
      if (context?.previous) {
        queryClient.setQueryData(queryKey, context.previous)
      }
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ['news'] })
    },
  })

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (debounceRef.current) clearTimeout(debounceRef.current)
    setQ(searchInput)
  }

  const handleStarClick = (e: React.MouseEvent, id: string, isStarred: boolean | null) => {
    e.preventDefault()
    e.stopPropagation()
    if (!isAuthenticated) return
    starMutation.mutate({ id, isStarred: isStarred === true })
  }

  const articles = data?.pages.flatMap(p => p.content) ?? []

  const emptyMessage = starredOnly
    ? t('news.noStarredArticles')
    : q.trim()
      ? t('news.noSearchResults')
      : t('news.noArticles')

  // Zawsze renderujemy pełną strukturę — nigdy wczesny return,
  // żeby input nie był odmontowywany i nie tracił fokusu.
  return (
    <div className="container mx-auto px-4 py-8">
      <h1 className="text-3xl font-bold text-dark-100 mb-6">{t('news.title')}</h1>

      {error && (
        <div className="mb-6">
          <QueryError error={error} />
        </div>
      )}

      {/* Search + filter bar — zawsze widoczny */}
      <div className="flex flex-col sm:flex-row gap-3 mb-8">
        <form onSubmit={handleSearchSubmit} className="flex flex-1 gap-2">
          <input
            type="text"
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            placeholder={t('news.searchPlaceholder')}
            className="flex-1 px-4 py-2 bg-dark-800 border border-dark-600 rounded-lg text-dark-100 placeholder-dark-400 focus:outline-none focus:border-primary-500 transition-colors"
          />
          <button
            type="submit"
            className="flex items-center gap-2 px-4 py-2 bg-dark-700 hover:bg-dark-600 text-dark-100 rounded-lg transition-colors"
          >
            <Search className="h-4 w-4" />
            <span className="hidden sm:inline">{t('news.searchButton')}</span>
          </button>
        </form>

        {isAuthenticated && (
          <button
            onClick={() => setStarredOnly(!starredOnly)}
            className={clsx(
              'flex items-center gap-2 px-4 py-2 rounded-lg border transition-colors',
              starredOnly
                ? 'bg-yellow-500/20 border-yellow-500/50 text-yellow-400'
                : 'bg-dark-800 border-dark-600 text-dark-300 hover:border-primary-500/50 hover:text-dark-100'
            )}
          >
            <Star className={clsx('h-4 w-4', starredOnly && 'fill-yellow-400')} />
            {t('news.myStars')}
          </button>
        )}
      </div>

      {/* Obszar wyników */}
      {isLoading ? (
        // Tylko przy PIERWSZYM załadowaniu strony (brak danych w cache)
        <div className="flex justify-center py-12">
          <LoadingSpinner />
        </div>
      ) : articles.length === 0 && !isFetching ? (
        <div className="text-center text-dark-400 py-12">
          {emptyMessage}
        </div>
      ) : (
        <div className={clsx('transition-opacity duration-150', isFetching && 'opacity-60')}>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
            {articles.map((article) => (
              <div key={article.id} className="relative group">
                <Link
                  to={`/aktualnosci/${article.id}`}
                  className="block bg-dark-800 rounded-lg overflow-hidden border border-dark-700 hover:border-primary-500/50 transition-colors"
                >
                  <div className="aspect-video bg-dark-700 relative overflow-hidden">
                    {article.thumbnailUrl ? (
                      article.thumbnailFocalPointX != null ? (
                        <img
                          src={article.thumbnailUrl}
                          alt={article.title}
                          className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
                          style={{ objectPosition: `${article.thumbnailFocalPointX * 100}% ${(article.thumbnailFocalPointY ?? 0.5) * 100}%` }}
                        />
                      ) : (
                        <>
                          <img
                            src={article.thumbnailUrl}
                            alt=""
                            aria-hidden="true"
                            className="absolute inset-0 w-full h-full object-cover blur-xl scale-110"
                          />
                          <img
                            src={article.thumbnailUrl}
                            alt={article.title}
                            className="relative w-full h-full object-contain group-hover:scale-105 transition-transform duration-300"
                          />
                        </>
                      )
                    ) : (
                      <div className="w-full h-full flex items-center justify-center">
                        <Newspaper className="h-16 w-16 text-dark-500" />
                      </div>
                    )}
                    <div className="absolute bottom-2 left-2 bg-dark-900/80 px-2 py-1 rounded text-xs text-dark-300">
                      {new Date(article.publishedAt).toLocaleDateString('pl-PL', {
                        day: 'numeric',
                        month: 'short',
                        year: 'numeric',
                      })}
                    </div>
                  </div>

                  <div className="p-4">
                    <h2 className="text-lg font-semibold text-dark-100 group-hover:text-primary-400 transition-colors">
                      {article.title}
                    </h2>
                    {article.excerpt && (
                      <p className="mt-2 text-sm text-dark-300 line-clamp-3">
                        {article.excerpt}
                      </p>
                    )}
                  </div>
                </Link>

                {/* Star button — overlays top-right corner */}
                {isAuthenticated && (
                  <button
                    onClick={(e) => handleStarClick(e, article.id, article.starred)}
                    aria-label={article.starred ? t('news.starRemove') : t('news.starAdd')}
                    className={clsx(
                      'absolute top-2 right-2 p-1.5 rounded-full transition-colors z-10',
                      article.starred
                        ? 'text-yellow-400 bg-dark-900/80 hover:bg-dark-900'
                        : 'text-dark-400 bg-dark-900/60 hover:text-yellow-400 hover:bg-dark-900/80'
                    )}
                  >
                    <Star className={clsx('h-4 w-4', article.starred && 'fill-yellow-400')} />
                  </button>
                )}
              </div>
            ))}
          </div>

          {hasNextPage && (
            <div className="mt-8 flex justify-center">
              <button
                onClick={() => fetchNextPage()}
                disabled={isFetchingNextPage}
                className="px-6 py-2 bg-dark-700 hover:bg-dark-600 text-dark-100 rounded-lg transition-colors disabled:opacity-50"
              >
                {isFetchingNextPage ? <LoadingSpinner /> : t('news.loadMore')}
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
