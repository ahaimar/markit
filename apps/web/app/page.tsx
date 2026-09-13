"use client";

import Link from "next/link";
import { getApiClient } from "@/lib/api/factory";
import { useApi } from "@/lib/useApi";
import { ProductCard } from "@/components/ProductCard";
import { ButtonLink } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";

export default function HomePage() {
  const { data, loading } = useApi(() => getApiClient().listProducts({ page: 0, pageSize: 3 }), []);

  return (
    <div className="mx-auto flex w-full max-w-6xl flex-col gap-12 px-4 py-12 sm:px-6">
      <section className="flex flex-col items-start gap-6 py-8 sm:py-16">
        <h1 className="max-w-2xl text-4xl font-bold tracking-tight text-zinc-900 dark:text-zinc-50 sm:text-5xl">
          Specialty coffee, made simple.
        </h1>
        <p className="max-w-xl text-lg text-zinc-600 dark:text-zinc-400">
          Single-origin beans, brewing gear, and everything you need to make a
          better cup at home.
        </p>
        <div className="flex gap-3">
          <ButtonLink href="/products">Shop now</ButtonLink>
          <ButtonLink href="/register" variant="secondary">
            Create an account
          </ButtonLink>
        </div>
      </section>

      <section className="flex flex-col gap-4">
        <div className="flex items-center justify-between">
          <h2 className="text-xl font-semibold text-zinc-900 dark:text-zinc-50">Featured products</h2>
          <Link
            href="/products"
            className="text-sm font-medium text-zinc-600 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-100"
          >
            View all →
          </Link>
        </div>

        {loading && (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {Array.from({ length: 3 }).map((_, i) => (
              <div key={i} className="h-72 animate-pulse rounded-xl bg-zinc-100 dark:bg-zinc-800" />
            ))}
          </div>
        )}
        {!loading && data && (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {data.items.map((product) => (
              <ProductCard key={product.id} product={product} />
            ))}
          </div>
        )}
        {!loading && !data && (
          <Card className="text-center text-sm text-zinc-500 dark:text-zinc-400">
            Products are unavailable right now.
          </Card>
        )}
      </section>
    </div>
  );
}