"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useAuth } from "@/lib/auth";

const LINKS = [
  { href: "/products", label: "Products" },
  { href: "/orders", label: "Orders" },
  { href: "/tasks", label: "Tasks" },
];

export function Navbar() {
  const { user, logout } = useAuth();
  const pathname = usePathname();

  return (
    <header className="sticky top-0 z-20 border-b border-zinc-200 bg-white/90 backdrop-blur dark:border-zinc-800 dark:bg-zinc-950/90">
      <nav className="mx-auto flex h-16 w-full max-w-6xl items-center justify-between gap-4 px-4 sm:px-6">
        <Link href="/" className="flex items-center gap-1.5 text-lg font-bold tracking-tight text-zinc-900 dark:text-zinc-50">
          <span className="text-xl">☕</span> Markit
        </Link>

        <div className="hidden items-center gap-1 sm:flex">
          {LINKS.map((link) => (
            <Link
              key={link.href}
              href={link.href}
              className={`rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
                pathname?.startsWith(link.href)
                  ? "bg-zinc-100 text-zinc-900 dark:bg-zinc-800 dark:text-zinc-100"
                  : "text-zinc-600 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-100"
              }`}
            >
              {link.label}
            </Link>
          ))}
          <Link
            href="/cart"
            className={`rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
              pathname === "/cart"
                ? "bg-zinc-100 text-zinc-900 dark:bg-zinc-800 dark:text-zinc-100"
                : "text-zinc-600 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-100"
            }`}
          >
            Cart
          </Link>
        </div>

        <div className="flex items-center gap-2">
          {user ? (
            <>
              <Link
                href="/account"
                className="rounded-lg px-3 py-2 text-sm font-medium text-zinc-600 hover:bg-zinc-100 hover:text-zinc-900 dark:text-zinc-400 dark:hover:bg-zinc-800 dark:hover:text-zinc-100"
              >
                {user.name}
              </Link>
              <button
                onClick={() => void logout()}
                className="rounded-lg px-3 py-2 text-sm font-medium text-zinc-600 hover:bg-zinc-100 hover:text-zinc-900 dark:text-zinc-400 dark:hover:bg-zinc-800 dark:hover:text-zinc-100"
              >
                Log out
              </button>
            </>
          ) : (
            <>
              <Link
                href="/login"
                className="rounded-lg px-3 py-2 text-sm font-medium text-zinc-600 hover:bg-zinc-100 hover:text-zinc-900 dark:text-zinc-400 dark:hover:bg-zinc-800 dark:hover:text-zinc-100"
              >
                Log in
              </Link>
              <Link
                href="/register"
                className="rounded-lg bg-zinc-900 px-3.5 py-2 text-sm font-medium text-white hover:bg-zinc-800 dark:bg-zinc-100 dark:text-zinc-900 dark:hover:bg-zinc-200"
              >
                Sign up
              </Link>
            </>
          )}
        </div>
      </nav>
    </header>
  );
}