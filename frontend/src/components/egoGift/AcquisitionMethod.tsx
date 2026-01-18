import { useTranslation } from 'react-i18next'

interface AcquisitionMethodProps {
  obtain: string
}

export default function AcquisitionMethod({ obtain }: AcquisitionMethodProps) {
  const { t } = useTranslation('database')

  return (
    <div className="border rounded p-4">
      <h3 className="text-lg font-semibold mb-2">{t('egoGift.acquisitionMethod')}</h3>
      <p className="text-sm text-gray-700">{obtain}</p>
    </div>
  )
}
