"use client";

import Link from "next/link";
import type { ButtonHTMLAttributes, ReactNode } from "react";

const VARIANTS = {
  primary: "bg-zinc-900 text-white hover:bg-zinc-800 dark:bg-zinc-100 dark:text-zinc-900 dark:hover:bg-zinc-200",
  secondary: "bg-zinc-100 text-zinc-900 hover:bg-zinc-200 dark:bg-zinc-800 dark:text-zinc-100 dark:hover:bg-zinc-700",
  danger: "bg-red-600 text-white hover:bg-red-700",
  ghost: "text-zinc-600 hover:text-zinc-900 hover:bg-zinc-100 dark:text-zinc-400 dark:hover:text-zinc-100 dark:hover:bg-zinc-800",
} as const;

type Variant = keyof typeof VARIANTS;

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  loading?: boolean;
  children: ReactNode;
}

interface ButtonLinkProps {
  variant?: Variant;
  href: string;
  children: ReactNode;
  className?: string;
}

const baseClasses = (variant: Variant) =>
  `inline-flex items-center justify-center gap-2 rounded-lg px-4 py-2.5 text-sm font-medium transition-colors disabled:opacity-50 disabled:cursor-not-allowed ${VARIANTS[variant]}`;

export function Button({
  variant = "primary",
  loading = false,
  disabled,
  className = "",
  children,
  ...rest
}: ButtonProps) {
  return (
    <button
      disabled={disabled || loading}
      className={`${baseClasses(variant)} ${className}`}
      {...rest}
    >
      {loading && (
        <span className="h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent" />
      )}
      {children}
    </button>
  );
}

export function ButtonLink({ variant = "primary", href, children, className = "" }: ButtonLinkProps) {
  return (
    <Link href={href} className={`${baseClasses(variant)} ${className}`}>
      {children}
    </Link>
  );
}