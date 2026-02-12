export function getErrorMessage(error: unknown): string {
  if (error instanceof TypeError && error.message === 'Failed to fetch') {
    return 'Nie udało się połączyć z serwerem. Sprawdź połączenie internetowe.'
  }
  if (error instanceof Error) return error.message
  if (typeof error === 'string') return error
  return 'Wystąpił nieoczekiwany błąd'
}
