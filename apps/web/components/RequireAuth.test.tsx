import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { RequireAuth } from "./RequireAuth";
import { AuthProvider } from "@/lib/auth";

vi.mock("next/navigation", () => ({
  useRouter: vi.fn().mockReturnValue({
    replace: vi.fn(),
  }),
}));

vi.mock("@/lib/auth", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/auth")>();
  return {
    ...actual,
    useAuth: vi.fn(),
  };
});

import { useAuth } from "@/lib/auth";

const mockUser = {
  id: "user-1",
  email: "test@example.com",
  name: "Test User",
  address: null,
  role: "CUSTOMER" as const,
  createdAt: "2024-01-01T00:00:00Z",
};

describe("RequireAuth", () => {
  beforeEach(() => {
    vi.mocked(useAuth).mockReset();
  });

  it("shows loading spinner while loading", () => {
    vi.mocked(useAuth).mockReturnValue({
      user: null,
      loading: true,
      login: vi.fn(),
      register: vi.fn(),
      logout: vi.fn(),
      updateProfile: vi.fn(),
      refreshUser: vi.fn(),
      requireUserId: vi.fn(),
    });

    render(
      <AuthProvider>
        <RequireAuth>
          <div>Protected content</div>
        </RequireAuth>
      </AuthProvider>
    );

    expect(screen.queryByText("Protected content")).not.toBeInTheDocument();
  });

  it("redirects to login when not authenticated", async () => {
    const mockReplace = vi.fn();
    const { useRouter } = await import("next/navigation");
    vi.mocked(useRouter).mockReturnValue({ replace: mockReplace } as any);

    vi.mocked(useAuth).mockReturnValue({
      user: null,
      loading: false,
      login: vi.fn(),
      register: vi.fn(),
      logout: vi.fn(),
      updateProfile: vi.fn(),
      refreshUser: vi.fn(),
      requireUserId: vi.fn(),
    });

    render(
      <AuthProvider>
        <RequireAuth>
          <div>Protected content</div>
        </RequireAuth>
      </AuthProvider>
    );

    await waitFor(() => {
      expect(mockReplace).toHaveBeenCalledWith("/login");
    });
  });

  it("renders children when authenticated", () => {
    vi.mocked(useAuth).mockReturnValue({
      user: mockUser,
      loading: false,
      login: vi.fn(),
      register: vi.fn(),
      logout: vi.fn(),
      updateProfile: vi.fn(),
      refreshUser: vi.fn(),
      requireUserId: vi.fn(),
    });

    render(
      <AuthProvider>
        <RequireAuth>
          <div>Protected content</div>
        </RequireAuth>
      </AuthProvider>
    );

    expect(screen.getByText("Protected content")).toBeInTheDocument();
  });

  it("renders nothing when not authenticated and not loading", () => {
    vi.mocked(useAuth).mockReturnValue({
      user: null,
      loading: false,
      login: vi.fn(),
      register: vi.fn(),
      logout: vi.fn(),
      updateProfile: vi.fn(),
      refreshUser: vi.fn(),
      requireUserId: vi.fn(),
    });

    render(
      <AuthProvider>
        <RequireAuth>
          <div>Protected content</div>
        </RequireAuth>
      </AuthProvider>
    );

    expect(screen.queryByText("Protected content")).not.toBeInTheDocument();
  });
});
