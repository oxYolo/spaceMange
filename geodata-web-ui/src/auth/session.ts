const TOKEN_KEY = 'GeoDataToken'
const ACCOUNT_KEY = 'GeoDataRememberedAccount'

export function getToken(): string {
  try {
    return sessionStorage.getItem(TOKEN_KEY) || ''
  } catch {
    return ''
  }
}

export function setToken(token: string): void {
  sessionStorage.setItem(TOKEN_KEY, token)
}

export function removeToken(): void {
  sessionStorage.removeItem(TOKEN_KEY)
}

export function getRememberedAccount(): string {
  try {
    return localStorage.getItem(ACCOUNT_KEY) || ''
  } catch {
    return ''
  }
}

export function rememberAccount(account: string, remember: boolean): void {
  if (remember) localStorage.setItem(ACCOUNT_KEY, account.trim())
  else localStorage.removeItem(ACCOUNT_KEY)
}
