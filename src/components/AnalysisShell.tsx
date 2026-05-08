"use client";

import { useState } from "react";
import { parseChannelInput } from "@/lib/channel-url";
import type { ApiErrorResponse } from "@/types/api";
import type { ChannelAnalysis } from "@/types/analysis";
import type { YouTubeCollectResult } from "@/types/youtube";
import { ChannelInput } from "./ChannelInput";
import { ErrorPanel } from "./ErrorPanel";
import { ProgressPanel } from "./ProgressPanel";

export type FlowStatus =
  | "idle"
  | "validating"
  | "collecting"
  | "analyzing"
  | "complete"
  | "validation_failed"
  | "collection_failed"
  | "analysis_failed";

interface FlowError {
  title: string;
  message: string;
  action: string;
}

export function AnalysisShell() {
  const [channelUrl, setChannelUrl] = useState("");
  const [status, setStatus] = useState<FlowStatus>("idle");
  const [error, setError] = useState<FlowError | null>(null);
  const [collectResult, setCollectResult] = useState<YouTubeCollectResult | null>(null);
  const [analysisResult, setAnalysisResult] = useState<ChannelAnalysis | null>(null);

  const isBusy = status === "validating" || status === "collecting" || status === "analyzing";

  async function runAnalysis() {
    setStatus("validating");
    setError(null);
    setAnalysisResult(null);

    const parsedInput = parseChannelInput(channelUrl);

    if (!parsedInput.ok) {
      setStatus("validation_failed");
      setError({
        title: "URL 형식을 확인해 주세요",
        message: parsedInput.error.message,
        action: "@handle 또는 youtube.com/channel/UC... 형식으로 다시 입력해 주세요.",
      });
      return;
    }

    setStatus("collecting");

    const collectResponse = await fetch("/api/channel/collect", {
      method: "POST",
      headers: {
        "content-type": "application/json",
      },
      body: JSON.stringify({ channelUrl }),
    });

    const collectPayload = (await collectResponse.json()) as
      | YouTubeCollectResult
      | ApiErrorResponse;

    if (!collectResponse.ok || isApiError(collectPayload)) {
      setStatus("collection_failed");
      setCollectResult(null);
      setError(toFlowError("collection_failed", collectPayload));
      return;
    }

    setCollectResult(collectPayload);
    setStatus("analyzing");

    const analyzeResponse = await fetch("/api/channel/analyze", {
      method: "POST",
      headers: {
        "content-type": "application/json",
      },
      body: JSON.stringify(collectPayload),
    });

    const analyzePayload = (await analyzeResponse.json()) as ChannelAnalysis | ApiErrorResponse;

    if (!analyzeResponse.ok || isApiError(analyzePayload)) {
      setStatus("analysis_failed");
      setError(toFlowError("analysis_failed", analyzePayload));
      return;
    }

    setAnalysisResult(analyzePayload);
    setStatus("complete");
  }

  return (
    <main className="min-h-screen bg-slate-100 px-5 py-8 text-slate-950 sm:px-8">
      <section className="mx-auto flex w-full max-w-6xl flex-col gap-6">
        <div className="grid gap-6 border border-slate-200 bg-white p-6 shadow-sm lg:grid-cols-[1fr_320px] lg:p-8">
          <div>
            <p className="text-sm font-semibold uppercase text-cyan-700">
              YouTube Channel Insight
            </p>
            <h1 className="mt-3 max-w-3xl text-3xl font-semibold leading-tight sm:text-4xl">
              채널 URL을 넣고 다음 콘텐츠 결정을 바로 시작하세요
            </h1>
            <p className="mt-4 max-w-2xl text-sm leading-6 text-slate-600">
              지원 형식: youtube.com/@handle, @handle, youtube.com/channel/UC...
            </p>
          </div>
          <div className="border border-slate-200 bg-slate-50 p-4">
            <p className="text-sm font-semibold text-slate-900">핵심 질문</p>
            <p className="mt-2 text-sm leading-6 text-slate-600">
              지금 무엇이 잘 되고 있고, 다음 영상은 무엇을 만들면 좋은가?
            </p>
          </div>
          <div className="lg:col-span-2">
            <ChannelInput
              value={channelUrl}
              disabled={isBusy}
              onChange={setChannelUrl}
              onSubmit={runAnalysis}
            />
          </div>
        </div>

        <ProgressPanel status={status} />

        {error ? (
          <ErrorPanel title={error.title} message={error.message} action={error.action} />
        ) : null}

        {status === "analysis_failed" && collectResult ? (
          <section className="border border-amber-200 bg-amber-50 p-5 text-amber-950">
            <p className="text-sm font-semibold">수집된 데이터는 유지했습니다</p>
            <p className="mt-2 text-sm">
              {collectResult.channel.title} 채널의 최근 공개 영상 {collectResult.sampleSize}개를
              다시 분석할 수 있습니다.
            </p>
          </section>
        ) : null}

        {status === "complete" && collectResult && analysisResult ? (
          <section className="border border-slate-200 bg-white p-6 shadow-sm">
            <p className="text-sm font-semibold text-cyan-700">분석 결과 준비 완료</p>
            <h2 className="mt-2 text-2xl font-semibold">{collectResult.channel.title}</h2>
            <p className="mt-3 text-sm text-slate-600">
              전체 진단 점수 {analysisResult.overallScore}점. 상세 대시보드는 다음
              단계에서 이 영역에 연결됩니다.
            </p>
          </section>
        ) : null}
      </section>
    </main>
  );
}

function isApiError(payload: unknown): payload is ApiErrorResponse {
  return Boolean(payload && typeof payload === "object" && "error" in payload);
}

function toFlowError(status: "collection_failed" | "analysis_failed", payload: unknown): FlowError {
  const message = isApiError(payload)
    ? payload.error.message
    : "요청을 처리하지 못했습니다.";

  if (status === "collection_failed") {
    return {
      title: "채널 데이터를 읽지 못했습니다",
      message,
      action: "채널이 공개 상태인지 확인한 뒤 다시 시도해 주세요.",
    };
  }

  return {
    title: "AI 분석을 완료하지 못했습니다",
    message,
    action: "수집 데이터는 유지했으니 잠시 후 다시 실행해 주세요.",
  };
}
