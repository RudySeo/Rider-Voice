import { describe, expect, it, vi } from "vitest";
import { collectChannelData } from "./youtube";

const channelId = "UC_x5XG1OV2P6uZZ5FSM9Ttw";

const channelResponse = {
  items: [
    {
      id: channelId,
      snippet: {
        title: "Google for Developers",
        description: "Developer videos",
        thumbnails: {
          high: {
            url: "https://img.youtube.com/channel/high.jpg",
          },
        },
      },
      statistics: {
        subscriberCount: "1000",
        viewCount: "500000",
        videoCount: "120",
      },
      contentDetails: {
        relatedPlaylists: {
          uploads: "UU_x5XG1OV2P6uZZ5FSM9Ttw",
        },
      },
    },
  ],
};

const playlistResponse = {
  items: [
    {
      contentDetails: {
        videoId: "video-a",
      },
    },
    {
      contentDetails: {
        videoId: "video-b",
      },
    },
  ],
};

const videosResponse = {
  items: [
    {
      id: "video-b",
      snippet: {
        title: "Second video",
        description: "Second description",
        publishedAt: "2026-05-07T00:00:00Z",
        thumbnails: {
          medium: {
            url: "https://img.youtube.com/vi/video-b/medium.jpg",
          },
        },
      },
      statistics: {
        viewCount: "200",
        likeCount: "20",
        commentCount: "2",
      },
      contentDetails: {
        duration: "PT8M",
      },
    },
    {
      id: "video-a",
      snippet: {
        title: "First video",
        description: "First description",
        publishedAt: "2026-05-08T00:00:00Z",
        thumbnails: {
          default: {
            url: "https://img.youtube.com/vi/video-a/default.jpg",
          },
        },
      },
      statistics: {
        viewCount: "100",
        likeCount: "10",
      },
      contentDetails: {
        duration: "PT10M",
      },
    },
  ],
};

describe("collectChannelData", () => {
  it("collects channel, playlist, and video data for a handle input", async () => {
    const { fetchImpl, requests } = mockYouTubeFetch([
      channelResponse,
      playlistResponse,
      videosResponse,
    ]);

    const result = await collectChannelData("https://youtube.com/@googledevs", {
      apiKey: "test-api-key",
      fetchImpl,
    });

    expect(result.ok).toBe(true);

    if (!result.ok) {
      return;
    }

    expect(requests).toHaveLength(3);
    expect(requests[0].pathname).toBe("/youtube/v3/channels");
    expect(requests[0].searchParams.get("forHandle")).toBe("googledevs");
    expect(requests[0].searchParams.get("key")).toBe("test-api-key");
    expect(requests[1].pathname).toBe("/youtube/v3/playlistItems");
    expect(requests[1].searchParams.get("playlistId")).toBe("UU_x5XG1OV2P6uZZ5FSM9Ttw");
    expect(requests[1].searchParams.get("maxResults")).toBe("50");
    expect(requests[2].pathname).toBe("/youtube/v3/videos");
    expect(requests[2].searchParams.get("id")).toBe("video-a,video-b");

    expect(result.data.channel).toEqual({
      id: channelId,
      title: "Google for Developers",
      description: "Developer videos",
      thumbnailUrl: "https://img.youtube.com/channel/high.jpg",
      subscriberCount: 1000,
      viewCount: 500000,
      videoCount: 120,
    });
    expect(result.data.videos.map((video) => video.id)).toEqual(["video-a", "video-b"]);
    expect(result.data.videos[0]).toMatchObject({
      viewCount: 100,
      likeCount: 10,
      commentCount: null,
      duration: "PT10M",
    });
    expect(result.data.sampleSize).toBe(2);
  });

  it("uses channel id lookup for direct channel id input", async () => {
    const { fetchImpl, requests } = mockYouTubeFetch([
      channelResponse,
      playlistResponse,
      videosResponse,
    ]);

    const result = await collectChannelData(channelId, {
      apiKey: "test-api-key",
      fetchImpl,
    });

    expect(result.ok).toBe(true);
    expect(requests[0].searchParams.get("id")).toBe(channelId);
    expect(requests[0].searchParams.has("forHandle")).toBe(false);
  });

  it("returns app errors for invalid input and missing api key", async () => {
    await expect(collectChannelData("https://youtube.com/watch?v=abc", { apiKey: "" }))
      .resolves.toMatchObject({
        ok: false,
        error: {
          error: {
            code: "INVALID_CHANNEL_URL",
          },
          status: 400,
        },
      });

    await expect(collectChannelData("@creator", { apiKey: "" })).resolves.toMatchObject({
      ok: false,
      error: {
        error: {
          code: "MISSING_YOUTUBE_API_KEY",
        },
        status: 500,
      },
    });
  });

  it("maps empty channel and empty playlist responses to app errors", async () => {
    await expect(
      collectChannelData("@missing", {
        apiKey: "test-api-key",
        fetchImpl: mockYouTubeFetch([{ items: [] }]).fetchImpl,
      }),
    ).resolves.toMatchObject({
      ok: false,
      error: {
        error: {
          code: "CHANNEL_NOT_FOUND",
        },
        status: 404,
      },
    });

    await expect(
      collectChannelData("@empty", {
        apiKey: "test-api-key",
        fetchImpl: mockYouTubeFetch([channelResponse, { items: [] }]).fetchImpl,
      }),
    ).resolves.toMatchObject({
      ok: false,
      error: {
        error: {
          code: "NO_PUBLIC_VIDEOS",
        },
        status: 404,
      },
    });
  });

  it("maps quota failures and provider failures without exposing raw provider errors", async () => {
    await expect(
      collectChannelData("@quota", {
        apiKey: "test-api-key",
        fetchImpl: mockYouTubeFetch([
          {
            error: {
              errors: [
                {
                  reason: "quotaExceeded",
                },
              ],
            },
          },
        ], [403]).fetchImpl,
      }),
    ).resolves.toMatchObject({
      ok: false,
      error: {
        error: {
          code: "YOUTUBE_RATE_LIMITED",
          message: "YouTube 요청 한도에 도달했습니다. 잠시 후 다시 시도해 주세요.",
        },
        status: 429,
      },
    });

    await expect(
      collectChannelData("@broken", {
        apiKey: "test-api-key",
        fetchImpl: mockYouTubeFetch([{ error: { code: 500 } }], [500]).fetchImpl,
      }),
    ).resolves.toMatchObject({
      ok: false,
      error: {
        error: {
          code: "YOUTUBE_PROVIDER_ERROR",
          message: "YouTube 데이터를 불러오지 못했습니다.",
        },
        status: 502,
      },
    });
  });
});

function mockYouTubeFetch(bodies: unknown[], statuses: number[] = []) {
  const requests: URL[] = [];
  const fetchImpl = vi.fn(async (input: RequestInfo | URL) => {
    requests.push(new URL(input.toString()));
    const responseIndex = requests.length - 1;
    const status = statuses[responseIndex] ?? 200;

    return new Response(JSON.stringify(bodies[responseIndex] ?? {}), {
      status,
      headers: {
        "content-type": "application/json",
      },
    });
  }) as unknown as typeof fetch;

  return {
    fetchImpl,
    requests,
  };
}
