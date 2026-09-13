import type { ReactNode } from "react";
import { formatPrice } from "@/lib/format";

export function Price({ value, className = "" }: { value: number; className?: string }) {
  return <span className={`font-semibold ${className}`}>{formatPrice(value)}</span>;
}

export function Badge({ children, tone = "neutral" }: { children: ReactNode; tone?: "neutral" | "success" | "warning" | "danger" }) {
  const tones = {
    neutral: "bg-zinc-100 text-zinc-700 dark:bg-zinc-800 dark:text-zinc-300",
    success: "bg-emerald-100 text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-300",
    warning: "bg-amber-100 text-amber-700 dark:bg-amber-900/40 dark:text-amber-300",
    danger: "bg-red-100 text-red-700 dark:bg-red-900/40 dark:text-red-300",
  } as const;
  return (
    <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${tones[tone]}`}>
      {children}
    </span>
  );
}

function statusTone(status: string): "neutral" | "success" | "warning" | "danger" {
  switch (status) {
    case "DELIVERED":
    case "CONFIRMED":
      return "success";
    case "PENDING":
      return "warning";
    case "CANCELLED":
      return "danger";
    default:
      return "neutral";
  }
}

export function OrderStatusBadge({ status }: { status: string }) {
  return <Badge tone={statusTone(status)}>{status}</Badge>;
}

function taskStatusTone(status: string): "neutral" | "success" | "warning" | "danger" {
  switch (status) {
    case "DONE":
      return "success";
    case "IN_PROGRESS":
      return "warning";
    default:
      return "neutral";
  }
}

export function TaskStatusBadge({ status }: { status: string }) {
  return <Badge tone={taskStatusTone(status)}>{status}</Badge>;
}

export function TaskPriorityBadge({ priority }: { priority: string }) {
  return <Badge tone={priority === "HIGH" ? "danger" : priority === "MEDIUM" ? "warning" : "neutral"}>{priority}</Badge>;
}