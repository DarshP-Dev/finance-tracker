"use client";

import { FormEvent, useState } from "react";
import type { ReactNode } from "react";
import { Bell, CheckCircle2, LockKeyhole, Moon, Palette, UserRound } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  changePassword,
  updateProfile,
  verifyCurrentPassword,
  type AuthResponse,
} from "@/lib/api";

type SettingsPageProps = {
  auth: AuthResponse;
  isDarkMode: boolean;
  onAuthChange: (auth: AuthResponse) => void;
  onDarkModeChange: (enabled: boolean) => void;
};

export function SettingsPage({ auth, isDarkMode, onAuthChange, onDarkModeChange }: SettingsPageProps) {
  const [username, setUsername] = useState(auth.username);
  const [email, setEmail] = useState(auth.email);
  const [profilePassword, setProfilePassword] = useState("");
  const [profileMessage, setProfileMessage] = useState("");
  const [isSavingProfile, setIsSavingProfile] = useState(false);
  const [currentPassword, setCurrentPassword] = useState("");
  const [isPasswordVerified, setIsPasswordVerified] = useState(false);
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [passwordMessage, setPasswordMessage] = useState("");
  const [isVerifyingPassword, setIsVerifyingPassword] = useState(false);
  const [isSavingPassword, setIsSavingPassword] = useState(false);

  async function handleProfileSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setProfileMessage("");

    if (!profilePassword) {
      setProfileMessage("Enter your current password to save profile changes.");
      return;
    }

    setIsSavingProfile(true);
    try {
      const updatedAuth = await updateProfile({
        username: username.trim(),
        email: email.trim(),
        currentPassword: profilePassword,
      });
      onAuthChange(updatedAuth);
      setProfilePassword("");
      setProfileMessage("Profile updated.");
    } catch (error) {
      setProfileMessage(getErrorMessage(error));
    } finally {
      setIsSavingProfile(false);
    }
  }

  async function handleVerifyPassword(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPasswordMessage("");

    if (!currentPassword) {
      setPasswordMessage("Enter your current password.");
      return;
    }

    setIsVerifyingPassword(true);
    try {
      await verifyCurrentPassword(currentPassword);
      setIsPasswordVerified(true);
      setPasswordMessage("Current password verified.");
    } catch (error) {
      setIsPasswordVerified(false);
      setPasswordMessage(getErrorMessage(error));
    } finally {
      setIsVerifyingPassword(false);
    }
  }

  async function handlePasswordSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPasswordMessage("");

    if (newPassword.length < 8) {
      setPasswordMessage("New password must be at least 8 characters.");
      return;
    }

    if (newPassword !== confirmPassword) {
      setPasswordMessage("New password and confirmation do not match.");
      return;
    }

    setIsSavingPassword(true);
    try {
      await changePassword({ currentPassword, newPassword });
      setCurrentPassword("");
      setNewPassword("");
      setConfirmPassword("");
      setIsPasswordVerified(false);
      setPasswordMessage("Password changed successfully.");
    } catch (error) {
      setPasswordMessage(getErrorMessage(error));
    } finally {
      setIsSavingPassword(false);
    }
  }

  function resetPasswordVerification() {
    setIsPasswordVerified(false);
    setCurrentPassword("");
    setNewPassword("");
    setConfirmPassword("");
    setPasswordMessage("");
  }

  const profileIsUnchanged = username.trim() === auth.username && email.trim().toLowerCase() === auth.email.toLowerCase();

  return (
    <div className="grid gap-5 py-6">
      <div>
        <h1 className="text-3xl font-semibold tracking-normal text-[#151515]">Settings</h1>
        <p className="mt-2 text-sm text-[#77717d]">Manage your account, preferences, and security settings.</p>
      </div>

      <div className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_380px]">
        <section className="rounded-2xl border border-[#e4e0e7] bg-white p-5 shadow-sm">
          <SectionHeader icon={<UserRound size={18} />} title="Profile" description="Update your account identity" />
          <form onSubmit={handleProfileSubmit} className="mt-5 grid gap-4">
            <div className="grid gap-4 md:grid-cols-2">
              <label className="grid gap-2 text-sm font-semibold text-[#46404b]">
                Account name
                <Input
                  value={username}
                  onChange={(event) => setUsername(event.target.value)}
                  minLength={3}
                  maxLength={50}
                  autoComplete="username"
                  required
                />
              </label>
              <label className="grid gap-2 text-sm font-semibold text-[#46404b]">
                Email
                <Input
                  type="email"
                  value={email}
                  onChange={(event) => setEmail(event.target.value)}
                  maxLength={255}
                  autoComplete="email"
                  required
                />
              </label>
            </div>
            <label className="grid gap-2 text-sm font-semibold text-[#46404b]">
              Confirm with current password
              <Input
                type="password"
                value={profilePassword}
                onChange={(event) => setProfilePassword(event.target.value)}
                maxLength={128}
                autoComplete="current-password"
                placeholder="Required to save profile changes"
                required
              />
            </label>
            <div className="flex flex-wrap items-center gap-3">
              <Button type="submit" disabled={isSavingProfile || profileIsUnchanged}>
                {isSavingProfile ? "Saving profile…" : "Save profile"}
              </Button>
              {profileMessage && <StatusMessage message={profileMessage} success={profileMessage === "Profile updated."} />}
            </div>
          </form>
        </section>

        <section className="rounded-2xl border border-[#e4e0e7] bg-white p-5 shadow-sm">
          <SectionHeader icon={<LockKeyhole size={18} />} title="Security" description="Verify your identity before changing your password" />

          {!isPasswordVerified ? (
            <form onSubmit={handleVerifyPassword} className="mt-5 grid gap-3">
              <label className="grid gap-2 text-sm font-semibold text-[#46404b]">
                Current password
                <Input
                  type="password"
                  value={currentPassword}
                  onChange={(event) => setCurrentPassword(event.target.value)}
                  maxLength={128}
                  autoComplete="current-password"
                  placeholder="Enter current password"
                  required
                />
              </label>
              <Button type="submit" disabled={isVerifyingPassword}>
                {isVerifyingPassword ? "Verifying…" : "Verify password"}
              </Button>
            </form>
          ) : (
            <form onSubmit={handlePasswordSubmit} className="mt-5 grid gap-3 rounded-xl border border-[#eeeaf1] p-4">
              <div className="flex items-center justify-between gap-3 text-sm font-semibold text-[#027a48]">
                <span className="flex items-center gap-2"><CheckCircle2 size={17} /> Identity verified</span>
                <button type="button" onClick={resetPasswordVerification} className="text-xs font-medium text-[#77717d] underline underline-offset-4">
                  Start over
                </button>
              </div>
              <label className="grid gap-2 text-sm font-semibold text-[#46404b]">
                New password
                <Input
                  type="password"
                  value={newPassword}
                  onChange={(event) => setNewPassword(event.target.value)}
                  minLength={8}
                  maxLength={128}
                  autoComplete="new-password"
                  placeholder="At least 8 characters"
                  required
                />
              </label>
              <label className="grid gap-2 text-sm font-semibold text-[#46404b]">
                Confirm password
                <Input
                  type="password"
                  value={confirmPassword}
                  onChange={(event) => setConfirmPassword(event.target.value)}
                  minLength={8}
                  maxLength={128}
                  autoComplete="new-password"
                  placeholder="Re-enter new password"
                  required
                />
              </label>
              <Button type="submit" disabled={isSavingPassword}>
                {isSavingPassword ? "Saving password…" : "Save new password"}
              </Button>
            </form>
          )}

          {passwordMessage && (
            <div className="mt-3">
              <StatusMessage message={passwordMessage} success={passwordMessage.includes("verified") || passwordMessage.includes("successfully")} />
            </div>
          )}
        </section>

        <section className="rounded-2xl border border-[#e4e0e7] bg-white p-5 shadow-sm">
          <SectionHeader icon={<Palette size={18} />} title="Preferences" description="Dashboard display defaults" />
          <div className="mt-5 grid gap-3">
            <SettingToggle label="Dark mode" enabled={isDarkMode} onToggle={() => onDarkModeChange(!isDarkMode)} icon={<Moon size={16} />} />
            <SettingToggle label="Compact dashboard cards" enabled />
            <SettingToggle label="Show spending analytics first" enabled />
            <SettingToggle label="Use orange accent theme" enabled />
          </div>
        </section>

        <section className="rounded-2xl border border-[#e4e0e7] bg-white p-5 shadow-sm">
          <SectionHeader icon={<Bell size={18} />} title="Notifications" description="Finance alerts" />
          <div className="mt-5 grid gap-3">
            <SettingToggle label="Budget warnings" enabled />
            <SettingToggle label="Large transaction alerts" enabled={false} />
            <SettingToggle label="Weekly summary email" enabled />
          </div>
        </section>
      </div>
    </div>
  );
}

function StatusMessage({ message, success }: { message: string; success: boolean }) {
  return (
    <p role="status" className={success ? "text-sm font-medium text-[#027a48]" : "text-sm font-medium text-[#b42318]"}>
      {message}
    </p>
  );
}

type SectionHeaderProps = {
  icon: ReactNode;
  title: string;
  description: string;
};

function SectionHeader({ icon, title, description }: SectionHeaderProps) {
  return (
    <div className="flex items-start gap-3">
      <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-[#fff3ed] text-[#ff5a1f]">{icon}</div>
      <div>
        <h2 className="font-semibold text-[#151515]">{title}</h2>
        <p className="mt-1 text-sm text-[#77717d]">{description}</p>
      </div>
    </div>
  );
}

type SettingToggleProps = {
  label: string;
  enabled: boolean;
  onToggle?: () => void;
  icon?: ReactNode;
};

function SettingToggle({ label, enabled, onToggle, icon }: SettingToggleProps) {
  return (
    <button
      type="button"
      onClick={onToggle}
      className="flex items-center justify-between rounded-xl border border-[#eeeaf1] px-4 py-3 text-left transition hover:border-[#ff5a1f]"
    >
      <span className="flex items-center gap-2 text-sm font-semibold text-[#46404b]">
        {icon}
        {label}
      </span>
      <span className={enabled ? "h-6 w-11 rounded-full bg-[#ff5a1f] p-1" : "h-6 w-11 rounded-full bg-[#d8d3dc] p-1"}>
        <span className={enabled ? "block h-4 w-4 translate-x-5 rounded-full bg-white" : "block h-4 w-4 rounded-full bg-white"} />
      </span>
    </button>
  );
}

function getErrorMessage(error: unknown) {
  if (
    typeof error === "object" &&
    error !== null &&
    "response" in error &&
    typeof error.response === "object" &&
    error.response !== null &&
    "data" in error.response
  ) {
    const data = error.response.data as { message?: string };
    return data.message ?? "Request failed.";
  }

  return error instanceof Error ? error.message : "Request failed.";
}
