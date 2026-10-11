"use client";

import { useSyncExternalStore } from "react";

const THEME_STORAGE_KEY = "financeTrackerTheme";
const THEME_CHANGED_EVENT = "finance-tracker:theme-changed";
let inMemoryDarkMode: boolean | undefined;

function getDarkMode() {
  if (inMemoryDarkMode !== undefined) return inMemoryDarkMode;
  try {
    return window.localStorage.getItem(THEME_STORAGE_KEY) === "dark";
  } catch {
    return false;
  }
}

function getServerDarkMode() {
  return false;
}

function subscribe(onChange: () => void) {
  function onStorage(event: StorageEvent) {
    if (event.key === THEME_STORAGE_KEY || event.key === null) {
      inMemoryDarkMode = undefined;
      onChange();
    }
  }

  window.addEventListener(THEME_CHANGED_EVENT, onChange);
  window.addEventListener("storage", onStorage);
  return () => {
    window.removeEventListener(THEME_CHANGED_EVENT, onChange);
    window.removeEventListener("storage", onStorage);
  };
}

function setIsDarkMode(enabled: boolean) {
  inMemoryDarkMode = enabled;
  try {
    window.localStorage.setItem(THEME_STORAGE_KEY, enabled ? "dark" : "light");
  } catch {
    // Keep navigation working when browser storage is unavailable.
  }
  window.dispatchEvent(new Event(THEME_CHANGED_EVENT));
}

/** Page remounts and reloads share the browser's saved display preference. */
export function useDarkMode() {
  const isDarkMode = useSyncExternalStore(subscribe, getDarkMode, getServerDarkMode);
  return { isDarkMode, setIsDarkMode };
}
