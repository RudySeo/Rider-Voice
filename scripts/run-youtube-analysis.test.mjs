import { mkdtemp, readFile, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import { afterEach, describe, expect, it, vi } from "vitest";
import { runYoutubeAnalysis } from "./run-youtube-analysis.mjs";

const collectPayload = {
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

const analysisPayload = {
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

const tempDirs = [];

afterEach(async () => {
  await Promise.all(tempDirs.map((dir) => rm(dir, { force: true, recursive: true })));
  tempDirs.length = 0;
});

describe("runYoutubeAnalysis", () => {
  it("requires a channel input before making network calls", async () => {
    const outDir = await makeTempDir();
    const fetchImpl = vi.fn();

    const result = await runYoutubeAnalysis({
      argv: ["--out-dir", outDir],
      env: {},
      fetchImpl,
      now: () => new Date("2026-05-08T10:00:00Z"),
      logger: silentLogger(),
    });

    expect(result.exitCode).toBe(1);
    expect(result.status).toBe("invalid_arguments");
    expect(fetchImpl).not.toHaveBeenCalled();
  });

  it("writes JSON and Markdown reports when collection and analysis succeed", async () => {
    const outDir = await makeTempDir();
    const fetchImpl = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(200, collectPayload))
      .mockResolvedValueOnce(jsonResponse(200, analysisPayload));

    const result = await runYoutubeAnalysis({
      argv: ["--channel", "@googledevs", "--base-url", "http://localhost:3000", "--out-dir", outDir],
      env: {},
      fetchImpl,
      now: () => new Date("2026-05-08T10:00:00Z"),
      logger: silentLogger(),
    });

    expect(result).toMatchObject({
      exitCode: 0,
      status: "success",
    });
    expect(fetchImpl).toHaveBeenCalledTimes(2);
    expect(fetchImpl).toHaveBeenNthCalledWith(
      1,
      "http://localhost:3000/api/channel/collect",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ channelUrl: "@googledevs" }),
      }),
    );
    expect(fetchImpl).toHaveBeenNthCalledWith(
      2,
      "http://localhost:3000/api/channel/analyze",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify(collectPayload),
      }),
    );

    const latestJson = JSON.parse(await readFile(path.join(outDir, "latest.json"), "utf8"));
    expect(latestJson).toMatchObject({
      status: "success",
      channelUrl: "@googledevs",
      baseUrl: "http://localhost:3000",
      generatedAt: "2026-05-08T10:00:00.000Z",
      collect: collectPayload,
      analysis: analysisPayload,
    });

    const latestMarkdown = await readFile(path.join(outDir, "latest.md"), "utf8");
    expect(latestMarkdown).toContain("# YouTube Channel Analysis");
    expect(latestMarkdown).toContain("Google for Developers");
    expect(latestMarkdown).toContain("Score: 82/100");
    expect(latestMarkdown).toContain("추천 제목 방향");
  });

  it("stops after collection failure and writes a collection_failed result", async () => {
    const outDir = await makeTempDir();
    const fetchImpl = vi.fn().mockResolvedValueOnce(
      jsonResponse(404, {
        error: {
          code: "CHANNEL_NOT_FOUND",
          message: "공개 YouTube 채널을 찾지 못했습니다.",
        },
      }),
    );

    const result = await runYoutubeAnalysis({
      argv: ["--channel", "@missing", "--out-dir", outDir],
      env: {},
      fetchImpl,
      now: () => new Date("2026-05-08T10:00:00Z"),
      logger: silentLogger(),
    });

    expect(result).toMatchObject({
      exitCode: 1,
      status: "collection_failed",
    });
    expect(fetchImpl).toHaveBeenCalledTimes(1);

    const latestJson = JSON.parse(await readFile(path.join(outDir, "latest.json"), "utf8"));
    expect(latestJson).toMatchObject({
      status: "collection_failed",
      error: {
        stage: "collect",
        status: 404,
        code: "CHANNEL_NOT_FOUND",
      },
    });
  });

  it("preserves collected data when analysis fails", async () => {
    const outDir = await makeTempDir();
    const fetchImpl = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(200, collectPayload))
      .mockResolvedValueOnce(
        jsonResponse(502, {
          error: {
            code: "OPENAI_PROVIDER_ERROR",
            message: "AI 분석 요청을 처리하지 못했습니다.",
          },
        }),
      );

    const result = await runYoutubeAnalysis({
      argv: ["--channel", "@googledevs", "--out-dir", outDir],
      env: {},
      fetchImpl,
      now: () => new Date("2026-05-08T10:00:00Z"),
      logger: silentLogger(),
    });

    expect(result).toMatchObject({
      exitCode: 1,
      status: "analysis_failed",
    });
    expect(fetchImpl).toHaveBeenCalledTimes(2);

    const latestJson = JSON.parse(await readFile(path.join(outDir, "latest.json"), "utf8"));
    expect(latestJson).toMatchObject({
      status: "analysis_failed",
      collect: collectPayload,
      error: {
        stage: "analyze",
        status: 502,
        code: "OPENAI_PROVIDER_ERROR",
      },
    });
  });
});

async function makeTempDir() {
  const dir = await mkdtemp(path.join(tmpdir(), "youtube-analysis-"));
  tempDirs.push(dir);
  return dir;
}

function jsonResponse(status, payload) {
  return {
    ok: status >= 200 && status < 300,
    status,
    async json() {
      return payload;
    },
  };
}

function silentLogger() {
  return {
    error: vi.fn(),
    log: vi.fn(),
    warn: vi.fn(),
  };
}
