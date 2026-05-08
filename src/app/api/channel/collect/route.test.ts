import { beforeEach, describe, expect, it, vi } from "vitest";
import { toErrorResponse } from "@/lib/app-errors";
import type { YouTubeCollectResult } from "@/types/youtube";
import { collectChannelData } from "@/services/youtube";
import { POST } from "./route";

vi.mock("@/services/youtube", () => ({
  collectChannelData: vi.fn(),
}));

const collectChannelDataMock = vi.mocked(collectChannelData);

const collectPayload: YouTubeCollectResult = {
  channel: {
    id: "UC_x5XG1OV2P6uZZ5FSM9Ttw",
    title: "Google for Developers",
    description: "Developer videos",
    thumbnailUrl: null,
    subscriberCount: null,
    viewCount: 1000,
    videoCount: null,
  },
  videos: [
    {
      id: "video-id",
      title: "Video title",
      description: "Video description",
      publishedAt: "2026-05-08T00:00:00Z",
      thumbnailUrl: null,
      viewCount: 100,
      likeCount: null,
      commentCount: null,
      duration: "PT10M",
    },
  ],
  sampleSize: 1,
  collectedAt: "2026-05-08T00:00:00Z",
};

describe("POST /api/channel/collect", () => {
  beforeEach(() => {
    collectChannelDataMock.mockReset();
  });

  it("validates request body and returns collected data", async () => {
    collectChannelDataMock.mockResolvedValue({
      ok: true,
      data: collectPayload,
    });

    const response = await POST(jsonRequest({ channelUrl: "@googledevs" }));

    await expect(response.json()).resolves.toEqual(collectPayload);
    expect(response.status).toBe(200);
    expect(collectChannelDataMock).toHaveBeenCalledWith("@googledevs");
  });

  it("returns sanitized validation errors without calling the service", async () => {
    const response = await POST(jsonRequest({ channelUrl: "" }));

    await expect(response.json()).resolves.toEqual({
      error: {
        code: "INVALID_CHANNEL_URL",
        message: "지원하지 않는 YouTube URL 형식입니다.",
      },
    });
    expect(response.status).toBe(400);
    expect(collectChannelDataMock).not.toHaveBeenCalled();
  });

  it("maps service errors to sanitized JSON responses", async () => {
    collectChannelDataMock.mockResolvedValue({
      ok: false,
      error: toErrorResponse("YOUTUBE_PROVIDER_ERROR", "safe message", 502),
    });

    const response = await POST(jsonRequest({ channelUrl: "@googledevs" }));

    await expect(response.json()).resolves.toEqual({
      error: {
        code: "YOUTUBE_PROVIDER_ERROR",
        message: "safe message",
      },
    });
    expect(response.status).toBe(502);
  });

  it("handles malformed JSON as an invalid channel request", async () => {
    const response = await POST(
      new Request("http://localhost/api/channel/collect", {
        method: "POST",
        body: "{",
      }),
    );

    await expect(response.json()).resolves.toMatchObject({
      error: {
        code: "INVALID_CHANNEL_URL",
      },
    });
    expect(response.status).toBe(400);
  });
});

function jsonRequest(body: unknown) {
  return new Request("http://localhost/api/channel/collect", {
    method: "POST",
    body: JSON.stringify(body),
    headers: {
      "content-type": "application/json",
    },
  });
}
