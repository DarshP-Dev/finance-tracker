"use client";

import { useEffect, useState } from "react";
import { Info, RefreshCw, Sparkles } from "lucide-react";
import { getFinancialInsightsSummary } from "@/lib/api";
import type { FinancialInsightsSummaryResponse } from "@/types/financial-insights";

export function FinancialInsightsSummary() {
  const [response, setResponse] = useState<FinancialInsightsSummaryResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [retry, setRetry] = useState(0);
  const [wait, setWait] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    const timer = window.setTimeout(() => {
      void getFinancialInsightsSummary(controller.signal).then((data) => {
        if (controller.signal.aborted) return;
        setResponse(data);
        setWait(Math.max(0, data.retryAfterSeconds));
        setFailed(false);
        setLoading(false);
      }).catch(() => {
        if (controller.signal.aborted) return;
        setFailed(true);
        setLoading(false);
        // No automatic retries; leave the deterministic cards usable.
        setWait(30);
      });
    }, 0);
    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [retry]);

  useEffect(() => {
    if (wait <= 0) return;
    const timer = window.setTimeout(() => setWait((remaining) => Math.max(0, remaining - 1)), 1000);
    return () => window.clearTimeout(timer);
  }, [wait]);

  if (!loading && response?.status === "DISABLED") return null;
  const unavailable = failed || response?.status === "UNAVAILABLE" || response?.status === "COOLDOWN";
  const generated = response?.aiGenerated === true;
  const refresh = () => {
    setResponse(null);
    setFailed(false);
    setLoading(true);
    setRetry((value) => value + 1);
  };

  return (
    <aside aria-label="Financial insights summary" aria-busy={loading} aria-live="polite"
      className="mt-5 min-w-0 rounded-xl border border-[#e4e0e7] bg-white p-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h3 className="inline-flex items-center gap-2 text-sm font-semibold text-[#151515]">
          {generated ? <Sparkles size={16} aria-hidden="true" /> : <Info size={16} aria-hidden="true" />}
          {generated ? "AI-generated summary" : "Financial summary"}
        </h3>
        {!loading && response?.status !== "EMPTY" && response?.status !== "DETERMINISTIC" && (
          <button type="button" onClick={refresh} disabled={wait > 0}
            className="inline-flex items-center gap-1.5 rounded-lg px-2 py-1 text-xs font-semibold text-[#195b4d] hover:underline disabled:cursor-default disabled:opacity-50">
            <RefreshCw size={13} aria-hidden="true" />
            {wait > 0 ? `Retry in ${wait}s` : unavailable ? "Retry summary" : "Refresh summary"}
          </button>
        )}
      </div>
      {loading ? (
        <div role="status" className="mt-3 min-h-16">
          <span className="sr-only">Loading financial summary</span>
          <div aria-hidden="true" className="space-y-2 animate-pulse motion-reduce:animate-none">
            <div className="h-3 w-full rounded bg-[#e4e0e7]" />
            <div className="h-3 w-5/6 rounded bg-[#e4e0e7]" />
            <div className="h-3 w-2/3 rounded bg-[#e4e0e7]" />
          </div>
        </div>
      ) : unavailable ? (
        <p className="mt-3 text-sm leading-6 text-[#46404b]">AI summary unavailable. Your calculated financial insights remain available below.</p>
      ) : (
        <>
          <p className="mt-3 whitespace-pre-line break-words text-sm leading-6 text-[#46404b]">{response?.summary}</p>
          {generated && <p className="mt-3 text-xs leading-5 text-[#77717d]">Based on the calculated insights below. AI wording may be imperfect; check the source cards for exact details.</p>}
        </>
      )}
    </aside>
  );
}
