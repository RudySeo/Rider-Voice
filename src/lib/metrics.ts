import type { YouTubeVideo } from "@/types/youtube";

const OUTLIER_MULTIPLIER = 1.5;
const MS_PER_DAY = 24 * 60 * 60 * 1_000;

export function getAverageViewCount(videos: YouTubeVideo[]): number | null {
  const viewCounts = videos
    .map((video) => video.viewCount)
    .filter((viewCount): viewCount is number => viewCount !== null);

  if (viewCounts.length === 0) {
    return null;
  }

  return viewCounts.reduce((total, viewCount) => total + viewCount, 0) / viewCounts.length;
}

export function getEngagementRate(video: YouTubeVideo): number | null {
  const { viewCount, likeCount, commentCount } = video;

  if (
    viewCount === null ||
    viewCount <= 0 ||
    likeCount === null ||
    commentCount === null
  ) {
    return null;
  }

  return ((likeCount + commentCount) / viewCount) * 100;
}

export function getTopVideos(videos: YouTubeVideo[], limit: number): YouTubeVideo[] {
  if (limit <= 0) {
    return [];
  }

  return [...videos]
    .sort((left, right) => compareNullableCounts(right.viewCount, left.viewCount))
    .slice(0, limit);
}

export function getUploadCadenceDays(videos: YouTubeVideo[]): number | null {
  const timestamps = videos
    .map((video) => Date.parse(video.publishedAt))
    .filter((timestamp) => Number.isFinite(timestamp))
    .sort((left, right) => left - right);

  if (timestamps.length < 2) {
    return null;
  }

  const gaps = timestamps
    .slice(1)
    .map((timestamp, index) => (timestamp - timestamps[index]) / MS_PER_DAY);

  return gaps.reduce((total, gap) => total + gap, 0) / gaps.length;
}

export function getOutlierVideos(videos: YouTubeVideo[]): YouTubeVideo[] {
  const averageViewCount = getAverageViewCount(videos);

  if (averageViewCount === null) {
    return [];
  }

  const threshold = averageViewCount * OUTLIER_MULTIPLIER;

  return getTopVideos(
    videos.filter(
      (video) => video.viewCount !== null && video.viewCount >= threshold,
    ),
    videos.length,
  );
}

function compareNullableCounts(
  leftCount: number | null,
  rightCount: number | null,
): number {
  if (leftCount === null && rightCount === null) {
    return 0;
  }

  if (leftCount === null) {
    return -1;
  }

  if (rightCount === null) {
    return 1;
  }

  return leftCount - rightCount;
}
