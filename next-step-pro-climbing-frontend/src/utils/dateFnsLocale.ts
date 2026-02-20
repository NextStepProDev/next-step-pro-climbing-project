import { useTranslation } from 'react-i18next'
import { pl } from 'date-fns/locale/pl'
import { enUS } from 'date-fns/locale/en-US'
import { es } from 'date-fns/locale/es'
import type { Locale } from 'date-fns'

const localeMap: Record<string, Locale> = {
  pl,
  en: enUS,
  es,
}

export function useDateLocale(): Locale {
  const { i18n } = useTranslation()
  return localeMap[i18n.language] ?? pl
}
