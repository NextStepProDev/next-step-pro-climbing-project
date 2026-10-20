import { useTranslation } from 'react-i18next'
import { formatPln } from '../../utils/money'

/**
 * Money formatted for whoever is reading, everywhere the settlement screens print a figure.
 *
 * Its own file rather than a local helper in the tab: the payer's history screen prints the same
 * figures, and a second copy is how two screens about the same money start rounding it differently.
 * A `.ts` file, so exporting a hook next to components never trips `react-refresh`.
 */
export function useMoney() {
  const { i18n } = useTranslation()
  return (amount: number) => formatPln(amount, i18n.language)
}

/**
 * Worked hours (a sum of session lengths, not a clock time) with the reader's decimal separator:
 * "12,5" in Polish and Spanish, "12.5" in English. They used to be `toFixed(1)` — a dot in every
 * language, beside amounts that Intl already printed with a comma.
 */
export function useHours() {
  const { i18n } = useTranslation()
  return (minutes: number) =>
    (minutes / 60).toLocaleString(i18n.language, { minimumFractionDigits: 1, maximumFractionDigits: 1 })
}
