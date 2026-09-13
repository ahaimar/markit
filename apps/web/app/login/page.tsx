"use client";

import { useState } from "react";
import type { FormEvent } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Field";
import { Card } from "@/components/ui/Card";
import { ApiFailure } from "@/lib/types";

export default function LoginPage() {
  const { login } = useAuth();
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await login(email, password);
      const redirect =
        typeof window !== "undefined" ? new URLSearchParams(window.location.search).get("redirect") : null;
      router.replace(redirect && redirect.startsWith("/") ? redirect : "/");
    } catch (err) {
      setError(err instanceof ApiFailure ? err.message : "Login failed. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="mx-auto flex w-full max-w-md flex-col gap-6 px-4 py-16 sm:px-6">
      <div className="flex flex-col gap-1.5">
        <h1 className="text-2xl font-semibold text-zinc-900 dark:text-zinc-50">Welcome back</h1>
        <p className="text-sm text-zinc-500 dark:text-zinc-400">
          Log in to continue shopping.
        </p>
      </div>

      <Card>
        <form onSubmit={(e) => void handleSubmit(e)} className="flex flex-col gap-4">
          <Input
            label="Email"
            name="email"
            type="email"
            autoComplete="email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
          <Input
            label="Password"
            name="password"
            type="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
          {error && <p className="text-sm text-red-600 dark:text-red-400">{error}</p>}
          <Button type="submit" loading={submitting} className="mt-1">
            Log in
          </Button>
        </form>
      </Card>

      <p className="text-center text-sm text-zinc-500 dark:text-zinc-400">
        No account yet?{" "}
        <Link href="/register" className="font-medium text-zinc-900 underline dark:text-zinc-100">
          Create one
        </Link>
      </p>
    </div>
  );
}