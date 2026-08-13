// @ts-nocheck -- generated discriminator cycles are validated by the backend OpenAPI contract
export type paths = {
    "/api/v1/reviews": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** 리뷰 작성 */
        post: operations["create"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/reviews/{reviewId}/reports": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** 리뷰 신고 */
        post: operations["reportReview"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/restaurants/{restaurantId}/reports": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** 음식점 정보 신고 */
        post: operations["reportRestaurant"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/auth/refresh": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** 서비스 access token 갱신 */
        post: operations["refresh"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/auth/logout": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** 서비스 로그아웃 */
        post: operations["logout"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/reviews/{reviewId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        /** 활성 리뷰 삭제 */
        delete: operations["delete"];
        options?: never;
        head?: never;
        /** 활성 리뷰 수정 */
        patch: operations["update"];
        trace?: never;
    };
    "/api/v1/admin/review-reports/{reportId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        /** 리뷰 신고 결정 */
        patch: operations["decideReviewReport"];
        trace?: never;
    };
    "/api/v1/admin/restaurants/{restaurantId}/status": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        /** 음식점 폐업 또는 재개장 처리 */
        patch: operations["changeStatus"];
        trace?: never;
    };
    "/api/v1/admin/restaurants/{restaurantId}/pickup-location": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        /** 음식점 픽업 장소 재연결 */
        patch: operations["relinkPickupLocation"];
        trace?: never;
    };
    "/api/v1/admin/restaurants/{restaurantId}/pickup-location/verified-address": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        /** 검증된 신규 주소로 픽업 장소 재연결 */
        patch: operations["relinkVerifiedAddress"];
        trace?: never;
    };
    "/api/v1/admin/restaurants/{restaurantId}/name": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        /** 음식점 이름 정정 */
        patch: operations["rename"];
        trace?: never;
    };
    "/api/v1/admin/restaurant-reports/{reportId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        /** 음식점 정보 신고 결정 */
        patch: operations["decideRestaurantReport"];
        trace?: never;
    };
    "/api/v1/users/me": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 현재 사용자 조회 */
        get: operations["me"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/users/me/reviews": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 내 리뷰 목록 조회 */
        get: operations["list"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/restaurants/{restaurantId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 음식점 상세 조회
         * @description 배달 브랜드, 픽업 장소, 브랜드·장소 리포트와 미인증 안내를 반환합니다.
         */
        get: operations["get"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/restaurants/{restaurantId}/reviews": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 음식점 공개 리뷰 목록 조회
         * @description ACTIVE 리뷰 이력을 최신순으로 반환하며 신고로 숨겨지지 않은 의견과 익명 활동 정보만 공개합니다.
         */
        get: operations["list_1"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/restaurants/search": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 음식점 검색
         * @description 내부 배달 브랜드와 카카오 장소 후보를 통합해 최대 20개까지 반환합니다.
         */
        get: operations["search"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/admin/reviews/{reviewId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 관리자 리뷰 조사 상세 */
        get: operations["review"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/admin/review-reports": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 처리 대기 리뷰 신고 목록 */
        get: operations["listReviewReports"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/admin/restaurants/{restaurantId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 관리자 음식점 상세 */
        get: operations["restaurant"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/admin/restaurants/search": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 관리자 음식점 검색 */
        get: operations["searchRestaurants"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/admin/restaurant-reports": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 처리 대기 음식점 정보 신고 목록 */
        get: operations["listRestaurantReports"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/admin/moderation-audits": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 관리자 감사 이력 */
        get: operations["audits"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/addresses/search": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 주소 검색
         * @description 원 검색어와 표준 주소 후보 및 기존 픽업 장소 식별자를 최대 20개까지 반환합니다.
         */
        get: operations["search_1"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/auth/oauth2/authorization/kakao": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 카카오 OAuth 로그인 시작
         * @description prompt=login으로 카카오 계정을 다시 인증하도록 authorization endpoint로 redirect하고 state를 임시 HTTP session에 저장합니다.
         */
        get: {
            parameters: {
                query?: never;
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description 카카오 authorization endpoint로 이동 */
                302: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
            };
        };
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/auth/oauth2/callback/kakao": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 카카오 OAuth callback
         * @description OAuth 로그인을 완료한 뒤 HttpOnly refresh cookie를 설정하고 고정된 frontend callback URL로 redirect합니다.
         */
        get: {
            parameters: {
                query: {
                    code: string;
                    state: string;
                };
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description refresh cookie 설정 후 고정된 frontend callback URL로 이동 */
                302: {
                    headers: {
                        /** @description HttpOnly Rider Voice refresh token cookie */
                        "Set-Cookie"?: string;
                        [name: string]: unknown;
                    };
                    content?: never;
                };
            };
        };
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
};
export type webhooks = Record<string, never>;
export type components = {
    schemas: {
        CreateReviewRequest: {
            restaurantTarget: components["schemas"]["RestaurantTargetRequest"];
            /** @example 2026-07 */
            visitMonth: string;
            /** @enum {string} */
            pickupSpaceCleanliness: "VERY_GOOD" | "GOOD" | "NEEDS_IMPROVEMENT" | "MAJOR_IMPROVEMENT" | "NOT_OBSERVED";
            /** @enum {string} */
            packagingStability: "VERY_GOOD" | "GOOD" | "NEEDS_IMPROVEMENT" | "MAJOR_IMPROVEMENT" | "NOT_OBSERVED";
            /** @enum {string} */
            orderReadiness: "VERY_GOOD" | "GOOD" | "NEEDS_IMPROVEMENT" | "MAJOR_IMPROVEMENT" | "NOT_OBSERVED";
            /** @enum {string} */
            handoffAccuracy: "VERY_GOOD" | "GOOD" | "NEEDS_IMPROVEMENT" | "MAJOR_IMPROVEMENT" | "NOT_OBSERVED";
            /** @enum {string} */
            staffInteraction: "VERY_GOOD" | "GOOD" | "NEEDS_IMPROVEMENT" | "MAJOR_IMPROVEMENT" | "NOT_OBSERVED";
            /** @enum {string} */
            riderRespect: "VERY_GOOD" | "GOOD" | "NEEDS_IMPROVEMENT" | "MAJOR_IMPROVEMENT" | "NOT_OBSERVED";
            comment?: string | null;
        };
        ExistingRestaurantTargetRequest: Omit<WithRequired<components["schemas"]["RestaurantTargetRequest"], "type">, "type"> & {
            /** Format: int64 */
            restaurantId: number;
        } & {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "EXISTING";
        } & {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "EXISTING";
        };
        KakaoRestaurantTargetRequest: Omit<WithRequired<components["schemas"]["RestaurantTargetRequest"], "type">, "type"> & {
            query: string;
            kakaoPlaceId: string;
        } & {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "KAKAO";
        } & {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "KAKAO";
        };
        ManualAddressRestaurantTargetRequest: Omit<WithRequired<components["schemas"]["RestaurantTargetRequest"], "type">, "type"> & {
            addressQuery: string;
            selectedStandardAddress: string;
            detailAddress?: string | null;
            name: string;
            platforms: ("BAEMIN" | "COUPANG_EATS" | "YOGIYO" | "OTHER")[];
        } & {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "MANUAL_ADDRESS";
        } & {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "MANUAL_ADDRESS";
        };
        ManualExistingLocationRestaurantTargetRequest: Omit<WithRequired<components["schemas"]["RestaurantTargetRequest"], "type">, "type"> & {
            /** Format: int64 */
            pickupLocationId: number;
            name: string;
            platforms: ("BAEMIN" | "COUPANG_EATS" | "YOGIYO" | "OTHER")[];
        } & {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "MANUAL_EXISTING_LOCATION";
        } & {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "MANUAL_EXISTING_LOCATION";
        };
        RestaurantTargetRequest: {
            /** @enum {string} */
            type: "EXISTING" | "KAKAO" | "MANUAL_EXISTING_LOCATION" | "MANUAL_ADDRESS";
        } & (components["schemas"]["ExistingRestaurantTargetRequest"] | components["schemas"]["KakaoRestaurantTargetRequest"] | components["schemas"]["ManualExistingLocationRestaurantTargetRequest"] | components["schemas"]["ManualAddressRestaurantTargetRequest"]);
        ReviewRatingsResponse: {
            /** @enum {string} */
            pickupSpaceCleanliness?: "VERY_GOOD" | "GOOD" | "NEEDS_IMPROVEMENT" | "MAJOR_IMPROVEMENT" | "NOT_OBSERVED";
            /** @enum {string} */
            packagingStability?: "VERY_GOOD" | "GOOD" | "NEEDS_IMPROVEMENT" | "MAJOR_IMPROVEMENT" | "NOT_OBSERVED";
            /** @enum {string} */
            orderReadiness?: "VERY_GOOD" | "GOOD" | "NEEDS_IMPROVEMENT" | "MAJOR_IMPROVEMENT" | "NOT_OBSERVED";
            /** @enum {string} */
            handoffAccuracy?: "VERY_GOOD" | "GOOD" | "NEEDS_IMPROVEMENT" | "MAJOR_IMPROVEMENT" | "NOT_OBSERVED";
            /** @enum {string} */
            staffInteraction?: "VERY_GOOD" | "GOOD" | "NEEDS_IMPROVEMENT" | "MAJOR_IMPROVEMENT" | "NOT_OBSERVED";
            /** @enum {string} */
            riderRespect?: "VERY_GOOD" | "GOOD" | "NEEDS_IMPROVEMENT" | "MAJOR_IMPROVEMENT" | "NOT_OBSERVED";
        };
        ReviewResponse: {
            /** Format: int64 */
            reviewId?: number;
            restaurant?: components["schemas"]["ReviewRestaurantResponse"];
            /** @example 2026-07 */
            visitMonth?: string;
            ratings?: components["schemas"]["ReviewRatingsResponse"];
            comment?: string | null;
            /** @enum {string} */
            commentModerationStatus?: "NONE" | "PENDING" | "PUBLISHED" | "REJECTED" | "HIDDEN_REPORTED";
            /** @enum {string} */
            visibilityStatus?: "ACTIVE" | "EXCLUDED";
            /** Format: date-time */
            createdAt?: string;
            /** Format: date-time */
            updatedAt?: string;
        };
        ReviewRestaurantResponse: {
            /** Format: int64 */
            restaurantId?: number;
            name?: string;
            address?: string;
        };
        CreateReviewReportRequest: {
            /** @enum {string} */
            reason: "PERSONAL_INFORMATION" | "ABUSIVE_CONTENT" | "IRRELEVANT_CONTENT" | "FALSE_INFORMATION" | "SPAM" | "OTHER";
            details?: string | null;
        };
        ProblemDetail: {
            /** Format: uri */
            type?: string;
            title?: string;
            /** Format: int32 */
            status?: number;
            detail?: string;
            /** Format: uri */
            instance?: string;
            properties?: {
                [key: string]: Record<string, never>;
            };
            /** @description 안정적인 Rider Voice 오류 코드 */
            code: string;
        };
        ReviewReportResponse: {
            /** Format: int64 */
            reportId?: number;
            /** Format: int64 */
            reviewId?: number;
            /** @enum {string} */
            reason?: "PERSONAL_INFORMATION" | "ABUSIVE_CONTENT" | "IRRELEVANT_CONTENT" | "FALSE_INFORMATION" | "SPAM" | "OTHER";
            /** @enum {string} */
            status?: "PENDING" | "RESOLVED";
            /** @enum {string|null} */
            decision?: "DISMISS" | "HIDE_COMMENT" | "EXCLUDE_REVIEW" | null;
            /** Format: date-time */
            createdAt?: string;
            /** Format: date-time */
            decidedAt?: string | null;
        };
        CreateRestaurantInfoReportRequest: {
            /** @enum {string} */
            reason: "INCORRECT_NAME" | "INCORRECT_PICKUP_LOCATION" | "DUPLICATE" | "CLOSED" | "OTHER";
            details?: string | null;
        };
        RestaurantInfoReportResponse: {
            /** Format: int64 */
            reportId?: number;
            /** Format: int64 */
            restaurantId?: number;
            /** @enum {string} */
            reason?: "INCORRECT_NAME" | "INCORRECT_PICKUP_LOCATION" | "DUPLICATE" | "CLOSED" | "OTHER";
            /** @enum {string} */
            status?: "PENDING" | "RESOLVED";
            /** @enum {string|null} */
            decision?: "DISMISS" | "RESOLVE" | null;
            /** Format: date-time */
            createdAt?: string;
            /** Format: date-time */
            decidedAt?: string | null;
        };
        AccessSessionResponse: {
            accessToken?: string;
            user?: components["schemas"]["UserResponse"];
        };
        UserResponse: {
            /** Format: int64 */
            id?: number;
            status?: string;
            /** @enum {string} */
            role?: "USER" | "ADMIN";
        };
        UpdateReviewRequest: {
            /** @enum {string} */
            pickupSpaceCleanliness: "VERY_GOOD" | "GOOD" | "NEEDS_IMPROVEMENT" | "MAJOR_IMPROVEMENT" | "NOT_OBSERVED";
            /** @enum {string} */
            packagingStability: "VERY_GOOD" | "GOOD" | "NEEDS_IMPROVEMENT" | "MAJOR_IMPROVEMENT" | "NOT_OBSERVED";
            /** @enum {string} */
            orderReadiness: "VERY_GOOD" | "GOOD" | "NEEDS_IMPROVEMENT" | "MAJOR_IMPROVEMENT" | "NOT_OBSERVED";
            /** @enum {string} */
            handoffAccuracy: "VERY_GOOD" | "GOOD" | "NEEDS_IMPROVEMENT" | "MAJOR_IMPROVEMENT" | "NOT_OBSERVED";
            /** @enum {string} */
            staffInteraction: "VERY_GOOD" | "GOOD" | "NEEDS_IMPROVEMENT" | "MAJOR_IMPROVEMENT" | "NOT_OBSERVED";
            /** @enum {string} */
            riderRespect: "VERY_GOOD" | "GOOD" | "NEEDS_IMPROVEMENT" | "MAJOR_IMPROVEMENT" | "NOT_OBSERVED";
            comment?: string | null;
        };
        ReviewReportDecisionRequest: {
            /** @enum {string} */
            decision: "DISMISS" | "HIDE_COMMENT" | "EXCLUDE_REVIEW";
            /** @description 관리자 결정 사유 */
            reason?: string | null;
        };
        ChangeRestaurantStatusRequest: {
            /** @enum {string} */
            action: "CLOSE" | "REOPEN";
            reason?: string | null;
        };
        RestaurantStatusChangeResponse: {
            /** Format: int64 */
            restaurantId?: number;
            /** @enum {string} */
            status?: "ACTIVE" | "CLOSED";
            /** Format: date-time */
            completedAt?: string;
        };
        RelinkRestaurantPickupLocationRequest: {
            /** Format: int64 */
            pickupLocationId: number;
            /** @description 관리자 픽업 장소 정정 사유 */
            reason?: string | null;
        };
        RestaurantPickupRelinkResponse: {
            /** Format: int64 */
            restaurantId?: number;
            /** Format: int64 */
            pickupLocationId?: number;
            /** Format: date-time */
            completedAt?: string;
        };
        RelinkRestaurantVerifiedAddressRequest: {
            addressQuery: string;
            selectedStandardAddress: string;
            detailAddress?: string | null;
            reason?: string | null;
        };
        RenameRestaurantRequest: {
            name: string;
            reason?: string | null;
        };
        RestaurantRenameResponse: {
            /** Format: int64 */
            restaurantId?: number;
            name?: string;
            /** Format: date-time */
            completedAt?: string;
        };
        CloseRestaurantCorrectionRequest: Omit<WithRequired<components["schemas"]["RestaurantInfoCorrectionRequest"], "type">, "type"> & {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "CLOSE";
        } & {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "CLOSE";
        };
        RelinkExistingPickupCorrectionRequest: Omit<WithRequired<components["schemas"]["RestaurantInfoCorrectionRequest"], "type">, "type"> & {
            /** Format: int64 */
            pickupLocationId: number;
        } & {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "RELINK_EXISTING_PICKUP";
        } & {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "RELINK_EXISTING_PICKUP";
        };
        RelinkVerifiedAddressCorrectionRequest: Omit<WithRequired<components["schemas"]["RestaurantInfoCorrectionRequest"], "type">, "type"> & {
            addressQuery: string;
            selectedStandardAddress: string;
            detailAddress?: string | null;
        } & {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "RELINK_VERIFIED_ADDRESS";
        } & {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "RELINK_VERIFIED_ADDRESS";
        };
        RenameRestaurantCorrectionRequest: Omit<WithRequired<components["schemas"]["RestaurantInfoCorrectionRequest"], "type">, "type"> & {
            name: string;
        } & {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "RENAME";
        } & {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "RENAME";
        };
        RestaurantInfoCorrectionRequest: {
            /** @enum {string} */
            type: "RENAME" | "RELINK_EXISTING_PICKUP" | "RELINK_VERIFIED_ADDRESS" | "CLOSE";
        } & (components["schemas"]["RenameRestaurantCorrectionRequest"] | components["schemas"]["RelinkExistingPickupCorrectionRequest"] | components["schemas"]["RelinkVerifiedAddressCorrectionRequest"] | components["schemas"]["CloseRestaurantCorrectionRequest"]);
        RestaurantInfoReportDecisionRequest: {
            /** @enum {string} */
            decision: "DISMISS" | "RESOLVE";
            /** @description 관리자 결정 사유 */
            reason?: string | null;
            correction?: components["schemas"]["RestaurantInfoCorrectionRequest"] | null;
        };
        MyReviewListResponse: {
            items?: components["schemas"]["ReviewResponse"][];
            /** @description createdAt과 reviewId 기반 opaque cursor */
            nextCursor?: string | null;
        };
        RestaurantAggregateMetricResponse: {
            /** Format: int32 */
            observedCount?: number;
            /** Format: int32 */
            notObservedCount?: number;
            distribution?: {
                [key: string]: number;
            };
        };
        RestaurantBrandReportMetricsResponse: {
            packagingStability?: components["schemas"]["RestaurantAggregateMetricResponse"];
            orderReadiness?: components["schemas"]["RestaurantAggregateMetricResponse"];
            handoffAccuracy?: components["schemas"]["RestaurantAggregateMetricResponse"];
        };
        RestaurantBrandReportResponse: {
            /** @enum {string} */
            status?: "NO_REVIEWS" | "COLLECTING" | "PUBLISHED";
            /** Format: int32 */
            contributorCount?: number;
            metrics?: components["schemas"]["RestaurantBrandReportMetricsResponse"] | null;
        };
        RestaurantDetailResponse: {
            /** Format: int64 */
            restaurantId?: number;
            name?: string;
            /** @enum {string} */
            status?: "ACTIVE" | "CLOSED";
            pickupLocation?: components["schemas"]["RestaurantPickupLocationResponse"];
            brandReport?: components["schemas"]["RestaurantBrandReportResponse"];
            pickupLocationReport?: components["schemas"]["RestaurantPickupLocationReportResponse"];
            /** @enum {string} */
            verificationStatus?: "UNVERIFIED";
            /** @example 라이더 신분과 실제 방문 여부가 인증되지 않은 정보입니다. */
            verificationNotice?: string;
        };
        RestaurantPickupLocationReportMetricsResponse: {
            pickupSpaceCleanliness?: components["schemas"]["RestaurantAggregateMetricResponse"];
            staffInteraction?: components["schemas"]["RestaurantAggregateMetricResponse"];
            riderRespect?: components["schemas"]["RestaurantAggregateMetricResponse"];
        };
        RestaurantPickupLocationReportResponse: {
            /** @enum {string} */
            status?: "NO_REVIEWS" | "COLLECTING" | "PUBLISHED";
            /** Format: int32 */
            contributorCount?: number;
            metrics?: components["schemas"]["RestaurantPickupLocationReportMetricsResponse"] | null;
        };
        RestaurantPickupLocationResponse: {
            /** Format: int64 */
            pickupLocationId?: number;
            standardAddress?: string;
            detailAddress?: string | null;
            latitude?: number;
            longitude?: number;
        };
        PublicReviewAuthorActivityResponse: {
            /** Format: int32 */
            activityMonths?: number;
            /** Format: int64 */
            publicReviewCount?: number;
        };
        PublicReviewListItemResponse: {
            /** Format: int64 */
            reviewId?: number;
            /** @example 2026-07 */
            visitMonth?: string;
            ratings?: components["schemas"]["ReviewRatingsResponse"];
            /** @description 작성 즉시 공개되며 신고 또는 관리자 조치로 숨겨질 수 있습니다. */
            comment?: string | null;
            authorActivity?: components["schemas"]["PublicReviewAuthorActivityResponse"];
            /** Format: date-time */
            createdAt?: string;
            /** @enum {string} */
            verificationStatus?: "UNVERIFIED";
            /** @example 라이더 신분과 실제 방문 여부가 인증되지 않은 정보입니다. */
            verificationNotice?: string;
        };
        PublicReviewListResponse: {
            items?: components["schemas"]["PublicReviewListItemResponse"][];
            /** @description createdAt과 reviewId 기반 opaque cursor */
            nextCursor?: string | null;
        };
        RestaurantSearchCandidateResponse: {
            /** @enum {string} */
            candidateType?: "INTERNAL" | "KAKAO";
            /** Format: int64 */
            restaurantId?: number | null;
            kakaoPlaceId?: string | null;
            name?: string;
            address?: string;
            /** @enum {string} */
            aggregationStatus?: "NO_REVIEWS" | "COLLECTING" | "PUBLISHED";
            /** Format: int32 */
            contributorCount?: number;
        };
        RestaurantSearchResponse: {
            /** @enum {string} */
            externalSearchStatus?: "AVAILABLE" | "UNAVAILABLE";
            candidates?: components["schemas"]["RestaurantSearchCandidateResponse"][];
        };
        AdminReviewAuthorResponse: {
            /** Format: int64 */
            userId?: number;
            /** @enum {string} */
            status?: "ACTIVE" | "RATE_LIMITED" | "SUSPENDED" | "WITHDRAWN";
            /** Format: int32 */
            activityMonths?: number;
            /** Format: int64 */
            publicReviewCount?: number;
        };
        AdminReviewDetailResponse: {
            /** Format: int64 */
            reviewId?: number;
            author?: components["schemas"]["AdminReviewAuthorResponse"];
            restaurant?: components["schemas"]["AdminReviewRestaurantResponse"];
            visitMonth?: string;
            ratings?: components["schemas"]["AdminReviewRatingsResponse"];
            comment?: string | null;
            /** @enum {string} */
            commentStatus?: "NONE" | "PENDING" | "PUBLISHED" | "REJECTED" | "HIDDEN_REPORTED";
            /** @enum {string} */
            visibilityStatus?: "ACTIVE" | "EXCLUDED";
            active?: boolean;
            /** Format: date-time */
            deletedAt?: string | null;
            /** Format: date-time */
            createdAt?: string;
            /** Format: date-time */
            updatedAt?: string;
        };
        AdminReviewRatingsResponse: {
            /** @enum {string} */
            pickupSpaceCleanliness?: "VERY_GOOD" | "GOOD" | "NEEDS_IMPROVEMENT" | "MAJOR_IMPROVEMENT" | "NOT_OBSERVED";
            /** @enum {string} */
            packagingStability?: "VERY_GOOD" | "GOOD" | "NEEDS_IMPROVEMENT" | "MAJOR_IMPROVEMENT" | "NOT_OBSERVED";
            /** @enum {string} */
            orderReadiness?: "VERY_GOOD" | "GOOD" | "NEEDS_IMPROVEMENT" | "MAJOR_IMPROVEMENT" | "NOT_OBSERVED";
            /** @enum {string} */
            handoffAccuracy?: "VERY_GOOD" | "GOOD" | "NEEDS_IMPROVEMENT" | "MAJOR_IMPROVEMENT" | "NOT_OBSERVED";
            /** @enum {string} */
            staffInteraction?: "VERY_GOOD" | "GOOD" | "NEEDS_IMPROVEMENT" | "MAJOR_IMPROVEMENT" | "NOT_OBSERVED";
            /** @enum {string} */
            riderRespect?: "VERY_GOOD" | "GOOD" | "NEEDS_IMPROVEMENT" | "MAJOR_IMPROVEMENT" | "NOT_OBSERVED";
        };
        AdminReviewRestaurantResponse: {
            /** Format: int64 */
            restaurantId?: number;
            name?: string;
            /** @enum {string} */
            status?: "ACTIVE" | "CLOSED";
            /** Format: int64 */
            pickupLocationId?: number;
            pickupAddress?: string;
        };
        PendingReviewReportPageResponse: {
            items?: components["schemas"]["PendingReviewReportResponse"][];
            /** @description createdAt과 reportId 기반 opaque cursor */
            nextCursor?: string | null;
        };
        PendingReviewReportResponse: {
            /** Format: int64 */
            reportId?: number;
            /** Format: int64 */
            reporterUserId?: number;
            /** Format: int64 */
            reviewId?: number;
            /** @enum {string} */
            reason?: "PERSONAL_INFORMATION" | "ABUSIVE_CONTENT" | "IRRELEVANT_CONTENT" | "FALSE_INFORMATION" | "SPAM" | "OTHER";
            details?: string | null;
            /** Format: date-time */
            createdAt?: string;
        };
        AdminPickupLocationResponse: {
            /** Format: int64 */
            pickupLocationId?: number;
            standardAddress?: string;
            detailAddress?: string | null;
            latitude?: number;
            longitude?: number;
        };
        AdminRestaurantDetailResponse: {
            /** Format: int64 */
            restaurantId?: number;
            name?: string;
            /** @enum {string} */
            status?: "ACTIVE" | "CLOSED";
            pickupLocation?: components["schemas"]["AdminPickupLocationResponse"];
            kakaoPlaceId?: string | null;
            platforms?: ("BAEMIN" | "COUPANG_EATS" | "YOGIYO" | "OTHER")[];
            /** Format: int64 */
            pendingReportCount?: number;
            /** Format: date-time */
            createdAt?: string;
            /** Format: date-time */
            updatedAt?: string;
        };
        AdminRestaurantSearchItemResponse: {
            /** Format: int64 */
            restaurantId?: number;
            name?: string;
            /** @enum {string} */
            status?: "ACTIVE" | "CLOSED";
            /** Format: int64 */
            pickupLocationId?: number;
            standardAddress?: string;
            detailAddress?: string | null;
            /** Format: date-time */
            createdAt?: string;
        };
        AdminRestaurantSearchPageResponse: {
            items?: components["schemas"]["AdminRestaurantSearchItemResponse"][];
            nextCursor?: string | null;
        };
        PendingRestaurantInfoReportPageResponse: {
            items?: components["schemas"]["PendingRestaurantInfoReportResponse"][];
            /** @description createdAt과 reportId 기반 opaque cursor */
            nextCursor?: string | null;
        };
        PendingRestaurantInfoReportResponse: {
            /** Format: int64 */
            reportId?: number;
            /** Format: int64 */
            reporterUserId?: number;
            /** Format: int64 */
            restaurantId?: number;
            /** @enum {string} */
            reason?: "INCORRECT_NAME" | "INCORRECT_PICKUP_LOCATION" | "DUPLICATE" | "CLOSED" | "OTHER";
            details?: string | null;
            /** Format: date-time */
            createdAt?: string;
        };
        ModerationAuditPageResponse: {
            items?: components["schemas"]["ModerationAuditResponse"][];
            nextCursor?: string | null;
        };
        ModerationAuditResponse: {
            /** Format: int64 */
            auditId?: number;
            /** Format: int64 */
            actorUserId?: number;
            /** @enum {string} */
            action?: "COMMENT_APPROVED" | "COMMENT_REJECTED" | "REVIEW_REPORT_DISMISSED" | "REVIEW_COMMENT_HIDDEN" | "REVIEW_EXCLUDED" | "RESTAURANT_REPORT_DISMISSED" | "RESTAURANT_INFO_CORRECTED" | "RESTAURANT_PICKUP_RELINKED" | "RESTAURANT_RENAMED" | "RESTAURANT_CLOSED" | "RESTAURANT_REOPENED";
            /** @enum {string} */
            targetType?: "REVIEW" | "REVIEW_REPORT" | "RESTAURANT" | "RESTAURANT_INFO_REPORT";
            /** Format: int64 */
            targetId?: number;
            reason?: string | null;
            beforeState?: string;
            afterState?: string;
            /** Format: date-time */
            occurredAt?: string;
            /** Format: date-time */
            createdAt?: string;
        };
        AddressSearchCandidateResponse: {
            standardAddress?: string;
            lotNumberAddress?: string | null;
            latitude?: number;
            longitude?: number;
            /** Format: int64 */
            existingPickupLocationId?: number | null;
        };
        AddressSearchResponse: {
            query?: string;
            candidates?: components["schemas"]["AddressSearchCandidateResponse"][];
        };
        DeleteReviewResponse: {
            /** Format: int64 */
            reviewId?: number;
        };
    };
    responses: never;
    parameters: never;
    requestBodies: never;
    headers: never;
    pathItems: never;
};
export type $defs = Record<string, never>;
export interface operations {
    create: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateReviewRequest"];
            };
        };
        responses: {
            /** @description 리뷰 작성 완료 */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ReviewResponse"];
                };
            };
        };
    };
    reportReview: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                reviewId: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateReviewReportRequest"];
            };
        };
        responses: {
            /** @description 리뷰 신고 접수 완료 */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ReviewReportResponse"];
                };
            };
            /** @description Bad Request */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description Unauthorized */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description Forbidden */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description Not Found */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description Conflict */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description Too Many Requests */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    reportRestaurant: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                restaurantId: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateRestaurantInfoReportRequest"];
            };
        };
        responses: {
            /** @description 음식점 정보 신고 접수 완료 */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["RestaurantInfoReportResponse"];
                };
            };
            /** @description Bad Request */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description Unauthorized */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description Forbidden */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description Not Found */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description Conflict */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description Too Many Requests */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    refresh: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["AccessSessionResponse"];
                };
            };
        };
    };
    logout: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 로그아웃 완료 */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    delete: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                reviewId: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DeleteReviewResponse"];
                };
            };
        };
    };
    update: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                reviewId: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UpdateReviewRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ReviewResponse"];
                };
            };
        };
    };
    decideReviewReport: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                reportId: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ReviewReportDecisionRequest"];
            };
        };
        responses: {
            /** @description 리뷰 신고 결정 완료 */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ReviewReportResponse"];
                };
            };
            /** @description Bad Request */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description Unauthorized */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description Forbidden */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description Not Found */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description Conflict */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    changeStatus: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                restaurantId: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ChangeRestaurantStatusRequest"];
            };
        };
        responses: {
            /** @description 음식점 상태 변경 완료 */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["RestaurantStatusChangeResponse"];
                };
            };
            /** @description Bad Request */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description Unauthorized */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description Forbidden */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description Not Found */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description Conflict */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    relinkPickupLocation: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                restaurantId: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["RelinkRestaurantPickupLocationRequest"];
            };
        };
        responses: {
            /** @description 픽업 장소 재연결 완료 */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["RestaurantPickupRelinkResponse"];
                };
            };
            /** @description Bad Request */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description Unauthorized */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description Forbidden */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description Not Found */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description Conflict */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    relinkVerifiedAddress: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                restaurantId: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["RelinkRestaurantVerifiedAddressRequest"];
            };
        };
        responses: {
            /** @description 검증된 신규 주소로 픽업 장소 재연결 완료 */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["RestaurantPickupRelinkResponse"];
                };
            };
            /** @description Bad Request */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description Unauthorized */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description Forbidden */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description Not Found */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description Conflict */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    rename: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                restaurantId: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["RenameRestaurantRequest"];
            };
        };
        responses: {
            /** @description 음식점 이름 정정 완료 */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["RestaurantRenameResponse"];
                };
            };
            /** @description Bad Request */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description Unauthorized */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description Forbidden */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description Not Found */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description Conflict */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    decideRestaurantReport: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                reportId: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["RestaurantInfoReportDecisionRequest"];
            };
        };
        responses: {
            /** @description 음식점 정보 신고 결정 완료 */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["RestaurantInfoReportResponse"];
                };
            };
            /** @description Bad Request */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description Unauthorized */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description Forbidden */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description Not Found */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description Conflict */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    me: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["UserResponse"];
                };
            };
        };
    };
    list: {
        parameters: {
            query?: {
                /** @description createdAt과 reviewId 기반 opaque cursor */
                cursor?: string | null;
                size?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["MyReviewListResponse"];
                };
            };
        };
    };
    get: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                restaurantId: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 음식점 상세 조회 성공 */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["RestaurantDetailResponse"];
                };
            };
            /** @description 음식점을 찾을 수 없음 */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    list_1: {
        parameters: {
            query?: {
                /** @description createdAt과 reviewId 기반 opaque cursor */
                cursor?: string | null;
                size?: number;
            };
            header?: never;
            path: {
                restaurantId: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 공개 리뷰 목록 조회 성공 */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PublicReviewListResponse"];
                };
            };
        };
    };
    search: {
        parameters: {
            query: {
                /**
                 * @description 정규화 후 2~100자인 검색어
                 * @example 강남 분식
                 */
                query: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 검색 성공 */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["RestaurantSearchResponse"];
                };
            };
            /** @description 호출자당 분당 30회 공개 검색 제한 초과 */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    review: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                reviewId: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["AdminReviewDetailResponse"];
                };
            };
        };
    };
    listReviewReports: {
        parameters: {
            query?: {
                /** @description createdAt과 ID 기반 opaque cursor */
                cursor?: string | null;
                size?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 처리 대기 리뷰 신고 목록 조회 성공 */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PendingReviewReportPageResponse"];
                };
            };
            /** @description Bad Request */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description Unauthorized */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description Forbidden */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description Not Found */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description Conflict */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    restaurant: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                restaurantId: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["AdminRestaurantDetailResponse"];
                };
            };
        };
    };
    searchRestaurants: {
        parameters: {
            query: {
                query: string;
                status?: "ACTIVE" | "CLOSED";
                cursor?: string | null;
                size?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["AdminRestaurantSearchPageResponse"];
                };
            };
        };
    };
    listRestaurantReports: {
        parameters: {
            query?: {
                /** @description createdAt과 ID 기반 opaque cursor */
                cursor?: string | null;
                size?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 처리 대기 음식점 정보 신고 목록 조회 성공 */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PendingRestaurantInfoReportPageResponse"];
                };
            };
            /** @description Bad Request */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description Unauthorized */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description Forbidden */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description Not Found */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description Conflict */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    audits: {
        parameters: {
            query?: {
                targetType?: "REVIEW" | "REVIEW_REPORT" | "RESTAURANT" | "RESTAURANT_INFO_REPORT";
                targetId?: number;
                actorUserId?: number;
                action?: "COMMENT_APPROVED" | "COMMENT_REJECTED" | "REVIEW_REPORT_DISMISSED" | "REVIEW_COMMENT_HIDDEN" | "REVIEW_EXCLUDED" | "RESTAURANT_REPORT_DISMISSED" | "RESTAURANT_INFO_CORRECTED" | "RESTAURANT_PICKUP_RELINKED" | "RESTAURANT_RENAMED" | "RESTAURANT_CLOSED" | "RESTAURANT_REOPENED";
                cursor?: string | null;
                size?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ModerationAuditPageResponse"];
                };
            };
        };
    };
    search_1: {
        parameters: {
            query: {
                /**
                 * @description 정규화 후 2~100자인 검색어
                 * @example 강남 분식
                 */
                query: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["AddressSearchResponse"];
                };
            };
        };
    };
}
type WithRequired<T, K extends keyof T> = T & {
    [P in K]-?: T[P];
};
