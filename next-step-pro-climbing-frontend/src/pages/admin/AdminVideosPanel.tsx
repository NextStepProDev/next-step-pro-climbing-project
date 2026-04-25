import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { ChevronUp, ChevronDown, Pencil, Trash2, Plus, ArrowLeft, ExternalLink, Eye, EyeOff } from 'lucide-react'
import { adminVideoApi } from '../../api/client'
import type { VideoAdmin, UpdateVideoRequest } from '../../types'
import { Button } from '../../components/ui/Button'
import { ConfirmModal } from '../../components/ui/ConfirmModal'
import { LoadingSpinner } from '../../components/ui/LoadingSpinner'
import { QueryError } from '../../components/ui/QueryError'
import { RichTextEditor } from '../../components/ui/RichTextEditor'
import clsx from 'clsx'

type View = 'list' | 'edit'

export function AdminVideosPanel() {
  const { t } = useTranslation('admin')
  const queryClient = useQueryClient()

  const [view, setView] = useState<View>('list')
  const [editingVideo, setEditingVideo] = useState<VideoAdmin | null>(null)
  const [formTitle, setFormTitle] = useState('')
  const [formExcerpt, setFormExcerpt] = useState('')
  const [formContent, setFormContent] = useState('')
  const [formYoutubeUrl, setFormYoutubeUrl] = useState('')
  const [isSaving, setIsSaving] = useState(false)
  const [saveSuccess, setSaveSuccess] = useState(false)

  const isDirty = editingVideo !== null && (
    formTitle !== editingVideo.title ||
    formExcerpt !== (editingVideo.excerpt ?? '') ||
    formContent !== (editingVideo.content ?? '') ||
    formYoutubeUrl !== editingVideo.youtubeUrl
  )
  const [deleteConfirm, setDeleteConfirm] = useState<{ open: boolean; video: VideoAdmin | null }>({
    open: false,
    video: null,
  })

  const { data: videos, isLoading, error } = useQuery({
    queryKey: ['admin', 'videos'],
    queryFn: adminVideoApi.getAll,
  })

  const createMutation = useMutation({
    mutationFn: adminVideoApi.create,
    onSuccess: (created) => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'videos'] })
      openEditView(created)
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: UpdateVideoRequest }) =>
      adminVideoApi.update(id, data),
    onSuccess: (updated) => {
      queryClient.setQueryData<VideoAdmin[]>(['admin', 'videos'], (prev) =>
        prev?.map((v) => (v.id === updated.id ? updated : v)) ?? [updated]
      )
      setEditingVideo(updated)
    },
  })

  const deleteMutation = useMutation({
    mutationFn: adminVideoApi.delete,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'videos'] })
      setDeleteConfirm({ open: false, video: null })
    },
  })

  const publishMutation = useMutation({
    mutationFn: ({ id, publish }: { id: string; publish: boolean }) =>
      publish ? adminVideoApi.publish(id) : adminVideoApi.unpublish(id),
    onSuccess: (updated) => {
      queryClient.setQueryData<VideoAdmin[]>(['admin', 'videos'], (prev) =>
        prev?.map((v) => (v.id === updated.id ? updated : v)) ?? [updated]
      )
      if (editingVideo?.id === updated.id) {
        setEditingVideo(updated)
      }
    },
  })

  const moveUpMutation = useMutation({
    mutationFn: adminVideoApi.moveUp,
    onSuccess: (updated) => {
      queryClient.setQueryData(['admin', 'videos'], updated)
    },
  })

  const moveDownMutation = useMutation({
    mutationFn: adminVideoApi.moveDown,
    onSuccess: (updated) => {
      queryClient.setQueryData(['admin', 'videos'], updated)
    },
  })

  function openEditView(video: VideoAdmin) {
    setEditingVideo(video)
    setFormTitle(video.title)
    setFormExcerpt(video.excerpt ?? '')
    setFormContent(video.content ?? '')
    setFormYoutubeUrl(video.youtubeUrl)
    setSaveSuccess(false)
    setView('edit')
  }

  function handleBackToList() {
    setView('list')
    setEditingVideo(null)
  }

  async function saveFormData(): Promise<VideoAdmin | null> {
    if (!editingVideo) return null
    const updated = await updateMutation.mutateAsync({
      id: editingVideo.id,
      data: {
        title: formTitle || undefined,
        excerpt: formExcerpt || undefined,
        content: formContent || undefined,
        youtubeUrl: formYoutubeUrl || undefined,
      },
    })
    queryClient.invalidateQueries({ queryKey: ['videos'] })
    return updated
  }

  async function handleSaveDraft() {
    if (!editingVideo) return
    setIsSaving(true)
    setSaveSuccess(false)
    try {
      await saveFormData()
      setSaveSuccess(true)
    } finally {
      setIsSaving(false)
    }
  }

  async function handlePublish() {
    if (!editingVideo) return
    setIsSaving(true)
    setSaveSuccess(false)
    try {
      await saveFormData()
      await publishMutation.mutateAsync({ id: editingVideo.id, publish: true })
      setSaveSuccess(true)
    } finally {
      setIsSaving(false)
    }
  }

  async function handleUnpublish() {
    if (!editingVideo) return
    publishMutation.mutate({ id: editingVideo.id, publish: false })
  }

  function handleCreateNew() {
    createMutation.mutate({
      title: t('videos.newVideoDefaultTitle'),
      youtubeUrl: 'https://www.youtube.com/watch?v=',
    })
  }

  if (view === 'edit' && editingVideo) {
    return (
      <div className="max-w-3xl">
        {/* Header */}
        <div className="flex items-center justify-between mb-6">
          <button
            onClick={handleBackToList}
            className="inline-flex items-center gap-2 text-dark-400 hover:text-dark-200 transition-colors text-sm"
          >
            <ArrowLeft className="w-4 h-4" />
            {t('videos.backToList')}
          </button>

          {editingVideo.published ? (
            <span className="px-2 py-1 rounded-full text-xs font-medium bg-green-500/10 text-green-400">
              {t('videos.published')}
            </span>
          ) : (
            <span className="px-2 py-1 rounded-full text-xs font-medium bg-dark-700 text-dark-400">
              {t('videos.draft')}
            </span>
          )}
        </div>

        {/* Form */}
        <div className="space-y-6">
          {/* Title */}
          <div>
            <label className="block text-sm font-medium text-dark-300 mb-1.5">
              {t('videos.titleLabel')}
            </label>
            <input
              type="text"
              value={formTitle}
              onChange={(e) => setFormTitle(e.target.value)}
              onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); handleSaveDraft() } }}
              className="w-full bg-dark-800 border border-dark-700 rounded-lg px-3 py-2 text-dark-100 placeholder-dark-500 focus:outline-none focus:border-primary-500 focus:ring-1 focus:ring-primary-500/30"
            />
          </div>

          {/* YouTube URL */}
          <div>
            <label className="block text-sm font-medium text-dark-300 mb-1.5">
              {t('videos.youtubeUrlLabel')}
            </label>
            <input
              type="url"
              value={formYoutubeUrl}
              onChange={(e) => setFormYoutubeUrl(e.target.value)}
              onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); handleSaveDraft() } }}
              placeholder={t('videos.youtubeUrlPlaceholder')}
              className="w-full bg-dark-800 border border-dark-700 rounded-lg px-3 py-2 text-dark-100 placeholder-dark-500 focus:outline-none focus:border-primary-500 focus:ring-1 focus:ring-primary-500/30"
            />
            {formYoutubeUrl && (
              <a
                href={formYoutubeUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center gap-1 text-xs text-primary-400 hover:text-primary-300 mt-1 transition-colors"
              >
                <ExternalLink className="w-3 h-3" />
                {formYoutubeUrl}
              </a>
            )}
          </div>

          {/* Excerpt */}
          <div>
            <label className="block text-sm font-medium text-dark-300 mb-1.5">
              {t('videos.excerptLabel')}
            </label>
            <RichTextEditor
              value={formExcerpt}
              onChange={setFormExcerpt}
              rows={6}
              placeholder={t('videos.excerptLabel') + '...'}
            />
          </div>

          {/* Content */}
          <div>
            <label className="block text-sm font-medium text-dark-300 mb-1.5">
              {t('videos.contentLabel')}
            </label>
            <RichTextEditor
              value={formContent}
              onChange={setFormContent}
              rows={10}
              placeholder={t('videos.contentLabel') + '...'}
            />
          </div>

          {/* Save / Publish */}
          <div className="flex items-center gap-3 pt-2 flex-wrap">
            {editingVideo.published ? (
              <>
                <Button
                  variant="primary"
                  onClick={handleSaveDraft}
                  disabled={isSaving || !isDirty || !formTitle.trim() || !formYoutubeUrl.trim()}
                >
                  {isSaving ? '...' : t('videos.saveChanges')}
                </Button>
                <Button
                  variant="ghost"
                  onClick={handleUnpublish}
                  disabled={publishMutation.isPending || isSaving}
                >
                  {t('videos.unpublish')}
                </Button>
              </>
            ) : (
              <>
                <Button
                  variant="ghost"
                  onClick={handleSaveDraft}
                  disabled={isSaving || !isDirty || !formTitle.trim() || !formYoutubeUrl.trim()}
                >
                  {isSaving && !publishMutation.isPending ? '...' : t('videos.saveDraft')}
                </Button>
                <Button
                  variant="primary"
                  onClick={handlePublish}
                  disabled={isSaving || !formTitle.trim() || !formYoutubeUrl.trim()}
                >
                  {isSaving && publishMutation.isPending ? '...' : t('videos.publish')}
                </Button>
              </>
            )}
            {saveSuccess && (
              <span className="text-sm text-green-400">{t('videos.saveSuccess')}</span>
            )}
          </div>
        </div>
      </div>
    )
  }

  // List view
  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h2 className="text-xl font-bold text-dark-100">{t('videos.title')}</h2>
        <Button
          variant="primary"
          size="sm"
          onClick={handleCreateNew}
          disabled={createMutation.isPending}
          className="flex items-center gap-2"
        >
          <Plus className="w-4 h-4" />
          {t('videos.addVideo')}
        </Button>
      </div>

      {isLoading && (
        <div className="flex justify-center py-12">
          <LoadingSpinner />
        </div>
      )}

      {error && <QueryError error={error} />}

      {!isLoading && !error && (!videos || videos.length === 0) && (
        <p className="text-dark-400 text-sm">{t('videos.noVideos')}</p>
      )}

      {!isLoading && !error && videos && videos.length > 0 && (
        <div className="space-y-2">
          {videos.map((video, idx) => (
            <div
              key={video.id}
              className="flex items-center gap-3 bg-dark-800 rounded-lg px-4 py-3 border border-dark-700"
            >
              {/* Order buttons */}
              <div className="flex flex-col gap-0.5 shrink-0">
                <button
                  onClick={() => moveUpMutation.mutate(video.id)}
                  disabled={idx === 0 || moveUpMutation.isPending || moveDownMutation.isPending}
                  title={t('videos.moveUp')}
                  className={clsx(
                    'p-0.5 rounded transition-colors',
                    idx === 0
                      ? 'text-dark-700 cursor-not-allowed'
                      : 'text-dark-400 hover:text-dark-200'
                  )}
                >
                  <ChevronUp className="w-4 h-4" />
                </button>
                <button
                  onClick={() => moveDownMutation.mutate(video.id)}
                  disabled={
                    idx === videos.length - 1 ||
                    moveUpMutation.isPending ||
                    moveDownMutation.isPending
                  }
                  title={t('videos.moveDown')}
                  className={clsx(
                    'p-0.5 rounded transition-colors',
                    idx === videos.length - 1
                      ? 'text-dark-700 cursor-not-allowed'
                      : 'text-dark-400 hover:text-dark-200'
                  )}
                >
                  <ChevronDown className="w-4 h-4" />
                </button>
              </div>

              {/* Title + status */}
              <div className="flex-1 min-w-0">
                <span className="text-dark-100 font-medium truncate block">{video.title}</span>
                <span className="text-xs text-dark-500 truncate block">{video.youtubeUrl}</span>
              </div>

              {/* Status badge */}
              <span
                className={clsx(
                  'flex items-center gap-1 px-2 py-1 rounded-full text-xs font-medium shrink-0',
                  video.published
                    ? 'bg-green-500/10 text-green-400'
                    : 'bg-dark-700 text-dark-400'
                )}
              >
                {video.published ? <Eye className="w-3 h-3" /> : <EyeOff className="w-3 h-3" />}
                {video.published ? t('videos.published') : t('videos.draft')}
              </span>

              {/* Actions */}
              <div className="flex items-center gap-1 shrink-0">
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() =>
                    publishMutation.mutate({ id: video.id, publish: !video.published })
                  }
                  disabled={publishMutation.isPending}
                  title={video.published ? t('videos.unpublish') : t('videos.publish')}
                  className={video.published ? 'text-emerald-400 hover:text-orange-400' : 'text-dark-400 hover:text-dark-100'}
                >
                  {video.published ? <Eye className="w-4 h-4" /> : <EyeOff className="w-4 h-4" />}
                </Button>
                <button
                  onClick={() => openEditView(video)}
                  title={t('videos.edit')}
                  className="p-1.5 rounded text-dark-400 hover:text-dark-200 hover:bg-dark-700 transition-colors"
                >
                  <Pencil className="w-4 h-4" />
                </button>
                <button
                  onClick={() => setDeleteConfirm({ open: true, video })}
                  title={t('videos.delete')}
                  className="p-1.5 rounded text-dark-400 hover:text-rose-400 hover:bg-dark-700 transition-colors"
                >
                  <Trash2 className="w-4 h-4" />
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      <ConfirmModal
        isOpen={deleteConfirm.open}
        onClose={() => setDeleteConfirm({ open: false, video: null })}
        onConfirm={() => {
          if (deleteConfirm.video) {
            deleteMutation.mutate(deleteConfirm.video.id)
          }
        }}
        title={t('videos.deleteConfirmTitle')}
        message={t('videos.deleteConfirmMessage')}
        variant="danger"
      />
    </div>
  )
}
