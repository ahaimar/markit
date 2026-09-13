import Link from "next/link";

export function Footer() {
  return (
    <footer className="border-t border-zinc-200 py-8 dark:border-zinc-800">
      <div className="mx-auto flex w-full max-w-6xl flex-col items-center justify-between gap-4 px-4 text-sm text-zinc-500 dark:text-zinc-400 sm:flex-row sm:px-6">
        <p>© {new Date().getFullYear()} Markit. All rights reserved.</p>
        <div className="flex items-center gap-4">
          <Link href="/products" className="hover:text-zinc-900 dark:hover:text-zinc-100">
            Shop
          </Link>
          <Link href="/orders" className="hover:text-zinc-900 dark:hover:text-zinc-100">
            Orders
          </Link>
        </div>
      </div>
    </footer>
  );
}