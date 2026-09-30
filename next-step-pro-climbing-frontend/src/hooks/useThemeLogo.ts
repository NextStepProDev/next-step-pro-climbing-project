import { useTheme } from '../context/ThemeContext'
import logoWhite from '../assets/logo/logo-white.png'
import logoBlack from '../assets/logo/logo-black.png'

/** The club logo that reads on the current theme: white on dark, black on light. */
export function useThemeLogo(): string {
  const { theme } = useTheme()
  return theme === 'dark' ? logoWhite : logoBlack
}
