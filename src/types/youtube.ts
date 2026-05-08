export type ChannelIdentifierType = "handle" | "channelId";

export interface ChannelIdentifier {
  type: ChannelIdentifierType;
  value: string;
}

export interface ParseChannelInputSuccess {
  ok: true;
  identifier: ChannelIdentifier;
}

export interface ParseChannelInputFailure {
  ok: false;
  error: {
    code: "INVALID_CHANNEL_URL";
    message: string;
  };
}

export type ParseChannelInputResult =
  | ParseChannelInputSuccess
  | ParseChannelInputFailure;

export interface YouTubeChannel {
  id: string;
  title: string;
  description: string;
  thumbnailUrl: string | null;
  subscriberCount: number | null;
  viewCount: number | null;
  videoCount: number | null;
}

export interface YouTubeVideo {
  id: string;
  title: string;
  description: string;
  publishedAt: string;
  thumbnailUrl: string | null;
  viewCount: number | null;
  likeCount: number | null;
  commentCount: number | null;
  duration: string;
}

export interface YouTubeCollectResult {
  channel: YouTubeChannel;
  videos: YouTubeVideo[];
  sampleSize: number;
  collectedAt: string;
}
