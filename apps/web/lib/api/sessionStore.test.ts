import { describe, it, expect, beforeEach } from "vitest";
import { loadSession, saveSession, getAccessToken, clearSession, isBrowser } from "./sessionStore";
import type { Session } from "@/lib/types";

const mockSession: Session = {
  accessToken: "test-access-token",
  refreshToken: "test-refresh-token",
  user: {
    id: "user-1",
    email: "test@example.com",
    name: "Test User",
    address: null,
    role: "CUSTOMER",
    createdAt: "2024-01-01T00:00:00Z",
  },
};

describe("sessionStore", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  describe("saveSession", () => {
    it("saves session to localStorage", () => {
      saveSession(mockSession);
      const raw = localStorage.getItem("markit.session.v1");
      expect(raw).toBeTruthy();
      expect(JSON.parse(raw!)).toEqual(mockSession);
    });

    it("overwrites existing session", () => {
      saveSession(mockSession);
      const newSession = { ...mockSession, accessToken: "new-token" };
      saveSession(newSession);
      expect(loadSession()?.accessToken).toBe("new-token");
    });
  });

  describe("loadSession", () => {
    it("returns null when no session exists", () => {
      expect(loadSession()).toBeNull();
    });

    it("returns session when one exists", () => {
      saveSession(mockSession);
      expect(loadSession()).toEqual(mockSession);
    });

    it("returns null for invalid JSON", () => {
      localStorage.setItem("markit.session.v1", "not-json{{{");
      expect(loadSession()).toBeNull();
    });
  });

  describe("getAccessToken", () => {
    it("returns null when no session exists", () => {
      expect(getAccessToken()).toBeNull();
    });

    it("returns access token when session exists", () => {
      saveSession(mockSession);
      expect(getAccessToken()).toBe("test-access-token");
    });
  });

  describe("clearSession", () => {
    it("removes session from localStorage", () => {
      saveSession(mockSession);
      clearSession();
      expect(loadSession()).toBeNull();
      expect(localStorage.getItem("markit.session.v1")).toBeNull();
    });

    it("does not throw when no session exists", () => {
      expect(() => clearSession()).not.toThrow();
    });
  });

  describe("isBrowser", () => {
    it("returns true in browser environment", () => {
      expect(isBrowser()).toBe(true);
    });
  });
});
