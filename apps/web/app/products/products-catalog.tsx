"use client";

import { useCallback } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { getApiClient } from "@/lib/api/factory";
import { useApi } from "@/lib/useApi";
import { ProductCard } from "@/components/ProductCard";
import { Input } from "@/components/ui/Field";
import { EmptyState } from "@/components/ui/Card";
import { ApiFailure } from "@/lib/types";

const CATEGORIES = ["All", "Coffee", "Mugs", "Brewing"] as const;
const PAGE_SIZE = 12;

export function ProductsCatalog() {
  const router = useRouter();
  const searchParams = useSearchParams();

  const page = Math.max(0, parseInt(searchParams.get("page") ?? "0", 10) || 0);
  const category = searchParams.get("category") ?? "All";
  const search = searchParams.get("search") ?? "";

  const { data, loading, error } = useApi(
    () =>
      getApiClient().listProducts({
        page,
        pageSize: PAGE_SIZE,
        category: category === "All" ? undefined : category,
        search: search || undefined,
      }),
    [page, category, search],
  );

  const updateSearch = useCallback(
    (patch: Record<string, string>) => {
      const next = new URLSearchParams(searchParams.toString());
      for (const [key, value] of Object.entries(patch)) {
        if (value === "" || value === "All") next.delete(key);
        else next.set(key, value);
      }
      next.set("page", "0");
      router.replace(`/products?${next.toString()}`);
    },
    [router, searchParams],
  );

  const totalPages = data ? Math.max(1, Math.ceil(data.total / PAGE_SIZE)) : 1;

  return (
    <div className="mx-auto flex w-full max-w-6xl flex-col gap-8 px-4 py-8 sm:px-6">
      <div className="flex flex-col gap-4">
        <h1 className="text-2xl font-semibold text-zinc-900 dark:text-zinc-50">Shop</h1>
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex flex-wrap gap-2">
            {CATEGORIES.map((c) => (
              <button
                key={c}
                onClick={() => updateSearch({ category: c })}
                className={`rounded-full px-3.5 py-1.5 text-sm font-medium transition-colors ${
                  category === c
                    ? "bg-zinc-900 text-white dark:bg-zinc-100 dark:text-zinc-900"
                    : "bg-zinc-100 text-zinc-700 hover:bg-zinc-200 dark:bg-zinc-800 dark:text-zinc-300 dark:hover:bg-zinc-700"
                }`}
              >
                {c}
              </button>
            ))}
          </div>
          <form
            className="sm:w-64"
            onSubmit={(e) => {
              e.preventDefault();
              const value = new FormData(e.currentTarget).get("search");
              updateSearch({ search: typeof value === "string" ? value : "" });
            }}
          >
            <Input name="search" placeholder="Search products…" defaultValue={search} />
          </form>
        </div>
      </div>

      {loading && (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 6 }).map((_, i) => (
            <div key={i} className="h-64 animate-pulse rounded-xl bg-zinc-100 dark:bg-zinc-800" />
          ))}
        </div>
      )}

      {!loading && error && (
        <EmptyState
          title="Could not load products"
          hint={error instanceof ApiFailure ? error.message : "Please try again later."}
        />
      )}

      {!loading && data && data.items.length === 0 && (
        <EmptyState title="No products found" hint="Try a different search or category." />
      )}

      {!loading && data && data.items.length > 0 && (
        <>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {data.items.map((product) => (
              <ProductCard key={product.id} product={product} />
            ))}
          </div>

          {totalPages > 1 && (
            <div className="flex items-center justify-center gap-4">
              <button
                onClick={() => updateSearch({ page: String(Math.max(0, page - 1)) })}
                disabled={page <= 0}
                className="rounded-lg border border-zinc-300 px-4 py-2 text-sm font-medium text-zinc-700 disabled:opacity-40 dark:border-zinc-700 dark:text-zinc-300"
              >
                Previous
              </button>
              <span className="text-sm text-zinc-600 dark:text-zinc-400">
                Page {page + 1} of {totalPages}
              </span>
              <button
                onClick={() => updateSearch({ page: String(Math.min(totalPages - 1, page + 1)) })}
                disabled={page >= totalPages - 1}
                className="rounded-lg border border-zinc-300 px-4 py-2 text-sm font-medium text-zinc-700 disabled:opacity-40 dark:border-zinc-700 dark:text-zinc-300"
              >
                Next
              </button>
            </div>
          )}
        </>
      )}

      {!loading && data && (
        <p className="text-center text-sm text-zinc-500 dark:text-zinc-400">
          {data.total} product{data.total === 1 ? "" : "s"}
        </p>
      )}
    </div>
  );
}