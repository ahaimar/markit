"use client";

import Link from "next/link";
import { RequireAuth } from "@/components/RequireAuth";
import { useAuth } from "@/lib/auth";
import { getApiClient } from "@/lib/api/factory";
import { useApi } from "@/lib/useApi";
import { Card, EmptyState } from "@/components/ui/Card";
import { Price, OrderStatusBadge } from "@/components/ui/Badge";
import { ButtonLink } from "@/components/ui/Button";

export default function OrdersPage() {
  return (
    <RequireAuth>
      <OrdersContent />
    </RequireAuth>
  );
}

function OrdersContent() {
  const { requireUserId } = useAuth();
  const userId = requireUserId();

  const { data, loading, error } = useApi(() => getApiClient().listOrders(userId), [userId]);

  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-6 px-4 py-10 sm:px-6">
      <h1 className="text-2xl font-semibold text-zinc-900 dark:text-zinc-50">Your orders</h1>

      {loading && <div className="h-40 animate-pulse rounded-xl bg-zinc-100 dark:bg-zinc-800" />}

      {!loading && error && <EmptyState title="Could not load orders" hint={error.message} />}

      {!loading && data && data.items.length === 0 && (
        <EmptyState title="No orders yet" hint="When you place an order it will show up here.">
          <ButtonLink href="/products" className="mt-4">
            Start shopping
          </ButtonLink>
        </EmptyState>
      )}

      {!loading && data && data.items.length > 0 && (
        <div className="flex flex-col gap-3">
          {data.items.map((order) => (
            <Link key={order.orderId} href={`/orders/${order.orderId}`}>
              <Card className="flex items-center justify-between transition-colors hover:border-zinc-300 dark:hover:border-zinc-700">
                <div className="flex flex-col gap-1">
                  <span className="font-mono text-sm text-zinc-600 dark:text-zinc-400">
                    #{order.orderId.slice(0, 8)}
                  </span>
                  <span className="text-sm text-zinc-500 dark:text-zinc-400">
                    {new Date(order.createdAt).toLocaleDateString()} · {order.items.length} item
                    {order.items.length === 1 ? "" : "s"}
                  </span>
                </div>
                <div className="flex items-center gap-4">
                  <OrderStatusBadge status={order.status} />
                  <Price value={order.totalPrice} />
                </div>
              </Card>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}