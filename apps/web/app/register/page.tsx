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

export default function RegisterPage() {
  const { register } = useAuth();
  const router = useRouter();
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    if (password.length < 8) {
      setError("Password must be at least 8 characters.");
      return;
    }
    setSubmitting(true);
    try {
      await register(email, name, password);
      router.replace("/");
    } catch (err) {
      setError(err instanceof ApiFailure ? err.message : "Registration failed. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="mx-auto flex w-full max-w-md flex-col gap-6 px-4 py-16 sm:px-6">
      <div className="flex flex-col gap-1.5">
        <h1 className="text-2xl font-semibold text-zinc-900 dark:text-zinc-50">Create an account</h1>
        <p className="text-sm text-zinc-500 dark:text-zinc-400">
          Register to start shopping with Markit.
        </p>
      </div>

      <Card>
        <form onSubmit={(e) => void handleSubmit(e)} className="flex flex-col gap-4">
          <Input
            label="Name"
            name="name"
            autoComplete="name"
            required
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
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
            autoComplete="new-password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
          <p className="text-xs text-zinc-500 dark:text-zinc-400">
            At least 8 characters, including an uppercase letter and a digit.
          </p>
          {error && <p className="text-sm text-red-600 dark:text-red-400">{error}</p>}
          <Button type="submit" loading={submitting} className="mt-1">
            Create account
          </Button>
        </form>
      </Card>

      <p className="text-center text-sm text-zinc-500 dark:text-zinc-400">
        Already have an account?{" "}
        <Link href="/login" className="font-medium text-zinc-900 underline dark:text-zinc-100">
          Log in
        </Link>
      </p>
    </div>
  );
}