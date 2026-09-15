// A thin fetch wrapper for the Spring API. Every error arrives as problem JSON with a stable
// `code` (and `fields` for validation), which the screens translate; see i18n `errors.*`.

const TOKEN_KEY = 'andaneri.token'

export class ApiError extends Error {
  status: number
  code: string
  fields: Record<string, string>
  body: Record<string, unknown>

  constructor(status: number, body: Record<string, unknown>) {
    super(String(body.code ?? status))
    this.status = status
    this.code = String(body.code ?? 'ERROR')
    this.fields = (body.fields as Record<string, string>) ?? {}
    this.body = body
  }
}

let unauthorizedHandler: (() => void) | null = null

/** Called on any 401, so an expired sign-in lands on the login page instead of a broken screen. */
export function onUnauthorized(handler: () => void) {
  unauthorizedHandler = handler
}

// Storage can throw (private mode, blocked site data); the app still works for the session.
let memoryToken: string | null = null

export function getToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY) ?? memoryToken
  } catch {
    return memoryToken
  }
}

export function setToken(token: string | null) {
  memoryToken = token
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token)
    else localStorage.removeItem(TOKEN_KEY)
  } catch {
    /* memory only */
  }
}

export type Params = Record<string, string | number | boolean | null | undefined | (string | number)[]>

export function query(params?: Params): string {
  if (!params) return ''
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value === null || value === undefined || value === '' || value === false) continue
    if (Array.isArray(value)) {
      if (value.length) search.set(key, value.join(','))
    } else {
      search.set(key, String(value))
    }
  }
  const text = search.toString()
  return text ? `?${text}` : ''
}

async function send<T>(method: string, path: string, body?: unknown, params?: Params): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`
  let payload: BodyInit | undefined
  if (body instanceof FormData) {
    payload = body
  } else if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
    payload = JSON.stringify(body)
  }
  let response: Response
  try {
    response = await fetch(`/api${path}${query(params)}`, { method, headers, body: payload })
  } catch {
    throw new ApiError(0, { code: 'NETWORK' })
  }
  if (response.status === 401 && !path.startsWith('/auth/login')) {
    unauthorizedHandler?.()
  }
  if (response.status === 204) return undefined as T
  const text = await response.text()
  const data = text ? safeJson(text) : undefined
  if (!response.ok) {
    throw new ApiError(response.status, (data as Record<string, unknown>) ?? { code: response.status >= 500 ? 'INTERNAL' : 'ERROR' })
  }
  return data as T
}

function safeJson(text: string): unknown {
  try {
    return JSON.parse(text)
  } catch {
    return { code: 'BAD_RESPONSE' }
  }
}

/** Fetches a file with the sign-in header and hands it to the browser as a download. */
async function download(path: string, params?: Params, fallbackName = 'download') {
  const token = getToken()
  const response = await fetch(`/api${path}${query(params)}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  })
  if (!response.ok) {
    const text = await response.text()
    throw new ApiError(response.status, (text && (safeJson(text) as Record<string, unknown>)) || { code: 'ERROR' })
  }
  await save(response, fallbackName)
}

/** Sends a file and saves the file that comes back (a backup turned into Excel). */
async function convert(path: string, file: File, params?: Params, fallbackName = 'download') {
  const token = getToken()
  const form = new FormData()
  form.append('file', file)
  const response = await fetch(`/api${path}${query(params)}`, {
    method: 'POST',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: form,
  })
  if (!response.ok) {
    const text = await response.text()
    throw new ApiError(response.status, (text && (safeJson(text) as Record<string, unknown>)) || { code: 'ERROR' })
  }
  await save(response, fallbackName)
}

async function save(response: Response, fallbackName: string) {
  const blob = await response.blob()
  const disposition = response.headers.get('Content-Disposition') ?? ''
  const encoded = /filename\*=UTF-8''([^;]+)/i.exec(disposition)?.[1]
  const plain = /filename="([^"]+)"/i.exec(disposition)?.[1]
  const name = encoded ? decodeURIComponent(encoded) : plain ?? fallbackName
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = name
  document.body.appendChild(link)
  link.click()
  link.remove()
  setTimeout(() => URL.revokeObjectURL(url), 10_000)
}

export const api = {
  get: <T>(path: string, params?: Params) => send<T>('GET', path, undefined, params),
  post: <T>(path: string, body?: unknown, params?: Params) => send<T>('POST', path, body ?? {}, params),
  put: <T>(path: string, body?: unknown) => send<T>('PUT', path, body ?? {}),
  patch: <T>(path: string, body?: unknown) => send<T>('PATCH', path, body ?? {}),
  del: <T>(path: string, params?: Params) => send<T>('DELETE', path, undefined, params),
  upload: <T>(path: string, file: File, params?: Params) => {
    const form = new FormData()
    form.append('file', file)
    return send<T>('POST', path, form, params)
  },
  download,
  convert,
}
