"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import type { ReactNode } from "react";
import { getApiClient } from "@/lib/api/factory";
import { clearSession, loadSession, saveSession } from "@/lib/api/sessionStore";
import type { AuthResponse, Session, UpdateProfileInput, User } from "@/lib/types";
import { ApiFailure } from "@/lib/types";

interface AuthState {
  user: User | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, name: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  updateProfile: (userId: string, input: UpdateProfileInput) => Promise<void>;
  refreshUser: (userId: string) => Promise<void>;
  requireUserId: () => string;
}

export const AuthContext = createContext<AuthState | null>(null);

function applySession(res: AuthResponse): Session {
  const session: Session = { accessToken: res.accessToken, refreshToken: res.refreshToken, user: res.user };
  saveSession(session);
  return session;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    try {
      const session = loadSession();
      // eslint-disable-next-line react-hooks/set-state-in-effect -- hydration of persisted session from localStorage
      if (session) setUser(session.user);
    } catch {
      clearSession();
    } finally {
      setLoading(false);
    }
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const res = await getApiClient().login({ email, password });
    const session = applySession(res);
    setUser(session.user);
  }, []);

  const register = useCallback(async (email: string, name: string, password: string) => {
    const res = await getApiClient().register({ email, name, password });
    const session = applySession(res);
    setUser(session.user);
  }, []);

  const logout = useCallback(async () => {
    const session = loadSession();
    if (session) {
      try { await getApiClient().logout(session.refreshToken); } catch {
        // ignore logout errors
      }
    }
    clearSession();
    setUser(null);
  }, []);

  const updateProfile = useCallback(async (userId: string, input: UpdateProfileInput) => {
    const updated = await getApiClient().updateProfile(userId, input);
    setUser((prev) => {
      if (!prev || prev.id !== userId) return prev;
      const next = { ...prev, ...updated };
      const session = loadSession();
      if (session) saveSession({ ...session, user: next });
      return next;
    });
  }, []);

  const refreshUser = useCallback(async (userId: string) => {
    const updated = await getApiClient().getMe(userId);
    setUser((prev) => {
      if (!prev || prev.id !== userId) return prev;
      const next = { ...prev, ...updated };
      const session = loadSession();
      if (session) saveSession({ ...session, user: next });
      return next;
    });
  }, []);

  const requireUserId = useCallback(() => {
    if (!user) throw new ApiFailure(401, { code: "ERR_UNAUTHORIZED", message: "Authentication required" });
    return user.id;
  }, [user]);

  const value = useMemo<AuthState>(
    () => ({ user, loading, login, register, logout, updateProfile, refreshUser, requireUserId }),
    [user, loading, login, register, logout, updateProfile, refreshUser, requireUserId],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}