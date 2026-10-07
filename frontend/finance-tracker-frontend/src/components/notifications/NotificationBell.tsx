"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { usePathname, useRouter } from "next/navigation";
import { AlertCircle, Bell, Check, CheckCheck, Info, LoaderCircle, Trash2, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { deleteNotification, fetchNotifications, fetchNotificationUnreadCount, markAllNotificationsRead, markNotificationRead } from "@/lib/api";
import type { Notification } from "@/types/notifications";

type Props = { userId: number; open: boolean; onToggle: () => void; onClose: () => void };
const targets = new Set(["/budgets", "/transactions?tab=recurring", "/transactions?tab=upcoming"]);
const severity = {
  INFO: { label: "Information", icon: Info, style: "bg-[#f5f7fb] text-[#4d5a6d]" },
  WARNING: { label: "Warning", icon: AlertCircle, style: "bg-amber-50 text-amber-800" },
  ERROR: { label: "Processing error", icon: AlertCircle, style: "bg-red-50 text-[#b42318]" },
};

export function NotificationBell({ userId, open, onToggle, onClose }: Props) {
  const pathname = usePathname();
  const router = useRouter();
  const root = useRef<HTMLDivElement>(null);
  const bell = useRef<HTMLButtonElement>(null);
  const close = useRef<HTMLButtonElement>(null);
  const request = useRef<AbortController | null>(null);
  const [count, setCount] = useState<number | null>(null);
  const [items, setItems] = useState<Notification[]>([]);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(false);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState("");
  const [actionError, setActionError] = useState("");
  const [busy, setBusy] = useState(false);

  const refresh = useCallback(async () => {
    request.current?.abort();
    const controller = new AbortController();
    request.current = controller;
    if (open) { setLoading(true); setError(""); setActionError(""); }
    const [unread, list] = await Promise.allSettled([
      fetchNotificationUnreadCount(controller.signal),
      open ? fetchNotifications(0, controller.signal) : Promise.resolve(null),
    ]);
    if (controller.signal.aborted) return;
    setCount(unread.status === "fulfilled" ? unread.value : null);
    if (open) {
      if (list.status === "fulfilled" && list.value) {
        setItems(list.value.notifications); setPage(0); setHasMore(list.value.hasMore);
      } else setError("Unable to load notifications.");
      setLoading(false);
    }
  }, [open]);

  useEffect(() => {
    let timer = window.setTimeout(() => { void refresh(); }, 0);
    function changed() {
      window.clearTimeout(timer);
      timer = window.setTimeout(() => { void refresh(); }, 150);
    }
    window.addEventListener("finance-tracker:notifications-changed", changed);
    return () => {
      window.clearTimeout(timer); request.current?.abort();
      window.removeEventListener("finance-tracker:notifications-changed", changed);
    };
  }, [refresh, pathname, userId]);

  useEffect(() => {
    if (!open) return;
    close.current?.focus();
    function escape(event: KeyboardEvent) {
      if (event.key === "Escape") { onClose(); bell.current?.focus(); }
    }
    function outside(event: PointerEvent) {
      if (!root.current?.contains(event.target as Node)) onClose();
    }
    document.addEventListener("keydown", escape);
    document.addEventListener("pointerdown", outside);
    return () => {
      document.removeEventListener("keydown", escape);
      document.removeEventListener("pointerdown", outside);
    };
  }, [open, onClose]);

  async function act(action: () => Promise<void>) {
    if (busy) return;
    setBusy(true); setActionError("");
    try { await action(); }
    catch { setActionError("Unable to update notifications. Please try again."); }
    finally { setBusy(false); }
  }

  async function updateCount() {
    try { setCount(await fetchNotificationUnreadCount()); }
    catch { setCount(null); }
  }

  async function read(item: Notification, navigate = false) {
    await act(async () => {
      if (!item.read) {
        const updated = await markNotificationRead(item.id);
        setItems((current) => current.map((n) => n.id === item.id ? updated : n));
        await updateCount();
      }
      if (navigate && targets.has(item.targetPath)) {
        onClose(); router.push(item.targetPath);
      }
    });
  }

  async function more() {
    setLoadingMore(true); setActionError("");
    try {
      const next = await fetchNotifications(page + 1);
      setItems((current) => [...current, ...next.notifications.filter((n) => !current.some((i) => i.id === n.id))]);
      setPage(next.page); setHasMore(next.hasMore);
    } catch { setActionError("Unable to load older notifications. Please try again."); }
    finally { setLoadingMore(false); }
  }

  return (
    <div ref={root} className="relative">
      <button ref={bell} type="button" aria-label={count ? `Notifications, ${count} unread` : "Notifications"}
        aria-expanded={open} aria-haspopup="dialog" aria-controls="notification-panel" onClick={onToggle}
        className="relative h-10 w-10 rounded-xl border border-[#ece8ef] bg-white text-[#151515] transition hover:border-[#ff5a1f]">
        <Bell className="mx-auto" size={17} />
        {!!count && <span aria-hidden="true" className="absolute -right-2 -top-2 min-w-5 rounded-full bg-[#ff5a1f] px-1 text-center text-[10px] font-bold leading-5 text-white">{count > 99 ? "99+" : count}</span>}
      </button>
      {open && (
        <section id="notification-panel" role="dialog" aria-labelledby="notification-panel-title"
          className="fixed left-4 right-4 top-36 z-50 max-h-[calc(100dvh-10rem)] overflow-y-auto rounded-2xl border border-[#e4e0e7] bg-white text-[#151515] shadow-xl sm:absolute sm:left-auto sm:right-0 sm:top-[calc(100%+10px)] sm:max-h-[70dvh] sm:w-96">
          <div className="sticky top-0 z-10 border-b border-[#e4e0e7] bg-white p-4">
            <div className="flex items-center justify-between gap-3">
              <h2 id="notification-panel-title" className="font-semibold">Notifications</h2>
              <button ref={close} type="button" aria-label="Close notifications" onClick={() => { onClose(); bell.current?.focus(); }} className="rounded-lg p-1 text-[#74707a] hover:bg-[#f5f7fb]"><X size={18} /></button>
            </div>
            <Button variant="ghost" className="mt-2 h-8 px-0 text-xs" disabled={busy || !count}
              onClick={() => void act(async () => {
                await markAllNotificationsRead(); setItems((current) => current.map((n) => ({ ...n, read: true }))); setCount(0);
              })}><CheckCheck size={14} className="mr-2" />Mark all as read</Button>
          </div>
          {actionError && <p role="alert" className="px-4 py-3 text-sm text-[#b42318]">{actionError}</p>}
          {loading ? <div role="status" className="flex min-h-40 items-center justify-center gap-2 p-4 text-sm text-[#74707a]"><LoaderCircle size={18} className="animate-spin" />Loading notifications…</div>
            : error ? <div role="alert" className="p-6 text-center text-sm"><p>{error}</p><Button variant="ghost" className="mt-2" onClick={() => void refresh()}>Retry</Button></div>
            : items.length === 0 ? <div className="p-6 text-center"><Bell size={22} className="mx-auto text-[#74707a]" /><p className="mt-3 text-sm font-semibold">No notifications yet.</p><p className="mt-2 text-xs leading-5 text-[#74707a]">We&apos;ll let you know about important budget and recurring activity.</p></div>
            : <ul aria-label="Notification history" className="divide-y divide-[#e4e0e7]">
              {items.map((item) => {
                const presentation = severity[item.severity];
                const Icon = presentation.icon;
                return <li key={item.id} className={item.read ? "p-4" : "bg-[#fff7f2] p-4"}>
                  <div className="flex min-w-0 items-start gap-3">
                    <span className={`mt-1 shrink-0 rounded-full p-2 ${presentation.style}`}><Icon size={16} /></span>
                    <div className="min-w-0 flex-1">
                      <div className="mb-1 flex flex-wrap items-center gap-2 text-[11px] text-[#74707a]">
                        <span>{presentation.label}</span><span>·</span><span>{item.read ? "Read" : "Unread"}</span>
                      </div>
                      <button type="button" disabled={busy} onClick={() => void read(item, true)} className="block w-full text-left disabled:cursor-wait">
                        <span className={`block break-words text-sm ${item.read ? "font-medium" : "font-semibold"}`}>{item.title}</span>
                        <span className="mt-1 block break-words text-sm leading-5 text-[#46404b] [overflow-wrap:anywhere]">{item.message}</span>
                      </button>
                      <time dateTime={item.createdAt} className="mt-2 block text-xs text-[#74707a]">{new Intl.DateTimeFormat("en-US", { month: "short", day: "numeric", hour: "numeric", minute: "2-digit", year: "numeric" }).format(new Date(item.createdAt))}</time>
                      <div className="mt-2 flex flex-wrap gap-3">
                        {!item.read && <button type="button" disabled={busy} aria-label={`Mark ${item.title} as read`} className="inline-flex items-center gap-1 text-xs text-[#195b4d] disabled:opacity-50" onClick={() => void read(item)}><Check size={13} />Mark read</button>}
                        <button type="button" disabled={busy} aria-label={`Delete ${item.title}`} className="inline-flex items-center gap-1 text-xs text-[#74707a] disabled:opacity-50" onClick={() => void act(async () => {
                          await deleteNotification(item.id); setItems((current) => current.filter((n) => n.id !== item.id)); await updateCount();
                        })}><Trash2 size={13} />Delete</button>
                      </div>
                    </div>
                  </div>
                </li>;
              })}
            </ul>}
          {!loading && !error && hasMore && <div className="border-t border-[#e4e0e7] p-3 text-center"><Button variant="ghost" disabled={loadingMore || busy} onClick={() => void more()}>{loadingMore ? "Loading…" : "Load older notifications"}</Button></div>}
        </section>
      )}
    </div>
  );
}
