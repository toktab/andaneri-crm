import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { api, getToken, onUnauthorized, setToken } from './api'
import type { LoginResponse, UserDto } from './types'

interface Auth {
  user: UserDto | null
  /** True until the saved sign-in has been checked with the server. */
  checking: boolean
  isSupervisor: boolean
  isAdmin: boolean
  /** The account from ROOT_USERNAME: everything admins can do, plus the security centre. */
  isRoot: boolean
  login: (username: string, password: string) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<Auth | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserDto | null>(null)
  const [checking, setChecking] = useState(() => Boolean(getToken()))
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  const logout = useCallback(() => {
    setToken(null)
    setUser(null)
    queryClient.clear()
    navigate('/login', { replace: true })
  }, [navigate, queryClient])

  useEffect(() => {
    onUnauthorized(() => {
      if (getToken()) logout()
    })
  }, [logout])

  useEffect(() => {
    if (!getToken()) return
    api.get<UserDto>('/auth/me')
      .then(setUser)
      .catch(() => setToken(null))
      .finally(() => setChecking(false))
  }, [])

  const login = useCallback(async (username: string, password: string) => {
    const response = await api.post<LoginResponse>('/auth/login', { username, password })
    setToken(response.token)
    queryClient.clear()
    setUser(response.user)
  }, [queryClient])

  const value = useMemo<Auth>(() => ({
    user,
    checking,
    isSupervisor: user?.role === 'SUPERVISOR' || user?.role === 'ADMIN' || user?.role === 'ROOT',
    isAdmin: user?.role === 'ADMIN' || user?.role === 'ROOT',
    isRoot: user?.role === 'ROOT',
    login,
    logout,
  }), [user, checking, login, logout])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): Auth {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth outside AuthProvider')
  return context
}
