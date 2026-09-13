"use client";

import { useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { getApiClient } from "@/lib/api/factory";
import { useApi } from "@/lib/useApi";
import { useAuth } from "@/lib/auth";
import { formatPrice } from "@/lib/format";
import { Button, ButtonLink } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
import { Card, EmptyState } from "@/components/ui/Card";
import { Input } from "@/components/ui/Field";
import { ApiFailure } from "@/lib/types";

export default function ProductDetailPage() {
  const params = useParams<{ id: string }>();
  const productId = params.id;
  const { user } = useAuth();
  const [quantity, setQuantity] = useState(1);
  const [action, setAction] = useState<"idle" | "adding" | "added" | "error">("idle");
  const [actionError, setActionError] = useState<string | null>(null);

  const { data: product, loading, error } = useApi(
    () => getApiClient().getProduct(productId),
    [productId],
  );

  if (loading) {
    return (
      <div className="mx-auto w-full max-w-6xl px-4 py-12 sm:px-6">
        <div className="h-80 animate-pulse rounded-xl bg-zinc-100 dark:bg-zinc-800" />
      </div>
    );
  }

  if (error && !product) {
    return (
      <div className="mx-auto w-full max-w-6xl px-4 py-12 sm:px-6">
        <EmptyState title="Product not found" hint={error.message} />
      </div>
    );
  }

  if (!product) return null;

  const currentProduct = product;
  const inStock = currentProduct.stockQuantity > 0;

  async function handleAddToCart() {
    if (!user) return;
    setAction("adding");
    setActionError(null);
    try {
      await getApiClient().addToCart(user.id, currentProduct.id, quantity);
      setAction("added");
    } catch (err) {
      setAction("error");
      setActionError(err instanceof ApiFailure ? err.message : "Could not add to cart");
    }
  }

  return (
    <div className="mx-auto grid w-full max-w-6xl gap-8 px-4 py-10 sm:px-6 lg:grid-cols-2">
      <div className="flex h-full min-h-72 items-center justify-center rounded-2xl bg-gradient-to-br from-zinc-100 to-zinc-200 text-8xl dark:from-zinc-800 dark:to-zinc-900">
        ☕
      </div>

      <div className="flex flex-col gap-5">
        <div className="flex flex-col gap-2">
          <div className="flex items-center gap-2">
            <Badge>{product.category}</Badge>
            {!inStock && <Badge tone="danger">Out of stock</Badge>}
          </div>
          <h1 className="text-3xl font-semibold tracking-tight text-zinc-900 dark:text-zinc-50">
            {product.name}
          </h1>
          <p className="text-2xl font-semibold text-zinc-900 dark:text-zinc-50">
            {formatPrice(product.price)}
          </p>
        </div>

        <p className="text-zinc-600 dark:text-zinc-400">{product.description}</p>

        <p className="text-sm text-zinc-500 dark:text-zinc-400">
          {inStock ? `${product.stockQuantity} available` : "This item is currently out of stock."}
        </p>

        <div className="flex items-end gap-3">
          <div className="w-28">
            <Input
              label="Quantity"
              type="number"
              min={1}
              max={Math.max(1, product.stockQuantity)}
              value={quantity}
              onChange={(e) => setQuantity(Math.max(1, parseInt(e.target.value, 10) || 1))}
            />
          </div>
          {user ? (
            <Button
              onClick={() => void handleAddToCart()}
              loading={action === "adding"}
              disabled={!inStock || action === "added"}
            >
              {action === "added"
                ? "Added ✓"
                : action === "error"
                  ? "Try again"
                  : "Add to cart"}
            </Button>
          ) : (
            <ButtonLink href={`/login?redirect=/products/${product.id}`}>Log in to add to cart</ButtonLink>
          )}
        </div>

        {actionError && <p className="text-sm text-red-600 dark:text-red-400">{actionError}</p>}
        {action === "added" && (
          <div className="flex items-center gap-3">
            <p className="text-sm text-emerald-600 dark:text-emerald-400">Added to your cart.</p>
            <Link
              href="/cart"
              className="text-sm font-medium text-zinc-900 underline dark:text-zinc-100"
            >
              View cart →
            </Link>
          </div>
        )}

        <Card className="mt-2">
          <p className="text-sm text-zinc-500 dark:text-zinc-400">
            Questions about this product? Contact our team and we will be happy to help.
          </p>
        </Card>
      </div>
    </div>
  );
}