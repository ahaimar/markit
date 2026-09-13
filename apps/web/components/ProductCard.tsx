import Link from "next/link";
import type { Product } from "@/lib/types";
import { formatPrice } from "@/lib/format";

export function ProductCard({ product }: { product: Product }) {
  return (
    <Link
      href={`/products/${product.id}`}
      className="group flex flex-col overflow-hidden rounded-xl border border-zinc-200 bg-white shadow-sm transition-shadow hover:shadow-md dark:border-zinc-800 dark:bg-zinc-900"
    >
      <div className="flex h-40 items-center justify-center bg-gradient-to-br from-zinc-100 to-zinc-200 text-4xl dark:from-zinc-800 dark:to-zinc-900">
        ☕
      </div>
      <div className="flex flex-1 flex-col gap-1.5 p-4">
        <span className="text-xs font-medium uppercase tracking-wide text-zinc-400 dark:text-zinc-500">
          {product.category}
        </span>
        <h3 className="font-medium text-zinc-900 group-hover:underline dark:text-zinc-100">
          {product.name}
        </h3>
        <p className="line-clamp-2 text-sm text-zinc-500 dark:text-zinc-400">{product.description}</p>
        <div className="mt-auto flex items-center justify-between pt-2">
          <span className="font-semibold text-zinc-900 dark:text-zinc-100">
            {formatPrice(product.price)}
          </span>
          <span
            className={`text-xs ${product.stockQuantity > 0 ? "text-emerald-600 dark:text-emerald-400" : "text-red-500"}`}
          >
            {product.stockQuantity > 0 ? `${product.stockQuantity} in stock` : "Out of stock"}
          </span>
        </div>
      </div>
    </Link>
  );
}