import "server-only";

import { toErrorResponse, type AppErrorResponse } from "@/lib/app-errors";
import { parseChannelInput } from "@/lib/channel-url";
import type { YouTubeChannel, YouTubeCollectResult, YouTubeVideo } from "@/types/youtube";

const YOUTUBE_API_BASE_URL = "https://www.googleapis.com/youtube/v3";
const MAX_RECENT_VIDEOS = 50;

export type CollectChannelDataResult =
  | {
      ok: true;
      data: YouTubeCollectResult;
    }
  | {
      ok: false;
      error: AppErrorResponse;
    };

interface CollectChannelDataOptions {
  apiKey?: string;
  fetchImpl?: typeof fetch;
}

interface YouTubeErrorBody {
  error?: {
    code?: number;
    errors?: Array<{
      reason?: string;
    }>;
  };
}

interface YouTubeChannelListResponse extends YouTubeErrorBody {
  items?: YouTubeChannelItem[];
}

interface YouTubePlaylistItemsResponse extends YouTubeErrorBody {
  items?: YouTubePlaylistItem[];
}

interface YouTubeVideosResponse extends YouTubeErrorBody {
  items?: YouTubeVideoItem[];
}

interface YouTubeChannelItem {
  id?: string;
  snippet?: {
    title?: string;
    description?: string;
    thumbnails?: YouTubeThumbnails;
  };
  statistics?: {
    subscriberCount?: string;
    hiddenSubscriberCount?: boolean;
    viewCount?: string;
    videoCount?: string;
  };
  contentDetails?: {
    relatedPlaylists?: {
      uploads?: string;
    };
  };
}

interface YouTubePlaylistItem {
  contentDetails?: {
    videoId?: string;
  };
}

interface YouTubeVideoItem {
  id?: string;
  snippet?: {
    title?: string;
    description?: string;
    publishedAt?: string;
    thumbnails?: YouTubeThumbnails;
  };
  statistics?: {
    viewCount?: string;
    likeCount?: string;
    commentCount?: string;
  };
  contentDetails?: {
    duration?: string;
  };
}

type YouTubeThumbnails = Record<string, { url?: string } | undefined>;

type YouTubeFetchResult<T> =
  | {
      ok: true;
      data: T;
    }
  | {
      ok: false;
      error: AppErrorResponse;
    };

export async function collectChannelData(
  input: string,
  options: CollectChannelDataOptions = {},
): Promise<CollectChannelDataResult> {
  const parsedInput = parseChannelInput(input);

  if (!parsedInput.ok) {
    return {
      ok: false,
      error: toErrorResponse(parsedInput.error.code, parsedInput.error.message),
    };
  }

  const apiKey = options.apiKey ?? process.env.YOUTUBE_API_KEY;

  if (!apiKey) {
    return {
      ok: false,
      error: toErrorResponse("MISSING_YOUTUBE_API_KEY"),
    };
  }

  const fetchImpl = options.fetchImpl ?? fetch;
  const channelResult = await fetchYouTube<YouTubeChannelListResponse>(
    fetchImpl,
    "channels",
    {
      part: "snippet,statistics,contentDetails",
      ...(parsedInput.identifier.type === "handle"
        ? { forHandle: parsedInput.identifier.value }
        : { id: parsedInput.identifier.value }),
    },
    apiKey,
  );

  if (!channelResult.ok) {
    return channelResult;
  }

  const channelItem = channelResult.data.items?.[0];
  const uploadsPlaylistId = channelItem?.contentDetails?.relatedPlaylists?.uploads;

  if (!channelItem || !uploadsPlaylistId) {
    return {
      ok: false,
      error: toErrorResponse("CHANNEL_NOT_FOUND"),
    };
  }

  const playlistResult = await fetchYouTube<YouTubePlaylistItemsResponse>(
    fetchImpl,
    "playlistItems",
    {
      part: "contentDetails",
      playlistId: uploadsPlaylistId,
      maxResults: String(MAX_RECENT_VIDEOS),
    },
    apiKey,
  );

  if (!playlistResult.ok) {
    return playlistResult;
  }

  const videoIds = (playlistResult.data.items ?? [])
    .map((item) => item.contentDetails?.videoId)
    .filter((videoId): videoId is string => Boolean(videoId));

  if (videoIds.length === 0) {
    return {
      ok: false,
      error: toErrorResponse("NO_PUBLIC_VIDEOS"),
    };
  }

  const videosResult = await fetchYouTube<YouTubeVideosResponse>(
    fetchImpl,
    "videos",
    {
      part: "snippet,statistics,contentDetails",
      id: videoIds.join(","),
    },
    apiKey,
  );

  if (!videosResult.ok) {
    return videosResult;
  }

  const videos = normalizeVideos(videosResult.data.items ?? [], videoIds);

  if (videos.length === 0) {
    return {
      ok: false,
      error: toErrorResponse("NO_PUBLIC_VIDEOS"),
    };
  }

  return {
    ok: true,
    data: {
      channel: normalizeChannel(channelItem),
      videos,
      sampleSize: videos.length,
      collectedAt: new Date().toISOString(),
    },
  };
}

async function fetchYouTube<T extends YouTubeErrorBody>(
  fetchImpl: typeof fetch,
  resource: string,
  params: Record<string, string>,
  apiKey: string,
): Promise<YouTubeFetchResult<T>> {
  const url = new URL(`${YOUTUBE_API_BASE_URL}/${resource}`);

  for (const [key, value] of Object.entries(params)) {
    url.searchParams.set(key, value);
  }

  url.searchParams.set("key", apiKey);

  try {
    const response = await fetchImpl(url.toString());
    const body = (await response.json().catch(() => ({}))) as T;

    if (!response.ok) {
      return {
        ok: false,
        error: mapYouTubeProviderError(response.status, body),
      };
    }

    return {
      ok: true,
      data: body,
    };
  } catch {
    return {
      ok: false,
      error: toErrorResponse("YOUTUBE_PROVIDER_ERROR"),
    };
  }
}

function mapYouTubeProviderError(status: number, body: YouTubeErrorBody): AppErrorResponse {
  const reasons = body.error?.errors?.map((item) => item.reason ?? "") ?? [];
  const isRateLimited =
    status === 429 ||
    reasons.some((reason) =>
      ["quotaExceeded", "rateLimitExceeded", "userRateLimitExceeded"].includes(reason),
    );

  if (isRateLimited) {
    return toErrorResponse("YOUTUBE_RATE_LIMITED");
  }

  return toErrorResponse("YOUTUBE_PROVIDER_ERROR");
}

function normalizeChannel(item: YouTubeChannelItem): YouTubeChannel {
  return {
    id: item.id ?? "",
    title: item.snippet?.title ?? "",
    description: item.snippet?.description ?? "",
    thumbnailUrl: getBestThumbnailUrl(item.snippet?.thumbnails),
    subscriberCount: item.statistics?.hiddenSubscriberCount
      ? null
      : parseNullableInteger(item.statistics?.subscriberCount),
    viewCount: parseNullableInteger(item.statistics?.viewCount),
    videoCount: parseNullableInteger(item.statistics?.videoCount),
  };
}

function normalizeVideos(items: YouTubeVideoItem[], requestedOrder: string[]): YouTubeVideo[] {
  const videosById = new Map(
    items
      .filter((item): item is YouTubeVideoItem & { id: string } => Boolean(item.id))
      .map((item) => [item.id, normalizeVideo(item)]),
  );

  return requestedOrder
    .map((videoId) => videosById.get(videoId))
    .filter((video): video is YouTubeVideo => Boolean(video));
}

function normalizeVideo(item: YouTubeVideoItem & { id: string }): YouTubeVideo {
  return {
    id: item.id,
    title: item.snippet?.title ?? "",
    description: item.snippet?.description ?? "",
    publishedAt: item.snippet?.publishedAt ?? "",
    thumbnailUrl: getBestThumbnailUrl(item.snippet?.thumbnails),
    viewCount: parseNullableInteger(item.statistics?.viewCount),
    likeCount: parseNullableInteger(item.statistics?.likeCount),
    commentCount: parseNullableInteger(item.statistics?.commentCount),
    duration: item.contentDetails?.duration ?? "",
  };
}

function parseNullableInteger(value: string | undefined): number | null {
  if (value === undefined) {
    return null;
  }

  const parsed = Number(value);

  return Number.isSafeInteger(parsed) && parsed >= 0 ? parsed : null;
}

function getBestThumbnailUrl(thumbnails: YouTubeThumbnails | undefined): string | null {
  if (!thumbnails) {
    return null;
  }

  for (const key of ["maxres", "standard", "high", "medium", "default"]) {
    const thumbnailUrl = thumbnails[key]?.url;

    if (thumbnailUrl) {
      return thumbnailUrl;
    }
  }

  return null;
}
