export function validatePassword(password: string, confirmPassword: string): string | null {
  if (password !== confirmPassword) {
    return 'Hasła nie są identyczne'
  }
  if (password.length < 8) {
    return 'Hasło musi mieć co najmniej 8 znaków'
  }
  return null
}
