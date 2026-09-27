import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Navbar } from "./Navbar";
import { AuthProvider, useAuth } from "@/lib/auth";

vi.mock("next/navigation", () => ({
  usePathname: vi.fn().mockReturnValue("/"),
}));

vi.mock("@/lib/auth", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/auth")>();
  return {
    ...actual,
    useAuth: vi.fn(),
  };
});

const mockUser = {
  id: "user-1",
  email: "test@example.com",
  name: "Test User",
  address: null,
  role: "CUSTOMER" as const,
  createdAt: "2024-01-01T00:00:00Z",
};

function renderNavbar() {
  return render(
    <AuthProvider>
      <Navbar />
    </AuthProvider>
  );
}

describe("Navbar", () => {
  beforeEach(() => {
    vi.mocked(useAuth).mockReset();
  });

  it("renders Markit brand", () => {
    vi.mocked(useAuth).mockReturnValue({
      user: null,
      loading: false,
      login: vi.fn(),
      register: vi.fn(),
      logout: vi.fn().mockResolvedValue(undefined),
      updateProfile: vi.fn(),
      refreshUser: vi.fn(),
      requireUserId: vi.fn(),
    });

    renderNavbar();
    expect(screen.getByText("Markit")).toBeInTheDocument();
  });

  it("renders navigation links", () => {
    vi.mocked(useAuth).mockReturnValue({
      user: null,
      loading: false,
      login: vi.fn(),
      register: vi.fn(),
      logout: vi.fn().mockResolvedValue(undefined),
      updateProfile: vi.fn(),
      refreshUser: vi.fn(),
      requireUserId: vi.fn(),
    });

    renderNavbar();
    expect(screen.getByRole("link", { name: "Products" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Orders" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Tasks" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Cart" })).toBeInTheDocument();
  });

  it("shows login and signup buttons when not authenticated", () => {
    vi.mocked(useAuth).mockReturnValue({
      user: null,
      loading: false,
      login: vi.fn(),
      register: vi.fn(),
      logout: vi.fn().mockResolvedValue(undefined),
      updateProfile: vi.fn(),
      refreshUser: vi.fn(),
      requireUserId: vi.fn(),
    });

    renderNavbar();
    expect(screen.getByRole("link", { name: "Log in" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Sign up" })).toBeInTheDocument();
  });

  it("shows user name and logout button when authenticated", () => {
    vi.mocked(useAuth).mockReturnValue({
      user: mockUser,
      loading: false,
      login: vi.fn(),
      register: vi.fn(),
      logout: vi.fn().mockResolvedValue(undefined),
      updateProfile: vi.fn(),
      refreshUser: vi.fn(),
      requireUserId: vi.fn(),
    });

    renderNavbar();
    expect(screen.getByText("Test User")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Log out" })).toBeInTheDocument();
  });

  it("calls logout when logout button is clicked", async () => {
    const mockLogout = vi.fn().mockResolvedValue(undefined);
    vi.mocked(useAuth).mockReturnValue({
      user: mockUser,
      loading: false,
      login: vi.fn(),
      register: vi.fn(),
      logout: mockLogout,
      updateProfile: vi.fn(),
      refreshUser: vi.fn(),
      requireUserId: vi.fn(),
    });

    renderNavbar();
    await userEvent.click(screen.getByRole("button", { name: "Log out" }));
    expect(mockLogout).toHaveBeenCalled();
  });

  it("highlights active navigation link", async () => {
    const { usePathname } = await import("next/navigation");
    vi.mocked(usePathname).mockReturnValue("/products");

    vi.mocked(useAuth).mockReturnValue({
      user: null,
      loading: false,
      login: vi.fn(),
      register: vi.fn(),
      logout: vi.fn().mockResolvedValue(undefined),
      updateProfile: vi.fn(),
      refreshUser: vi.fn(),
      requireUserId: vi.fn(),
    });

    renderNavbar();
    const productsLink = screen.getByRole("link", { name: "Products" });
    expect(productsLink.className).toContain("bg-zinc-100");
  });
});
