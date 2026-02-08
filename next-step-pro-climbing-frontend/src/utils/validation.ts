export function validatePassword(password: string, confirmPassword: string): string | null {
  if (password !== confirmPassword) {
    return 'Hasła nie są identyczne'
  }
  if (password.length < 4) {
    return 'Hasło musi mieć co najmniej 4 znaki'
  }
  return null
}
