"use client";

import { useState } from "react";
import type { FormEvent } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { RequireAuth } from "@/components/RequireAuth";
import { useAuth } from "@/lib/auth";
import { getApiClient } from "@/lib/api/factory";
import { useApi } from "@/lib/useApi";
import { Button } from "@/components/ui/Button";
import { Card, EmptyState } from "@/components/ui/Card";
import { Textarea } from "@/components/ui/Field";
import { Price } from "@/components/ui/Badge";
import { ApiFailure } from "@/lib/types";

export default function CheckoutPage() {
  return (
    <RequireAuth>
      <CheckoutContent />
    </RequireAuth>
  );
}

function CheckoutContent() {
  const { requireUserId, user } = useAuth();
  const userId = requireUserId();
  const router = useRouter();

  const [shippingAddress, setShippingAddress] = useState(user?.address ?? "");
  const [error, setError] = useState<string | null>(null);
  const [placing, setPlacing] = useState(false);

  const { data: cart, loading } = useApi(() => getApiClient().getCart(userId), [userId]);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!cart || cart.items.length === 0) return;
    setError(null);
    setPlacing(true);
    try {
      const idempotencyKey = typeof crypto !== "undefined" ? crypto.randomUUID() : `${Date.now()}`;
      const order = await getApiClient().placeOrder(
        userId,
        { cartId: cart.cartId ?? "", shippingAddress },
        idempotencyKey,
      );
      router.replace(`/orders/${order.orderId}`);
    } catch (err) {
      setError(err instanceof ApiFailure ? err.message : "Could not place order.");
      setPlacing(false);
    }
  }

  if (loading) {
    return (
      <div className="mx-auto w-full max-w-4xl px-4 py-10 sm:px-6">
        <div className="h-60 animate-pulse rounded-xl bg-zinc-100 dark:bg-zinc-800" />
      </div>
    );
  }

  if (!cart || cart.items.length === 0) {
    return (
      <div className="mx-auto w-full max-w-4xl px-4 py-10 sm:px-6">
        <EmptyState title="Nothing to check out" hint="Your cart is empty.">
          <Link href="/products" className="mt-4 text-sm font-medium text-zinc-900 underline dark:text-zinc-100">
            Browse products →
          </Link>
        </EmptyState>
      </div>
    );
  }

  return (
    <div className="mx-auto grid w-full max-w-4xl gap-8 px-4 py-10 sm:px-6 lg:grid-cols-2">
      <div className="flex flex-col gap-4">
        <h1 className="text-2xl font-semibold text-zinc-900 dark:text-zinc-50">Checkout</h1>
        <Card>
          <form onSubmit={(e) => void handleSubmit(e)} className="flex flex-col gap-4">
            <Textarea
              label="Shipping address"
              name="shipping-address"
              required
              placeholder="Street, city, ZIP, country"
              value={shippingAddress}
              onChange={(e) => setShippingAddress(e.target.value)}
            />
            {error && <p className="text-sm text-red-600 dark:text-red-400">{error}</p>}
            <Button type="submit" loading={placing}>
              Place order
            </Button>
          </form>
        </Card>
        <p className="text-xs text-zinc-500 dark:text-zinc-400">
          Your order is protected by an idempotency key, so double-taps won&apos;t create duplicates.
        </p>
      </div>

      <div className="flex flex-col gap-4">
        <h2 className="text-lg font-medium text-zinc-900 dark:text-zinc-50">Order summary</h2>
        <Card className="flex flex-col divide-y divide-zinc-100 dark:divide-zinc-800">
          {cart.items.map((item) => (
            <div key={item.productId} className="flex items-center justify-between py-3 first:pt-0 last:pb-0">
              <div>
                <span className="font-medium text-zinc-900 dark:text-zinc-50">{item.productName}</span>
                <span className="ml-2 text-sm text-zinc-500 dark:text-zinc-400">× {item.quantity}</span>
              </div>
              <Price value={item.totalPrice} />
            </div>
          ))}
          <div className="flex items-center justify-between pt-3">
            <span className="font-semibold text-zinc-900 dark:text-zinc-50">Total</span>
            <Price value={cart.totalPrice} className="text-lg" />
          </div>
        </Card>
      </div>
    </div>
  );
}