const TOKEN_KEY = 'GeoDataToken'
const ACCOUNT_KEY = 'GeoDataRememberedAccount'
type SessionListener = (token: string) => void
interface SessionState {
  token?: string
  rememberedAccount?: string
  version: number
  listeners: Set<SessionListener>
  expiredListeners: Set<() => void>
}

// HMR preserves the source of truth and existing subscriptions; refresh restores sessionStorage.
const state: SessionState = import.meta.hot?.data.geodataSession ?? {
  version: 0, listeners: new Set<SessionListener>(), expiredListeners: new Set<() => void>(),
}
if (import.meta.hot) import.meta.hot.dispose(data => { data.geodataSession = state })

function read(storage: 'sessionStorage' | 'localStorage', key: string): string {
  try { return globalThis[storage].getItem(key) || '' } catch { return '' }
}
function write(storage: 'sessionStorage' | 'localStorage', key: string, value: string): void {
  try {
    if (value) globalThis[storage].setItem(key, value)
    else globalThis[storage].removeItem(key)
  } catch { /* Restricted storage still permits an in-memory session for this page. */ }
}
export function getToken(): string {
  if (state.token === undefined) state.token = read('sessionStorage', TOKEN_KEY)
  return state.token
}
export function setToken(token: string): void {
  state.token = token.trim()
  state.version++
  write('sessionStorage', TOKEN_KEY, state.token)
  state.listeners.forEach(listener => listener(state.token!))
}
/** Reusing a token still starts a new session and invalidates earlier asynchronous work. */
export function getSessionVersion(): number { return state.version }
export function removeToken(): void { setToken('') }
export function onSessionChange(listener: SessionListener): () => void {
  state.listeners.add(listener)
  return () => state.listeners.delete(listener)
}
export function onSessionExpired(listener: () => void): () => void {
  state.expiredListeners.add(listener)
  return () => state.expiredListeners.delete(listener)
}
/** Only the matching request can expire a session; concurrent 401s notify once. */
export function invalidateSession(requestToken: string, requestVersion?: number): boolean {
  if (!requestToken || requestToken !== getToken() || (requestVersion !== undefined && requestVersion !== state.version)) return false
  removeToken()
  state.expiredListeners.forEach(listener => listener())
  return true
}
export function getRememberedAccount(): string {
  if (state.rememberedAccount === undefined) state.rememberedAccount = read('localStorage', ACCOUNT_KEY)
  return state.rememberedAccount
}
export function rememberAccount(account: string, remember: boolean): void {
  state.rememberedAccount = remember ? account.trim() : ''
  write('localStorage', ACCOUNT_KEY, state.rememberedAccount)
}
