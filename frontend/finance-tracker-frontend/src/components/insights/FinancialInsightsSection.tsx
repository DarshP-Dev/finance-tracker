"use client";

import Link from "next/link";
import { ArrowRight, Info, RefreshCw } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { getFinancialInsights } from "@/lib/api";
import { FinancialInsightCard } from "@/components/insights/FinancialInsightCard";
import { FinancialInsightsSummary } from "@/components/insights/FinancialInsightsSummary";
import type { FinancialInsight } from "@/types/financial-insights";

export function FinancialInsightsSection({ preview = false }: { preview?: boolean }) {
  const [insights, setInsights] = useState<FinancialInsight[]>([]);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [retry, setRetry] = useState(0);
  const section = useRef<HTMLElement>(null);

  useEffect(() => {
    const controller = new AbortController();
    // Match existing page loading patterns and avoid duplicate development-mode requests.
    const timer = window.setTimeout(() => {
      void getFinancialInsights(controller.signal).then((response) => {
        if (!controller.signal.aborted) {
          setInsights(response.insights);
          setFailed(false);
          setLoading(false);
        }
      }).catch(() => {
        if (!controller.signal.aborted) {
          setFailed(true);
          setLoading(false);
        }
      });
    }, 0);
    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [retry]);

  useEffect(() => {
    if (preview || window.location.hash !== "#financial-insights") return;
    const frame = window.requestAnimationFrame(() => {
      section.current?.scrollIntoView({ block: "start" });
      section.current?.focus({ preventScroll: true });
    });
    return () => window.cancelAnimationFrame(frame);
  }, [preview]);

  const refresh = () => {
    setLoading(true);
    setFailed(false);
    setRetry((value) => value + 1);
  };
  // The server owns priority and deduplication. Only the preview truncates the list.
  const visibleInsights = preview ? insights.slice(0, 3) : insights;

  return (
    <section ref={section} id="financial-insights" tabIndex={-1} aria-labelledby="financial-insights-heading"
      className="min-w-0 scroll-mt-6 rounded-2xl border border-[#e4e0e7] bg-white p-5 shadow-sm focus-visible:outline-2 focus-visible:outline-[#ff5a1f]">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <h2 id="financial-insights-heading" className={`${preview ? "text-base" : "text-xl"} font-semibold text-[#151515]`}>Financial Insights</h2>
          <p className={`mt-1 ${preview ? "text-xs" : "text-sm"} leading-5 text-[#77717d]`}>
            {preview ? "Highlights from your latest financial activity." : "Based on your latest data. Each insight shows its own period, independent of the analytics filters."}
          </p>
        </div>
        {!preview && <button type="button" onClick={refresh} disabled={loading}
          className="inline-flex items-center gap-1.5 rounded-lg px-2 py-1.5 text-xs font-semibold text-[#195b4d] hover:underline disabled:cursor-default disabled:opacity-50">
          <RefreshCw size={14} aria-hidden="true" /> Refresh insights
        </button>}
      </div>

      {!preview && <FinancialInsightsSummary key={retry} />}

      <div className="mt-5" aria-busy={loading} aria-live="polite">
        {loading ? (
          <div role="status" aria-label="Loading financial insights">
            <span className="sr-only">Loading financial insights</span>
            <div aria-hidden="true" className={`grid gap-3 ${preview ? "md:grid-cols-3" : "md:grid-cols-2"}`}>
              {Array.from({ length: preview ? 3 : 4 }).map((_, index) => (
                <div key={index} className="min-h-40 animate-pulse rounded-xl border border-[#e4e0e7] bg-[#faf9fa] p-4 motion-reduce:animate-none">
                  <div className="h-3 w-20 rounded bg-[#e4e0e7]" />
                  <div className="mt-5 h-4 w-2/3 rounded bg-[#e4e0e7]" />
                  <div className="mt-3 h-3 w-full rounded bg-[#e4e0e7]" />
                  <div className="mt-2 h-3 w-4/5 rounded bg-[#e4e0e7]" />
                </div>
              ))}
            </div>
          </div>
        ) : failed ? (
          <div role="alert" className="rounded-xl border border-[#e4e0e7] px-4 py-5 text-sm text-[#46404b]">
            <p>Unable to load financial insights.</p>
            <button type="button" onClick={refresh} className="mt-3 rounded-xl bg-[#15151b] px-4 py-2 text-sm font-semibold text-white">Retry</button>
          </div>
        ) : visibleInsights.length === 0 ? (
          <div className="rounded-xl border border-[#e4e0e7] px-4 py-6 text-center">
            <Info size={20} aria-hidden="true" className="mx-auto text-[#77717d]" />
            <p className="mt-3 text-sm font-semibold text-[#151515]">No financial insights yet.</p>
            <p className="mt-1 text-sm leading-6 text-[#77717d]">Add more transactions, budgets, or recurring activity to generate insights.</p>
          </div>
        ) : (
          <ul className={`grid gap-3 ${preview ? "md:grid-cols-3" : "md:grid-cols-2"}`}>
            {visibleInsights.map((insight) => <li key={insight.key} className="min-w-0"><FinancialInsightCard insight={insight} compact={preview} /></li>)}
          </ul>
        )}
      </div>
      {preview && <Link href="/analytics#financial-insights" className="mt-4 inline-flex items-center gap-1.5 rounded-lg py-1 text-sm font-semibold text-[#195b4d] hover:underline focus-visible:outline-2 focus-visible:outline-[#ff5a1f]">
        View all insights <ArrowRight size={15} aria-hidden="true" />
      </Link>}
    </section>
  );
}
