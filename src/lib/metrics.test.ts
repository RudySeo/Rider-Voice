import { describe, expect, it } from "vitest";
import type { YouTubeVideo } from "@/types/youtube";
import {
  getAverageViewCount,
  getEngagementRate,
  getOutlierVideos,
  getTopVideos,
  getUploadCadenceDays,
} from "./metrics";

const video = (overrides: Partial<YouTubeVideo>): YouTubeVideo => ({
  id: "video-id",
  title: "Video title",
  description: "Video description",
  publishedAt: "2026-05-01T00:00:00Z",
  thumbnailUrl: "https://img.youtube.com/vi/video-id/default.jpg",
  viewCount: 100,
  likeCount: 10,
  commentCount: 1,
  duration: "PT10M",
  ...overrides,
});

describe("metric helpers", () => {
  describe("getAverageViewCount", () => {
    it("returns null for an empty list or all-null view counts", () => {
      expect(getAverageViewCount([])).toBeNull();
      expect(getAverageViewCount([video({ viewCount: null })])).toBeNull();
    });

    it("averages only known view counts", () => {
      expect(
        getAverageViewCount([
          video({ id: "a", viewCount: 100 }),
          video({ id: "b", viewCount: null }),
          video({ id: "c", viewCount: 300 }),
        ]),
      ).toBe(200);
    });
  });

  describe("getEngagementRate", () => {
    it("returns a percentage engagement rate when all counts are known", () => {
      expect(
        getEngagementRate(
          video({ viewCount: 1_000, likeCount: 80, commentCount: 20 }),
        ),
      ).toBe(10);
    });

    it("returns null when required counts are missing or views are zero", () => {
      expect(getEngagementRate(video({ viewCount: null }))).toBeNull();
      expect(getEngagementRate(video({ viewCount: 0 }))).toBeNull();
      expect(getEngagementRate(video({ likeCount: null }))).toBeNull();
      expect(getEngagementRate(video({ commentCount: null }))).toBeNull();
    });
  });

  describe("getTopVideos", () => {
    it("sorts videos by known view count, keeps null counts last, and does not mutate input", () => {
      const videos = [
        video({ id: "a", viewCount: 120 }),
        video({ id: "b", viewCount: null }),
        video({ id: "c", viewCount: 900 }),
      ];

      expect(getTopVideos(videos, 2).map((item) => item.id)).toEqual(["c", "a"]);
      expect(videos.map((item) => item.id)).toEqual(["a", "b", "c"]);
    });

    it("returns an empty list for non-positive limits", () => {
      expect(getTopVideos([video({ id: "a" })], 0)).toEqual([]);
    });
  });

  describe("getUploadCadenceDays", () => {
    it("returns null when fewer than two valid publish dates are available", () => {
      expect(getUploadCadenceDays([])).toBeNull();
      expect(getUploadCadenceDays([video({ id: "a" })])).toBeNull();
    });

    it("returns the average day gap between uploads", () => {
      expect(
        getUploadCadenceDays([
          video({ id: "latest", publishedAt: "2026-05-10T00:00:00Z" }),
          video({ id: "oldest", publishedAt: "2026-05-01T00:00:00Z" }),
          video({ id: "middle", publishedAt: "2026-05-04T00:00:00Z" }),
        ]),
      ).toBe(4.5);
    });
  });

  describe("getOutlierVideos", () => {
    it("returns videos at least 1.5x above the known average view count", () => {
      expect(
        getOutlierVideos([
          video({ id: "steady-a", viewCount: 100 }),
          video({ id: "steady-b", viewCount: 120 }),
          video({ id: "breakout", viewCount: 900 }),
          video({ id: "unknown", viewCount: null }),
        ]).map((item) => item.id),
      ).toEqual(["breakout"]);
    });

    it("returns an empty list when there is no comparable view data", () => {
      expect(getOutlierVideos([])).toEqual([]);
      expect(getOutlierVideos([video({ id: "unknown", viewCount: null })])).toEqual([]);
    });
  });
});
