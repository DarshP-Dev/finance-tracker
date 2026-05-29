"use client";

import { useState } from "react";
import type { ReactNode } from "react";
import { Bell, LockKeyhole, Moon, Palette, UserRound } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import type { AuthResponse } from "@/lib/api";

type SettingsPageProps = {
  auth: AuthResponse;
  isDarkMode: boolean;
  onDarkModeChange: (enabled: boolean) => void;
};

export function SettingsPage({ auth, isDarkMode, onDarkModeChange }: SettingsPageProps) {
  const [currentPassword, setCurrentPassword] = useState("");
  const [isPasswordUnlocked, setIsPasswordUnlocked] = useState(false);
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [passwordMessage, setPasswordMessage] = useState("");

  function handleUnlockPassword() {
    if (!currentPassword.trim()) {
      setPasswordMessage("Enter your current password to unlock password changes.");
      return;
    }

    setIsPasswordUnlocked(true);
    setPasswordMessage("Password change options unlocked.");
  }

  function handlePasswordSubmit() {
    if (newPassword.length < 8) {
      setPasswordMessage("New password must be at least 8 characters.");
      return;
    }

    if (newPassword !== confirmPassword) {
      setPasswordMessage("New password and confirmation do not match.");
      return;
    }

    setPasswordMessage("Password change ready. Backend password update endpoint can be connected next.");
  }

  return (
    <div className="grid gap-5 py-6">
      <div>
        <h1 className="text-3xl font-semibold tracking-normal text-[#151515]">Settings</h1>
        <p className="mt-2 text-sm text-[#77717d]">Manage your account, preferences, and security settings.</p>
      </div>

      <div className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_380px]">
        <section className="rounded-2xl border border-[#e4e0e7] bg-white p-5 shadow-sm">
          <SectionHeader icon={<UserRound size={18} />} title="Profile" description="Your account identity" />
          <div className="mt-5 grid gap-4 md:grid-cols-2">
            <label className="grid gap-2 text-sm font-semibold text-[#46404b]">
              Account name
              <Input value={auth.username} readOnly />
            </label>
            <label className="grid gap-2 text-sm font-semibold text-[#46404b]">
              Email
              <Input value={auth.email} readOnly />
            </label>
          </div>
        </section>

        <section className="rounded-2xl border border-[#e4e0e7] bg-white p-5 shadow-sm">
          <SectionHeader icon={<LockKeyhole size={18} />} title="Security" description="JWT protected account access" />
          <div className="mt-5 grid gap-3">
            <label className="grid gap-2 text-sm font-semibold text-[#46404b]">
              Current password
              <Input
                type="password"
                value={currentPassword}
                onChange={(event) => setCurrentPassword(event.target.value)}
                placeholder="Enter current password"
              />
            </label>
            <Button type="button" onClick={handleUnlockPassword}>
              Unlock password change
            </Button>

            {isPasswordUnlocked && (
              <div className="grid gap-3 rounded-xl border border-[#eeeaf1] p-4">
                <label className="grid gap-2 text-sm font-semibold text-[#46404b]">
                  New password
                  <Input
                    type="password"
                    value={newPassword}
                    onChange={(event) => setNewPassword(event.target.value)}
                    minLength={8}
                    placeholder="At least 8 characters"
                  />
                </label>
                <label className="grid gap-2 text-sm font-semibold text-[#46404b]">
                  Confirm password
                  <Input
                    type="password"
                    value={confirmPassword}
                    onChange={(event) => setConfirmPassword(event.target.value)}
                    minLength={8}
                    placeholder="Re-enter new password"
                  />
                </label>
                <Button type="button" onClick={handlePasswordSubmit}>
                  Save new password
                </Button>
              </div>
            )}

            {passwordMessage && (
              <div className="rounded-xl bg-[#fff7f2] p-3 text-sm text-[#6f3420]">
                {passwordMessage}
              </div>
            )}
          </div>
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
