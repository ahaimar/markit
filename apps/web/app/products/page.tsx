import { Suspense } from "react";
import { ProductsCatalog } from "./products-catalog";

export default function ProductsPage() {
  return (
    <Suspense fallback={<ProductsLoading />}>
      <ProductsCatalog />
    </Suspense>
  );
}

function ProductsLoading() {
  return (
    <div className="mx-auto grid w-full max-w-6xl gap-4 px-4 py-8 sm:grid-cols-2 lg:grid-cols-3 sm:px-6">
      {Array.from({ length: 6 }).map((_, i) => (
        <div key={i} className="h-64 animate-pulse rounded-xl bg-zinc-100 dark:bg-zinc-800" />
      ))}
    </div>
  );
}