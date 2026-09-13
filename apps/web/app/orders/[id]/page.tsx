"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { RequireAuth } from "@/components/RequireAuth";
import { useAuth } from "@/lib/auth";
import { getApiClient } from "@/lib/api/factory";
import { useApi } from "@/lib/useApi";
import { Card, EmptyState } from "@/components/ui/Card";
import { Price, OrderStatusBadge } from "@/components/ui/Badge";
import { formatPrice } from "@/lib/format";

export default function OrderDetailPage() {
  return (
    <RequireAuth>
      <OrderDetailContent />
    </RequireAuth>
  );
}

function OrderDetailContent() {
  const params = useParams<{ id: string }>();
  const orderId = params.id;
  const { requireUserId } = useAuth();
  const userId = requireUserId();

  const { data: order, loading, error } = useApi(() => getApiClient().getOrder(userId, orderId), [
    userId,
    orderId,
  ]);

  if (loading) {
    return (
      <div className="mx-auto w-full max-w-3xl px-4 py-10 sm:px-6">
        <div className="h-60 animate-pulse rounded-xl bg-zinc-100 dark:bg-zinc-800" />
      </div>
    );
  }

  if (error && !order) {
    return <EmptyState title="Order not found" hint={error.message} />;
  }

  if (!order) return null;

  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-6 px-4 py-10 sm:px-6">
      <div className="flex flex-col gap-2">
        <Link
          href="/orders"
          className="text-sm font-medium text-zinc-500 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-100"
        >
          ← All orders
        </Link>
        <div className="flex flex-col gap-1 sm:flex-row sm:items-center sm:justify-between">
          <h1 className="text-2xl font-semibold text-zinc-900 dark:text-zinc-50">
            Order #{order.orderId.slice(0, 8)}
          </h1>
          <OrderStatusBadge status={order.status} />
        </div>
        <p className="text-sm text-zinc-500 dark:text-zinc-400">
          Placed on {new Date(order.createdAt).toLocaleString()}
        </p>
      </div>

      <Card className="flex flex-col divide-y divide-zinc-100 dark:divide-zinc-800">
        {order.items.map((item) => (
          <div key={item.productId} className="flex items-center justify-between py-3 first:pt-0 last:pb-0">
            <div>
              <span className="font-medium text-zinc-900 dark:text-zinc-50">{item.productName}</span>
              <span className="ml-2 text-sm text-zinc-500 dark:text-zinc-400">
                × {item.quantity} · {formatPrice(item.priceAtPurchase)} each
              </span>
            </div>
            <Price value={item.priceAtPurchase * item.quantity} />
          </div>
        ))}
        <div className="flex items-center justify-between pt-3">
          <span className="font-semibold text-zinc-900 dark:text-zinc-50">Total</span>
          <Price value={order.totalPrice} className="text-lg" />
        </div>
      </Card>

      <Card>
        <h2 className="mb-1 text-sm font-medium text-zinc-900 dark:text-zinc-50">Shipping address</h2>
        <p className="text-sm text-zinc-500 dark:text-zinc-400">
          {order.shippingAddress || "No address provided"}
        </p>
      </Card>
    </div>
  );
}