import type { Session } from "@/lib/types";

const STORAGE_KEY = "markit.session.v1";

export function loadSession(): Session | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as Session) : null;
  } catch {
    return null;
  }
}

export function saveSession(session: Session): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
}

export function getAccessToken(): string | null {
  return loadSession()?.accessToken ?? null;
}

export function clearSession(): void {
  localStorage.removeItem(STORAGE_KEY);
}

export function isBrowser(): boolean {
  return typeof window !== "undefined";
}