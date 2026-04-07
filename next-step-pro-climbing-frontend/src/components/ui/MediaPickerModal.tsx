import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Check } from 'lucide-react'
import { useState } from 'react'
import { adminAssetsApi } from '../../api/client'
import type { AssetDto } from '../../types'
import { Modal } from './Modal'
import { LoadingSpinner } from './LoadingSpinner'

interface MediaPickerModalProps {
  isOpen: boolean
  onClose: () => void
  onSelect: (asset: AssetDto) => void
}

export function MediaPickerModal({ isOpen, onClose, onSelect }: MediaPickerModalProps) {
  const { t } = useTranslation('admin')
  const [selected, setSelected] = useState<string | null>(null)

  const { data: assets, isLoading } = useQuery({
    queryKey: ['admin', 'assets'],
    queryFn: adminAssetsApi.list,
    enabled: isOpen,
  })

  function handleSelect(asset: AssetDto) {
    setSelected(asset.id)
    onSelect(asset)
    setSelected(null)
    onClose()
  }

  return (
    <Modal isOpen={isOpen} onClose={onClose} title={t('mediaPicker.title')} size="xl">
      {isLoading ? (
        <div className="flex justify-center py-8">
          <LoadingSpinner />
        </div>
      ) : !assets || assets.length === 0 ? (
        <p className="text-center text-dark-400 py-8">{t('assets.empty')}</p>
      ) : (
        <div className="grid grid-cols-3 sm:grid-cols-4 gap-3">
          {assets.map((asset) => (
            <button
              key={asset.id}
              onClick={() => handleSelect(asset)}
              className={`relative aspect-square rounded-lg overflow-hidden border-2 transition-colors ${
                selected === asset.id
                  ? 'border-blue-500'
                  : 'border-dark-700 hover:border-dark-500'
              }`}
            >
              <img
                src={asset.url}
                alt={asset.originalName}
                className="w-full h-full object-cover"
              />
              {selected === asset.id && (
                <div className="absolute inset-0 bg-blue-500/20 flex items-center justify-center">
                  <Check className="w-6 h-6 text-white drop-shadow" />
                </div>
              )}
            </button>
          ))}
        </div>
      )}
    </Modal>
  )
}
