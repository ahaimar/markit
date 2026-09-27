import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor, act } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AuthProvider, useAuth } from "./auth";
import { getApiClient } from "@/lib/api/factory";
import { clearSession } from "@/lib/api/sessionStore";
import type { AuthResponse, User } from "@/lib/types";

vi.mock("@/lib/api/factory", () => ({
  getApiClient: vi.fn(),
}));

const mockUser: User = {
  id: "user-1",
  email: "test@example.com",
  name: "Test User",
  address: null,
  role: "CUSTOMER",
  createdAt: "2024-01-01T00:00:00Z",
};

const mockAuthResponse: AuthResponse = {
  accessToken: "test-access-token",
  refreshToken: "test-refresh-token",
  user: mockUser,
};

function TestComponent() {
  const auth = useAuth();
  return (
    <div>
      <div data-testid="user">{auth.user?.name ?? "none"}</div>
      <div data-testid="loading">{String(auth.loading)}</div>
      <button onClick={() => auth.login("test@example.com", "password")}>Login</button>
      <button onClick={() => auth.register("test@example.com", "Test User", "password")}>Register</button>
      <button onClick={() => auth.logout()}>Logout</button>
      <button onClick={() => auth.requireUserId()}>GetUserId</button>
      <button onClick={() => auth.updateProfile("user-1", { name: "Updated" })}>UpdateProfile</button>
      <button onClick={() => auth.refreshUser("user-1")}>RefreshUser</button>
    </div>
  );
}

describe("AuthProvider", () => {
  beforeEach(() => {
    clearSession();
    vi.mocked(getApiClient).mockReset();
  });

  it("provides user from localStorage on mount", async () => {
    const session = {
      accessToken: "stored-token",
      refreshToken: "stored-refresh",
      user: mockUser,
    };
    localStorage.setItem("markit.session.v1", JSON.stringify(session));

    render(
      <AuthProvider>
        <TestComponent />
      </AuthProvider>
    );

    await waitFor(() => {
      expect(screen.getByTestId("user")).toHaveTextContent("Test User");
      expect(screen.getByTestId("loading")).toHaveTextContent("false");
    });
  });

  it("starts with no user when localStorage is empty", async () => {
    render(
      <AuthProvider>
        <TestComponent />
      </AuthProvider>
    );

    await waitFor(() => {
      expect(screen.getByTestId("user")).toHaveTextContent("none");
      expect(screen.getByTestId("loading")).toHaveTextContent("false");
    });
  });

  it("login sets user and saves session", async () => {
    const mockLogin = vi.fn().mockResolvedValue(mockAuthResponse);
    vi.mocked(getApiClient).mockReturnValue({ login: mockLogin } as any);

    render(
      <AuthProvider>
        <TestComponent />
      </AuthProvider>
    );

    await waitFor(() => expect(screen.getByTestId("loading")).toHaveTextContent("false"));

    await act(async () => {
      screen.getByText("Login").click();
    });

    expect(mockLogin).toHaveBeenCalledWith({ email: "test@example.com", password: "password" });
    await waitFor(() => {
      expect(screen.getByTestId("user")).toHaveTextContent("Test User");
    });

    const stored = localStorage.getItem("markit.session.v1");
    expect(stored).toBeTruthy();
  });

  it("register sets user and saves session", async () => {
    const mockRegister = vi.fn().mockResolvedValue(mockAuthResponse);
    vi.mocked(getApiClient).mockReturnValue({ register: mockRegister } as any);

    render(
      <AuthProvider>
        <TestComponent />
      </AuthProvider>
    );

    await waitFor(() => expect(screen.getByTestId("loading")).toHaveTextContent("false"));

    await act(async () => {
      screen.getByText("Register").click();
    });

    expect(mockRegister).toHaveBeenCalledWith({ email: "test@example.com", name: "Test User", password: "password" });
    await waitFor(() => {
      expect(screen.getByTestId("user")).toHaveTextContent("Test User");
    });
  });

  it("logout clears user and session", async () => {
    const session = {
      accessToken: "stored-token",
      refreshToken: "stored-refresh",
      user: mockUser,
    };
    localStorage.setItem("markit.session.v1", JSON.stringify(session));

    const mockLogout = vi.fn().mockResolvedValue(undefined);
    vi.mocked(getApiClient).mockReturnValue({ logout: mockLogout } as any);

    render(
      <AuthProvider>
        <TestComponent />
      </AuthProvider>
    );

    await waitFor(() => expect(screen.getByTestId("user")).toHaveTextContent("Test User"));

    await act(async () => {
      screen.getByText("Logout").click();
    });

    expect(mockLogout).toHaveBeenCalledWith("stored-refresh");
    await waitFor(() => {
      expect(screen.getByTestId("user")).toHaveTextContent("none");
    });
    expect(localStorage.getItem("markit.session.v1")).toBeNull();
  });

  it("logout ignores API errors", async () => {
    const session = {
      accessToken: "stored-token",
      refreshToken: "stored-refresh",
      user: mockUser,
    };
    localStorage.setItem("markit.session.v1", JSON.stringify(session));

    const mockLogout = vi.fn().mockRejectedValue(new Error("Network error"));
    vi.mocked(getApiClient).mockReturnValue({ logout: mockLogout } as any);

    render(
      <AuthProvider>
        <TestComponent />
      </AuthProvider>
    );

    await waitFor(() => expect(screen.getByTestId("user")).toHaveTextContent("Test User"));

    await act(async () => {
      screen.getByText("Logout").click();
    });

    await waitFor(() => {
      expect(screen.getByTestId("user")).toHaveTextContent("none");
    });
  });

  it("updateProfile updates user name", async () => {
    const session = {
      accessToken: "stored-token",
      refreshToken: "stored-refresh",
      user: mockUser,
    };
    localStorage.setItem("markit.session.v1", JSON.stringify(session));

    const updatedUser = { ...mockUser, name: "Updated Name" };
    const mockUpdateProfile = vi.fn().mockResolvedValue(updatedUser);
    vi.mocked(getApiClient).mockReturnValue({ updateProfile: mockUpdateProfile } as any);

    render(
      <AuthProvider>
        <TestComponent />
      </AuthProvider>
    );

    await waitFor(() => expect(screen.getByTestId("user")).toHaveTextContent("Test User"));

    await act(async () => {
      screen.getByText("UpdateProfile").click();
    });

    expect(mockUpdateProfile).toHaveBeenCalledWith("user-1", { name: "Updated" });
    await waitFor(() => {
      expect(screen.getByTestId("user")).toHaveTextContent("Updated Name");
    });
  });

  it("refreshUser updates user data", async () => {
    const session = {
      accessToken: "stored-token",
      refreshToken: "stored-refresh",
      user: mockUser,
    };
    localStorage.setItem("markit.session.v1", JSON.stringify(session));

    const refreshedUser = { ...mockUser, name: "Refreshed User" };
    const mockGetMe = vi.fn().mockResolvedValue(refreshedUser);
    vi.mocked(getApiClient).mockReturnValue({ getMe: mockGetMe } as any);

    render(
      <AuthProvider>
        <TestComponent />
      </AuthProvider>
    );

    await waitFor(() => expect(screen.getByTestId("user")).toHaveTextContent("Test User"));

    await act(async () => {
      screen.getByText("RefreshUser").click();
    });

    expect(mockGetMe).toHaveBeenCalledWith("user-1");
    await waitFor(() => {
      expect(screen.getByTestId("user")).toHaveTextContent("Refreshed User");
    });
  });

  it("requireUserId throws when not authenticated", async () => {
    const mockRequireUserId = vi.fn();
    vi.mocked(getApiClient).mockReturnValue({} as any);

    function ThrowingComponent() {
      const auth = useAuth();
      return <button onClick={() => auth.requireUserId()}>GetUserId</button>;
    }

    render(
      <AuthProvider>
        <ThrowingComponent />
      </AuthProvider>
    );

    await waitFor(() => expect(screen.getByTestId("loading")).toHaveTextContent("false"));

    let error: Error | null = null;
    try {
      await act(async () => {
        screen.getByText("GetUserId").click();
      });
    } catch (e) {
      error = e as Error;
    }

    expect(error).not.toBeNull();
    expect(error!.message).toContain("Authentication required");
  });

  it("requireUserId returns userId when authenticated", async () => {
    const session = {
      accessToken: "stored-token",
      refreshToken: "stored-refresh",
      user: mockUser,
    };
    localStorage.setItem("markit.session.v1", JSON.stringify(session));

    let userId: string | null = null;

    function GettingComponent() {
      const auth = useAuth();
      return (
        <button
          onClick={() => {
            try {
              userId = auth.requireUserId();
            } catch {}
          }}
        >
          GetUserId
        </button>
      );
    }

    render(
      <AuthProvider>
        <GettingComponent />
      </AuthProvider>
    );

    await waitFor(() => expect(screen.getByTestId("loading")).toHaveTextContent("false"));

    await act(async () => {
      screen.getByText("GetUserId").click();
    });

    expect(userId).toBe("user-1");
  });
});
