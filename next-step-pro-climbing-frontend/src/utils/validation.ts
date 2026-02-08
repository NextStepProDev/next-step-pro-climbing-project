export function validatePassword(password: string, confirmPassword: string): string | null {
  if (password !== confirmPassword) {
    return 'Hasła nie są identyczne'
  }
  if (password.length < 8) {
    return 'Hasło musi mieć co najmniej 8 znaków'
  }
  if (!/[a-z]/.test(password)) {
    return 'Hasło musi zawierać małą literę'
  }
  if (!/[A-Z]/.test(password)) {
    return 'Hasło musi zawierać wielką literę'
  }
  if (!/\d/.test(password)) {
    return 'Hasło musi zawierać cyfrę'
  }
  if (!/[@$!%*?&]/.test(password)) {
    return 'Hasło musi zawierać znak specjalny (@$!%*?&)'
  }
  return null
}
