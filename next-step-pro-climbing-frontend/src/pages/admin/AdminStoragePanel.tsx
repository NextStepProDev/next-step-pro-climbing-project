import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { HardDrive, AlertTriangle, FileX, RefreshCw, CheckCircle, Trash2 } from 'lucide-react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { adminStorageApi } from '../../api/client'
import { LoadingSpinner } from '../../components/ui/LoadingSpinner'
import { ConfirmModal } from '../../components/ui/ConfirmModal'
import type { StorageAuditResult } from '../../types'

function formatBytes(bytes: number): string {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return `${parseFloat((bytes / Math.pow(k, i)).toFixed(1))} ${sizes[i]}`
}

export function AdminStoragePanel() {
  const { t } = useTranslation('admin')
  const queryClient = useQueryClient()
  const [enabled, setEnabled] = useState(false)
  const [showConfirm, setShowConfirm] = useState(false)
  const [deleteResult, setDeleteResult] = useState<number | null>(null)

  const { data, isLoading, error, refetch, isFetching } = useQuery<StorageAuditResult>({
    queryKey: ['admin', 'storage', 'audit'],
    queryFn: adminStorageApi.audit,
    enabled,
    staleTime: 0,
  })

  const deleteMutation = useMutation({
    mutationFn: adminStorageApi.deleteOrphaned,
    onSuccess: (result) => {
      setDeleteResult(result.deletedCount)
      queryClient.removeQueries({ queryKey: ['admin', 'storage', 'audit'] })
      refetch()
    },
  })

  const handleRunAudit = () => {
    setDeleteResult(null)
    if (enabled) {
      refetch()
    } else {
      setEnabled(true)
    }
  }

  const handleDeleteConfirm = () => {
    setShowConfirm(false)
    deleteMutation.mutate()
  }

  const orphanedSize = data?.orphanedFiles.reduce((sum, f) => sum + f.sizeBytes, 0) ?? 0

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-xl font-semibold text-dark-100">{t('storage.title')}</h2>
          <p className="text-sm text-dark-400 mt-1">{t('storage.description')}</p>
        </div>
        <button
          onClick={handleRunAudit}
          disabled={isLoading || isFetching || deleteMutation.isPending}
          className="flex items-center gap-2 px-4 py-2 bg-primary-600 hover:bg-primary-500 disabled:opacity-50 text-white rounded-lg text-sm font-medium transition-colors"
        >
          <RefreshCw className={`w-4 h-4 ${(isLoading || isFetching) ? 'animate-spin' : ''}`} />
          {t('storage.runAudit')}
        </button>
      </div>

      {(isLoading || isFetching || deleteMutation.isPending) && (
        <div className="flex justify-center py-12">
          <LoadingSpinner />
        </div>
      )}

      {error && (
        <div className="bg-red-500/10 border border-red-500/30 rounded-lg p-4 text-red-400 text-sm">
          {t('storage.error')}
        </div>
      )}

      {deleteResult !== null && (
        <div className="flex items-center gap-3 bg-green-500/10 border border-green-500/30 rounded-lg p-4 text-green-400">
          <CheckCircle className="w-5 h-5 shrink-0" />
          <span className="text-sm">{t('storage.deleteSuccess', { count: deleteResult })}</span>
        </div>
      )}

      {data && !isFetching && !deleteMutation.isPending && (
        <div className="space-y-6">
          {/* Stats */}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
            <StatCard
              label={t('storage.totalOnDisk')}
              value={data.totalFilesOnDisk.toString()}
              icon={<HardDrive className="w-5 h-5 text-primary-400" />}
            />
            <StatCard
              label={t('storage.totalInDb')}
              value={data.totalFilesInDb.toString()}
              icon={<HardDrive className="w-5 h-5 text-blue-400" />}
            />
            <StatCard
              label={t('storage.orphaned')}
              value={data.orphanedFiles.length.toString()}
              icon={<AlertTriangle className={`w-5 h-5 ${data.orphanedFiles.length > 0 ? 'text-yellow-400' : 'text-dark-400'}`} />}
              highlight={data.orphanedFiles.length > 0}
            />
            <StatCard
              label={t('storage.totalSize')}
              value={formatBytes(data.totalSizeBytesOnDisk)}
              icon={<HardDrive className="w-5 h-5 text-dark-400" />}
            />
          </div>

          {/* All clear */}
          {data.orphanedFiles.length === 0 && data.missingFiles.length === 0 && (
            <div className="flex items-center gap-3 bg-green-500/10 border border-green-500/30 rounded-lg p-4 text-green-400">
              <CheckCircle className="w-5 h-5 shrink-0" />
              <span className="text-sm">{t('storage.allClear')}</span>
            </div>
          )}

          {/* Orphaned files */}
          {data.orphanedFiles.length > 0 && (
            <section>
              <div className="flex items-center justify-between mb-3">
                <h3 className="text-sm font-semibold text-yellow-400 flex items-center gap-2">
                  <AlertTriangle className="w-4 h-4" />
                  {t('storage.orphanedTitle')} ({data.orphanedFiles.length} — {formatBytes(orphanedSize)})
                </h3>
                <button
                  onClick={() => setShowConfirm(true)}
                  className="flex items-center gap-2 px-3 py-1.5 bg-red-600 hover:bg-red-500 text-white rounded-lg text-xs font-medium transition-colors"
                >
                  <Trash2 className="w-3.5 h-3.5" />
                  {t('storage.deleteOrphaned')}
                </button>
              </div>
              <p className="text-xs text-dark-400 mb-3">{t('storage.orphanedDesc')}</p>
              <div className="bg-dark-800 rounded-lg overflow-hidden">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-dark-700">
                      <th className="text-left px-4 py-2 text-dark-400 font-medium">{t('storage.colFolder')}</th>
                      <th className="text-left px-4 py-2 text-dark-400 font-medium">{t('storage.colFilename')}</th>
                      <th className="text-right px-4 py-2 text-dark-400 font-medium">{t('storage.colSize')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.orphanedFiles.map((f) => (
                      <tr key={`${f.folder}/${f.filename}`} className="border-b border-dark-700/50 last:border-0">
                        <td className="px-4 py-2 text-dark-300">{f.folder}</td>
                        <td className="px-4 py-2 font-mono text-xs text-dark-300 break-all">{f.filename}</td>
                        <td className="px-4 py-2 text-right text-dark-400">{formatBytes(f.sizeBytes)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </section>
          )}

          {/* Missing files */}
          {data.missingFiles.length > 0 && (
            <section>
              <h3 className="text-sm font-semibold text-red-400 mb-3 flex items-center gap-2">
                <FileX className="w-4 h-4" />
                {t('storage.missingTitle')} ({data.missingFiles.length})
              </h3>
              <p className="text-xs text-dark-400 mb-3">{t('storage.missingDesc')}</p>
              <div className="bg-dark-800 rounded-lg overflow-hidden">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-dark-700">
                      <th className="text-left px-4 py-2 text-dark-400 font-medium">{t('storage.colFolder')}</th>
                      <th className="text-left px-4 py-2 text-dark-400 font-medium">{t('storage.colFilename')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.missingFiles.map((f) => (
                      <tr key={`${f.folder}/${f.filename}`} className="border-b border-dark-700/50 last:border-0">
                        <td className="px-4 py-2 text-dark-300">{f.folder}</td>
                        <td className="px-4 py-2 font-mono text-xs text-dark-300 break-all">{f.filename}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </section>
          )}
        </div>
      )}

      {!enabled && !data && (
        <div className="text-center py-16 text-dark-400">
          <HardDrive className="w-10 h-10 mx-auto mb-3 opacity-30" />
          <p className="text-sm">{t('storage.prompt')}</p>
        </div>
      )}

      <ConfirmModal
        isOpen={showConfirm}
        onClose={() => setShowConfirm(false)}
        onConfirm={handleDeleteConfirm}
        title={t('storage.confirmTitle')}
        message={t('storage.confirmMessage', { count: data?.orphanedFiles.length ?? 0, size: formatBytes(orphanedSize) })}
        confirmText={t('storage.deleteOrphaned')}
        variant="danger"
      />
    </div>
  )
}

function StatCard({
  label,
  value,
  icon,
  highlight = false,
}: {
  label: string
  value: string
  icon: React.ReactNode
  highlight?: boolean
}) {
  return (
    <div className={`rounded-lg p-4 border ${highlight ? 'bg-yellow-500/5 border-yellow-500/20' : 'bg-dark-800 border-dark-700'}`}>
      <div className="flex items-center gap-2 mb-1">{icon}</div>
      <div className={`text-2xl font-bold ${highlight ? 'text-yellow-400' : 'text-dark-100'}`}>{value}</div>
      <div className="text-xs text-dark-400 mt-0.5">{label}</div>
    </div>
  )
}
