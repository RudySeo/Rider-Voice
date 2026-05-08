import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { ChannelAnalysis } from "@/types/analysis";
import type { YouTubeCollectResult } from "@/types/youtube";
import { AnalysisShell } from "./AnalysisShell";

const collectPayload: YouTubeCollectResult = {
  channel: {
    id: "UC_x5XG1OV2P6uZZ5FSM9Ttw",
    title: "Google for Developers",
    description: "Developer videos",
    thumbnailUrl: null,
    subscriberCount: 1000,
    viewCount: 500000,
    videoCount: 120,
  },
  videos: [
    {
      id: "video-a",
      title: "First video",
      description: "First description",
      publishedAt: "2026-05-08T00:00:00Z",
      thumbnailUrl: null,
      viewCount: 100,
      likeCount: 10,
      commentCount: 1,
      duration: "PT10M",
    },
  ],
  sampleSize: 1,
  collectedAt: "2026-05-08T00:00:00Z",
};

const analysisPayload: ChannelAnalysis = {
  overallScore: 82,
  executiveSummary: ["요약 1", "요약 2", "요약 3"],
  strongSignals: ["강한 성과 신호"],
  growthBottlenecks: ["성장 병목"],
  contentPatterns: ["콘텐츠 패턴"],
  recommendedNextVideos: [
    {
      titleDirection: "추천 제목 방향",
      format: "튜토리얼",
      whyItFits: "상위 영상과 맞습니다.",
      evidence: "기존 영상 성과",
      expectedImpact: "조회수 개선",
      difficulty: "low",
    },
  ],
  actionChecklist: [
    {
      priority: "high",
      task: "반복할 패턴 정리",
      reason: "성과 근거가 있습니다.",
      expectedImpact: "기획 속도 개선",
    },
  ],
  confidence: {
    level: "medium",
    reason: "표본이 작습니다.",
  },
};

describe("AnalysisShell", () => {
  beforeEach(() => {
    vi.unstubAllGlobals();
  });

  it("renders the initial channel input form", () => {
    render(<AnalysisShell />);

    expect(screen.getByRole("heading", { name: /채널 URL을 넣고/i })).toBeInTheDocument();
    expect(screen.getByLabelText("YouTube 채널 URL")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /분석 시작/i })).toBeInTheDocument();
    expect(screen.getByText(/youtube.com\/@handle/i)).toBeInTheDocument();
  });

  it("does not call fetch for invalid URL submissions", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    const user = userEvent.setup();

    render(<AnalysisShell />);

    await user.type(screen.getByLabelText("YouTube 채널 URL"), "https://youtube.com/watch?v=abc");
    await user.click(screen.getByRole("button", { name: /분석 시작/i }));

    expect(fetchMock).not.toHaveBeenCalled();
    expect(await screen.findByRole("alert")).toHaveTextContent("URL 형식을 확인해 주세요");
  });

  it("calls collect and analyze APIs in order for valid submissions", async () => {
    const fetchMock = mockFetchSequence([
      jsonResponse(collectPayload),
      jsonResponse(analysisPayload),
    ]);
    vi.stubGlobal("fetch", fetchMock);
    const user = userEvent.setup();

    render(<AnalysisShell />);

    await user.type(screen.getByLabelText("YouTube 채널 URL"), "@googledevs");
    await user.click(screen.getByRole("button", { name: /분석 시작/i }));

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledTimes(2);
    });

    expect(fetchMock.mock.calls[0][0]).toBe("/api/channel/collect");
    expect(fetchMock.mock.calls[1][0]).toBe("/api/channel/analyze");
    expect(await screen.findByRole("heading", { name: "Google for Developers" })).toBeInTheDocument();
    expect(screen.getByText("전체 점수")).toBeInTheDocument();
    expect(screen.getByText("82")).toBeInTheDocument();
    expect(screen.getByText("다음 영상 아이디어")).toBeInTheDocument();
  });

  it("does not call analyze when collect fails", async () => {
    const fetchMock = mockFetchSequence([
      jsonResponse({
        error: {
          code: "CHANNEL_NOT_FOUND",
          message: "공개 YouTube 채널을 찾지 못했습니다.",
        },
      }, 404),
    ]);
    vi.stubGlobal("fetch", fetchMock);
    const user = userEvent.setup();

    render(<AnalysisShell />);

    await user.type(screen.getByLabelText("YouTube 채널 URL"), "@missing");
    await user.click(screen.getByRole("button", { name: /분석 시작/i }));

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledTimes(1);
    });

    expect(await screen.findByRole("alert")).toHaveTextContent("채널 데이터를 읽지 못했습니다");
  });

  it("keeps collected data visible when analyze fails", async () => {
    const fetchMock = mockFetchSequence([
      jsonResponse(collectPayload),
      jsonResponse({
        error: {
          code: "OPENAI_PROVIDER_ERROR",
          message: "AI 분석 요청을 처리하지 못했습니다.",
        },
      }, 502),
    ]);
    vi.stubGlobal("fetch", fetchMock);
    const user = userEvent.setup();

    render(<AnalysisShell />);

    await user.type(screen.getByLabelText("YouTube 채널 URL"), "@googledevs");
    await user.click(screen.getByRole("button", { name: /분석 시작/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent("AI 분석을 완료하지 못했습니다");
    expect(screen.getByText("수집된 데이터는 유지했습니다")).toBeInTheDocument();
    expect(screen.getByText(/Google for Developers 채널의 최근 공개 영상 1개/)).toBeInTheDocument();
  });
});

function mockFetchSequence(responses: Response[]) {
  return vi.fn(async () => {
    const response = responses.shift();

    if (!response) {
      throw new Error("Unexpected fetch call");
    }

    return response;
  }) as unknown as ReturnType<typeof vi.fn<typeof fetch>>;
}

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json",
    },
  });
}
