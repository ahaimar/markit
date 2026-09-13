import type { ReactNode } from "react";

export function Card({ children, className = "" }: { children: ReactNode; className?: string }) {
  return (
    <div
      className={`rounded-xl border border-zinc-200 bg-white p-5 shadow-sm dark:border-zinc-800 dark:bg-zinc-900 ${className}`}
    >
      {children}
    </div>
  );
}

export function EmptyState({
  title,
  hint,
  children,
}: {
  title: string;
  hint?: string;
  children?: ReactNode;
}) {
  return (
    <div className="flex flex-col items-center justify-center rounded-xl border border-dashed border-zinc-300 px-6 py-16 text-center dark:border-zinc-700">
      <p className="text-base font-medium text-zinc-800 dark:text-zinc-200">{title}</p>
      {hint && <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">{hint}</p>}
      {children}
    </div>
  );
}