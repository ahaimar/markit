"use client";

import { useState } from "react";
import type { FormEvent } from "react";
import { RequireAuth } from "@/components/RequireAuth";
import { useAuth } from "@/lib/auth";
import { getApiClient } from "@/lib/api/factory";
import { Button } from "@/components/ui/Button";
import { Input, Textarea } from "@/components/ui/Field";
import { Card } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";
import { ApiFailure } from "@/lib/types";

export default function AccountPage() {
  return (
    <RequireAuth>
      <AccountContent />
    </RequireAuth>
  );
}

function AccountContent() {
  const { user, updateProfile, logout } = useAuth();

  const [name, setName] = useState(user?.name ?? "");
  const [email, setEmail] = useState(user?.email ?? "");
  const [address, setAddress] = useState(user?.address ?? "");
  const [profileMsg, setProfileMsg] = useState<{ tone: "ok" | "err"; text: string } | null>(null);
  const [saving, setSaving] = useState(false);

  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [passwordMsg, setPasswordMsg] = useState<{ tone: "ok" | "err"; text: string } | null>(null);
  const [changing, setChanging] = useState(false);

  if (!user) return null;

  const userId = user.id;

  async function handleProfileSubmit(e: FormEvent) {
    e.preventDefault();
    setSaving(true);
    setProfileMsg(null);
    try {
      await updateProfile(userId, { name, email, address });
      setProfileMsg({ tone: "ok", text: "Profile updated." });
    } catch (err) {
      setProfileMsg({
        tone: "err",
        text: err instanceof ApiFailure ? err.message : "Could not update profile.",
      });
    } finally {
      setSaving(false);
    }
  }

  async function handlePasswordSubmit(e: FormEvent) {
    e.preventDefault();
    setChanging(true);
    setPasswordMsg(null);
    try {
      await getApiClient().changePassword(userId, { currentPassword, newPassword });
      setCurrentPassword("");
      setNewPassword("");
      setPasswordMsg({ tone: "ok", text: "Password changed." });
    } catch (err) {
      setPasswordMsg({
        tone: "err",
        text: err instanceof ApiFailure ? err.message : "Could not change password.",
      });
    } finally {
      setChanging(false);
    }
  }

  return (
    <div className="mx-auto flex w-full max-w-2xl flex-col gap-8 px-4 py-10 sm:px-6">
      <div className="flex flex-col gap-2">
        <h1 className="text-2xl font-semibold text-zinc-900 dark:text-zinc-50">Your account</h1>
        <p className="text-sm text-zinc-500 dark:text-zinc-400">
          Member since {new Date(user.createdAt).toLocaleDateString()}
        </p>
      </div>

      <div className="flex items-center gap-3">
        <div className="flex h-12 w-12 items-center justify-center rounded-full bg-zinc-900 font-semibold text-white dark:bg-zinc-100 dark:text-zinc-900">
          {user.name.slice(0, 1).toUpperCase()}
        </div>
        <div className="flex flex-col">
          <span className="font-medium text-zinc-900 dark:text-zinc-50">{user.name}</span>
          <span className="text-sm text-zinc-500 dark:text-zinc-400">{user.email}</span>
        </div>
        <div className="ml-auto">
          <Badge>{user.role}</Badge>
        </div>
      </div>

      <Card>
        <h2 className="mb-4 text-lg font-medium text-zinc-900 dark:text-zinc-50">Profile</h2>
        <form onSubmit={(e) => void handleProfileSubmit(e)} className="flex flex-col gap-4">
          <Input label="Name" name="account-name" value={name} onChange={(e) => setName(e.target.value)} />
          <Input label="Email" name="account-email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
          <Textarea
            label="Shipping address"
            name="account-address"
            value={address}
            onChange={(e) => setAddress(e.target.value)}
          />
          {profileMsg && (
            <p className={profileMsg.tone === "ok" ? "text-sm text-emerald-600" : "text-sm text-red-600"}>
              {profileMsg.text}
            </p>
          )}
          <Button type="submit" loading={saving} className="self-start">
            Save changes
          </Button>
        </form>
      </Card>

      <Card>
        <h2 className="mb-4 text-lg font-medium text-zinc-900 dark:text-zinc-50">Change password</h2>
        <form onSubmit={(e) => void handlePasswordSubmit(e)} className="flex flex-col gap-4">
          <Input
            label="Current password"
            name="current-password"
            type="password"
            autoComplete="current-password"
            value={currentPassword}
            onChange={(e) => setCurrentPassword(e.target.value)}
          />
          <Input
            label="New password"
            name="new-password"
            type="password"
            autoComplete="new-password"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
          />
          <p className="text-xs text-zinc-500 dark:text-zinc-400">
            At least 8 characters, including an uppercase letter and a digit.
          </p>
          {passwordMsg && (
            <p className={passwordMsg.tone === "ok" ? "text-sm text-emerald-600" : "text-sm text-red-600"}>
              {passwordMsg.text}
            </p>
          )}
          <Button type="submit" variant="secondary" loading={changing} className="self-start">
            Update password
          </Button>
        </form>
      </Card>

      <Button variant="ghost" onClick={() => void logout()} className="self-start">
        Log out
      </Button>
    </div>
  );
}