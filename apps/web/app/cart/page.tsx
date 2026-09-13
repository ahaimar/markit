"use client";

import Link from "next/link";
import { RequireAuth } from "@/components/RequireAuth";
import { useAuth } from "@/lib/auth";
import { getApiClient } from "@/lib/api/factory";
import { useApi } from "@/lib/useApi";
import { Button, ButtonLink } from "@/components/ui/Button";
import { Card, EmptyState } from "@/components/ui/Card";
import { Price } from "@/components/ui/Badge";
import { ApiFailure } from "@/lib/types";

export default function CartPage() {
  return (
    <RequireAuth>
      <CartContent />
    </RequireAuth>
  );
}

function CartContent() {
  const { requireUserId } = useAuth();
  const userId = requireUserId();

  const { data: cart, loading, error, reload } = useApi(() => getApiClient().getCart(userId), [userId]);

  async function run(action: () => Promise<unknown>) {
    try {
      await action();
      reload();
    } catch (err) {
      alert(err instanceof ApiFailure ? err.message : "Something went wrong.");
    }
  }

  return (
    <div className="mx-auto flex w-full max-w-4xl flex-col gap-8 px-4 py-10 sm:px-6">
      <h1 className="text-2xl font-semibold text-zinc-900 dark:text-zinc-50">Your cart</h1>

      {loading && (
        <div className="h-40 animate-pulse rounded-xl bg-zinc-100 dark:bg-zinc-800" />
      )}

      {!loading && error && (
        <EmptyState title="Could not load your cart" hint={error.message} />
      )}

      {!loading && cart && cart.items.length === 0 && (
        <EmptyState
          title="Your cart is empty"
          hint="Browse the shop and add something you love."
        >
          <ButtonLink href="/products" className="mt-4">
            Start shopping
          </ButtonLink>
        </EmptyState>
      )}

      {!loading && cart && cart.items.length > 0 && (
        <>
          <Card className="flex flex-col divide-y divide-zinc-100 dark:divide-zinc-800">
            {cart.items.map((item) => (
              <div key={item.productId} className="flex items-center justify-between gap-4 py-4 first:pt-0 last:pb-0">
                <div className="flex flex-col gap-1">
                  <Link
                    href={`/products/${item.productId}`}
                    className="font-medium text-zinc-900 hover:underline dark:text-zinc-50"
                  >
                    {item.productName}
                  </Link>
                  <span className="text-sm text-zinc-500 dark:text-zinc-400">
                    {item.quantity} × {item.unitPrice.toFixed(2)}
                  </span>
                </div>
                <div className="flex items-center gap-4">
                  <Price value={item.totalPrice} />
                  <div className="flex gap-2">
                    <Button
                      variant="secondary"
                      className="px-3 py-1.5"
                      onClick={() => void run(() => getApiClient().addToCart(userId, item.productId, 1))}
                    >
                      +
                    </Button>
                    <Button
                      variant="ghost"
                      className="px-3 py-1.5 text-red-600 hover:bg-red-50 dark:hover:bg-red-950/40"
                      onClick={() => void run(() => getApiClient().removeFromCart(userId, item.productId))}
                    >
                      Remove
                    </Button>
                  </div>
                </div>
              </div>
            ))}
          </Card>

          <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
            <div className="flex items-center gap-4">
              <span className="text-lg font-semibold text-zinc-900 dark:text-zinc-50">
                Total
              </span>
              <Price value={cart.totalPrice} className="text-xl" />
            </div>
            <div className="flex items-center gap-3">
              <Button
                variant="ghost"
                onClick={() => void run(() => getApiClient().clearCart(userId))}
              >
                Clear cart
              </Button>
              <ButtonLink href="/checkout">Checkout</ButtonLink>
            </div>
          </div>
        </>
      )}
    </div>
  );
}