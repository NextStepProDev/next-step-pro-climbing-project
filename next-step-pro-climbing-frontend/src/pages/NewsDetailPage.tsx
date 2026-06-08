import { useEffect, useRef } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { useParams, Link, useNavigate } from 'react-router-dom'
import { Helmet } from 'react-helmet-async'
import { ArrowLeft, Star } from 'lucide-react'
import { ReadingProgressBar } from '../components/ui/ReadingProgressBar'
import { newsApi } from '../api/client'
import { PageHead } from '../components/ui/PageHead'
import { LoadingSpinner } from '../components/ui/LoadingSpinner'
import { QueryError } from '../components/ui/QueryError'
import { ShareButtons } from '../components/ui/ShareButtons'
import { renderRichText } from '../utils/renderRichText'
import { useAuth } from '../context/AuthContext'
import { COURSE_CONTENT_LANGUAGES, getDefaultCourseContentLanguage, pickBestTranslation } from '../constants/courseLanguages'
import clsx from 'clsx'

export function NewsDetailPage() {
  const { t, i18n } = useTranslation('common')
  const { newsId } = useParams<{ newsId: string }>()
  const navigate = useNavigate()
  const { isAuthenticated } = useAuth()
  const queryClient = useQueryClient()

  const { data: article, isLoading, error } = useQuery({
    queryKey: ['news', newsId],
    queryFn: () => newsApi.getById(newsId!),
    enabled: !!newsId,
  })

  const { data: translations } = useQuery({
    queryKey: ['newsTranslations', article?.translationGroupId],
    queryFn: () => newsApi.getTranslations(article!.translationGroupId),
    enabled: !!article?.translationGroupId,
  })

  const currentContentLang = getDefaultCourseContentLanguage(i18n.language)
  const prevLangRef = useRef(currentContentLang)
  useEffect(() => {
    if (prevLangRef.current !== currentContentLang && article && translations) {
      const target = pickBestTranslation(translations, currentContentLang)
      if (target && target.id !== article.id) {
        navigate(`/aktualnosci/${target.id}`, { replace: true })
      }
    }
    prevLangRef.current = currentContentLang
  }, [currentContentLang, article, translations, navigate])

  const starMutation = useMutation({
    mutationFn: (isStarred: boolean) =>
      isStarred ? newsApi.unstar(newsId!) : newsApi.star(newsId!),
    onMutate: async (isStarred) => {
      await queryClient.cancelQueries({ queryKey: ['news', newsId] })
      const previous = queryClient.getQueryData(['news', newsId])
      queryClient.setQueryData(['news', newsId], (old: typeof article) => {
        if (!old) return old
        return { ...old, starred: !isStarred }
      })
      return { previous }
    },
    onError: (_err, _vars, context) => {
      if (context?.previous) {
        queryClient.setQueryData(['news', newsId], context.previous)
      }
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ['news'] })
    },
  })

  if (isLoading) {
    return (
      <div className="min-h-[50vh] flex items-center justify-center">
        <LoadingSpinner />
      </div>
    )
  }

  if (error) {
    return (
      <div className="container mx-auto px-4 py-8">
        <QueryError error={error} />
      </div>
    )
  }

  if (!article) {
    return (
      <div className="container mx-auto px-4 py-8">
        <div className="text-center text-surface-400">{t('news.articleNotFound')}</div>
      </div>
    )
  }

  return (
    <div className="container mx-auto px-4 py-8 max-w-3xl">
      <ReadingProgressBar />
      <PageHead
        title={article.title}
        description={article.blocks.find(b => b.blockType === 'TEXT' && b.content)?.content?.replace(/<[^>]*>/g, '').slice(0, 160) ?? undefined}
        path={`/aktualnosci/${newsId}`}
        ogImage={article.thumbnailUrl ?? undefined}
      />
      <Helmet>
        <script type="application/ld+json">{JSON.stringify({
          '@context': 'https://schema.org',
          '@type': 'Article',
          headline: article.title,
          ...(article.thumbnailUrl && { image: article.thumbnailUrl }),
          datePublished: article.publishedAt,
          publisher: {
            '@type': 'Organization',
            name: 'Next Step Pro Climbing',
            url: 'https://nextsteppro.pl',
          },
          mainEntityOfPage: `https://nextsteppro.pl/aktualnosci/${newsId}`,
          inLanguage: article.language,
        })}</script>
      </Helmet>
      {/* Breadcrumb */}
      <Link
        to="/aktualnosci"
        className="inline-flex items-center gap-2 text-surface-300 hover:text-surface-100 mb-6 transition-colors"
      >
        <ArrowLeft className="h-4 w-4" />
        {t('news.backToNews')}
      </Link>

      {/* Header */}
      <div className="mb-8">
        <div className="flex items-start justify-between gap-4">
          <h1 className="text-3xl font-bold text-surface-100 mb-3">{article.title}</h1>
          {isAuthenticated && (
            <button
              onClick={() => starMutation.mutate(article.starred === true)}
              aria-label={article.starred ? t('news.starRemove') : t('news.starAdd')}
              disabled={starMutation.isPending}
              className={clsx(
                'flex-shrink-0 mt-1 p-2 rounded-full transition-colors',
                article.starred
                  ? 'text-yellow-400 hover:bg-surface-700'
                  : 'text-surface-400 hover:text-yellow-400 hover:bg-surface-700'
              )}
            >
              <Star className={clsx('h-5 w-5', article.starred && 'fill-yellow-400')} />
            </button>
          )}
        </div>
        <div className="flex items-center justify-between gap-4 flex-wrap">
          <p className="text-sm text-surface-400">
            {t('news.published')}: {new Date(article.publishedAt).toLocaleDateString('pl-PL', {
              day: 'numeric',
              month: 'long',
              year: 'numeric',
            })}
          </p>
          <div className="flex items-center gap-3">
            <div className="flex items-center gap-1 bg-surface-800 border border-surface-700 rounded-lg p-1">
              {COURSE_CONTENT_LANGUAGES.map((lang) => {
                const translation = translations?.find(tr => tr.language === lang.code)
                const isActive = article.language === lang.code
                const isAvailable = isActive || !!translation
                return (
                  <button
                    key={lang.code}
                    disabled={!isAvailable}
                    onClick={() => {
                      if (!isActive && translation) navigate(`/aktualnosci/${translation.id}`)
                    }}
                    className={clsx(
                      'px-2.5 py-1 rounded-md text-xs font-medium transition-all duration-150',
                      isActive
                        ? 'bg-primary-500 text-white'
                        : isAvailable
                          ? 'text-surface-400 hover:text-surface-100 hover:bg-surface-700'
                          : 'text-surface-600 cursor-not-allowed opacity-40'
                    )}
                  >
                    {lang.label}
                  </button>
                )
              })}
            </div>
            <ShareButtons title={article.title} />
          </div>
        </div>
      </div>

      <hr className="border-surface-700 mb-8" />

      {/* Bloki treści */}
      <div className="space-y-6">
        {article.blocks.map((block) => {
          if (block.blockType === 'TEXT') {
            return (
              <div
                key={block.id}
                className="text-surface-200 leading-relaxed"
                dangerouslySetInnerHTML={{ __html: renderRichText(block.content ?? '') }}
              />
            )
          }

          if (block.blockType === 'IMAGE') {
            return (
              <figure key={block.id} className="my-6">
                <img
                  src={block.imageUrl ?? ''}
                  alt={block.caption ?? ''}
                  className="block max-w-full max-h-[70vh] rounded-lg mx-auto"
                />
                {block.caption && (
                  <figcaption className="text-sm text-surface-400 text-center mt-2">
                    {block.caption}
                  </figcaption>
                )}
              </figure>
            )
          }

          if (block.blockType === 'VIDEO_EMBED' && block.content) {
            return (
              <div key={block.id} className="my-6 w-full" style={{ aspectRatio: '16/9' }}>
                <iframe
                  src={block.content}
                  className="w-full h-full rounded-lg"
                  allowFullScreen
                  allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
                  loading="lazy"
                />
              </div>
            )
          }

          return null
        })}
      </div>
    </div>
  )
}
