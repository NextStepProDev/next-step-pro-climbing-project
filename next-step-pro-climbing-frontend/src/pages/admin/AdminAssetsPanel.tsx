import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Upload, Copy, Check, Trash2 } from 'lucide-react'
import { adminAssetsApi } from '../../api/client'
import { Button } from '../../components/ui/Button'
import { Modal } from '../../components/ui/Modal'
import { ConfirmModal } from '../../components/ui/ConfirmModal'
import { FileUpload } from '../../components/ui/FileUpload'
import { LoadingSpinner } from '../../components/ui/LoadingSpinner'
import { QueryError } from '../../components/ui/QueryError'
import type { AssetDto } from '../../types'

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

export function AdminAssetsPanel() {
  const { t } = useTranslation('admin')
  const queryClient = useQueryClient()

  const [uploadModalOpen, setUploadModalOpen] = useState(false)
  const [selectedFiles, setSelectedFiles] = useState<File[]>([])
  const [previews, setPreviews] = useState<string[]>([])
  const [uploadProgress, setUploadProgress] = useState(0)
  const [assetToDelete, setAssetToDelete] = useState<AssetDto | null>(null)
  const [copiedId, setCopiedId] = useState<string | null>(null)

  const { data: assets, isLoading, error } = useQuery({
    queryKey: ['admin', 'assets'],
    queryFn: adminAssetsApi.list,
  })

  const uploadMutation = useMutation({
    mutationFn: async (files: File[]) => {
      for (let i = 0; i < files.length; i++) {
        await adminAssetsApi.upload(files[i])
        setUploadProgress(Math.round(((i + 1) / files.length) * 100))
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'assets'] })
      closeUploadModal()
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => adminAssetsApi.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'assets'] })
      setAssetToDelete(null)
    },
  })

  const handleCopyUrl = (asset: AssetDto) => {
    navigator.clipboard.writeText(asset.url).then(() => {
      setCopiedId(asset.id)
      setTimeout(() => setCopiedId(null), 2000)
    })
  }

  const handleFileSelect = (files: File[]) => {
    setSelectedFiles(files)
    setPreviews(files.map((f) => URL.createObjectURL(f)))
  }

  const closeUploadModal = () => {
    previews.forEach((p) => URL.revokeObjectURL(p))
    setSelectedFiles([])
    setPreviews([])
    setUploadProgress(0)
    setUploadModalOpen(false)
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h2 className="text-xl font-semibold text-dark-100">{t('assets.title')}</h2>
        <Button onClick={() => setUploadModalOpen(true)}>
          <Upload className="w-4 h-4 mr-2" />
          {t('assets.upload')}
        </Button>
      </div>

      {isLoading && (
        <div className="flex justify-center py-12">
          <LoadingSpinner />
        </div>
      )}

      {error && <QueryError error={error} />}

      {assets && assets.length === 0 && (
        <div className="text-center py-16 text-dark-400">
          {t('assets.empty')}
        </div>
      )}

      {assets && assets.length > 0 && (
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
          {assets.map((asset) => (
            <div key={asset.id} className="group relative bg-dark-800 rounded-lg overflow-hidden border border-dark-700">
              <div className="aspect-square bg-dark-900 flex items-center justify-center overflow-hidden">
                <img
                  src={asset.url}
                  alt={asset.originalName}
                  className="w-full h-full object-contain"
                />
              </div>
              <div className="px-2 py-1 text-xs text-dark-400 truncate" title={asset.originalName}>
                {asset.originalName} · {formatBytes(asset.sizeBytes)}
              </div>

              {/* Hover overlay */}
              <div className="absolute inset-0 bg-black/60 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center gap-2">
                <Button
                  size="sm"
                  variant="secondary"
                  onClick={() => handleCopyUrl(asset)}
                  title={t('assets.copyUrl')}
                >
                  {copiedId === asset.id
                    ? <Check className="w-4 h-4 text-green-400" />
                    : <Copy className="w-4 h-4" />
                  }
                </Button>
                <Button
                  size="sm"
                  variant="danger"
                  onClick={() => setAssetToDelete(asset)}
                  title={t('assets.delete')}
                >
                  <Trash2 className="w-4 h-4" />
                </Button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Upload Modal */}
      <Modal
        isOpen={uploadModalOpen}
        onClose={closeUploadModal}
        title={t('assets.uploadTitle')}
        size="xl"
      >
        <div className="space-y-4">
          <FileUpload
            onFileSelect={handleFileSelect}
            onClear={() => { previews.forEach((p) => URL.revokeObjectURL(p)); setSelectedFiles([]); setPreviews([]) }}
            accept="image/jpeg,image/png,image/webp"
            maxSizeMB={10}
            multiple
            previews={previews}
          />

          {uploadMutation.isPending && uploadProgress > 0 && (
            <div className="space-y-1">
              <div className="flex justify-between text-xs text-dark-400">
                <span>{t('assets.uploading')}</span>
                <span>{uploadProgress}%</span>
              </div>
              <div className="w-full bg-dark-700 rounded-full h-1.5">
                <div
                  className="bg-primary-500 h-1.5 rounded-full transition-all"
                  style={{ width: `${uploadProgress}%` }}
                />
              </div>
            </div>
          )}

          {uploadMutation.isError && (
            <p className="text-sm text-red-400">
              {uploadMutation.error instanceof Error ? uploadMutation.error.message : 'Błąd uploadu'}
            </p>
          )}

          <div className="flex justify-between items-center">
            <span className="text-sm text-dark-400">
              {selectedFiles.length > 0 && t('assets.selectedCount', { count: selectedFiles.length })}
            </span>
            <div className="flex gap-3">
              <Button variant="secondary" onClick={closeUploadModal} disabled={uploadMutation.isPending}>
                {t('cancel', { ns: 'common' })}
              </Button>
              <Button
                onClick={() => uploadMutation.mutate(selectedFiles)}
                disabled={selectedFiles.length === 0 || uploadMutation.isPending}
                loading={uploadMutation.isPending}
              >
                {t('assets.uploadBtn')}
              </Button>
            </div>
          </div>
        </div>
      </Modal>

      {/* Delete Confirm Modal */}
      <ConfirmModal
        isOpen={assetToDelete !== null}
        onClose={() => setAssetToDelete(null)}
        onConfirm={() => assetToDelete && deleteMutation.mutate(assetToDelete.id)}
        title={t('assets.deleteTitle')}
        message={t('assets.deleteMessage', { name: assetToDelete?.originalName ?? '' })}
        variant="danger"
      />
    </div>
  )
}
