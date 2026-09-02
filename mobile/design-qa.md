# Rider Voice mobile design QA

## Evidence

- Source visual truth: `/Users/seodong-gwon/Documents/Codex/2026-08-21/new-chat/rider-voice-prototype/qa/current-audit-20260825/`
  - `02-home.jpg`, `03-results.jpg`, `04-detail.jpg`, `06-login.jpg`, `07-first-review.jpg`, `10-activity.jpg`
- Browser-rendered implementation: `/Users/seodong-gwon/Desktop/sideproject/mobile/qa/mobile-implementation/`
  - `home-393x852.jpg`, `search-393x852.jpg`, `detail-393x852.jpg`, `login-393x852.jpg`, `review-393x852.jpg`, `activity-393x852.jpg`
- Combined comparison evidence: `/Users/seodong-gwon/Desktop/sideproject/mobile/qa/comparisons/*-normalized-comparison.jpg`
- Implementation URL: `http://localhost:8083/`
- Viewport: 393 × 852 CSS px, device scale factor 1
- State: light theme, mock API mode, logged-out public flow

The source captures are 865 × 791 px and include the prototype canvas, iPhone bezel, status bar, and home indicator. For the combined comparison, the visible 300 × 650 phone region was cropped and normalized to 393 × 852. The implementation screenshots are native 393 × 852 browser captures. Native system chrome is intentionally absent from the web render and will be supplied by iOS or Android on device.

## Full-view comparison

- Home: hierarchy, search control, three information rows, and persistent navigation match the selected composition. The former public verification banner has been removed by the current role policy.
- Search: the reviewed/Kakao grouping, counts, row density, purple reviewed-place accent, and first-review action remain clear without distance metadata.
- Detail: helmet score treatment, information density, dividers, and fixed bottom navigation match the selected direction; review-writing actions are role-gated.
- Login: centered brand block, two reassurance rows, Kakao CTA, secondary action, and account-identification note match.
- Review: question header, progress, first-review note, five radio choices, privacy note, and fixed CTA match. The optional comment is intentionally deferred to the sixth step rather than repeated on every metric step.
- Activity: profile header, two summary cards, recent-review rows, outline CTA, and navigation match.

## Focused comparison

- Typography and home hierarchy: `home-normalized-comparison.jpg`
- Helmet icon shape, amber score token, and metric rhythm: `detail-normalized-comparison.jpg`
- Kakao CTA, disclosure, and reassurance-row alignment: `login-normalized-comparison.jpg`
- Radio hit areas, progress state, and fixed CTA: `review-normalized-comparison.jpg`

These focused regions were readable at 1:1 density; no additional enlargement was needed.

## Required fidelity surfaces

- Fonts and typography: official LINE Seed KR Regular/Bold files are bundled and the browser reports `LINESeedKR-Bold` for display text. Sizes, line heights, hierarchy, wrapping, and 1.5× font scaling policy are consistent with the design system.
- Spacing and layout rhythm: 22 px screen gutters, 4 px-based spacing tokens, section gaps, radii, separators, and fixed navigation/CTA areas are consistent across screens. No persistent controls are clipped.
- Colors and visual tokens: jade primary, pale mint surfaces, periwinkle reviewed-place accent, apricot/lavender/mint category tiles, and amber helmet score are consistently applied with sufficient foreground contrast.
- Image quality and asset fidelity: all interface symbols use `@expo/vector-icons`; the helmet uses the library `racing-helmet` glyph rather than a text symbol or custom drawing. There are no blurred, stretched, placeholder, or approximate raster assets in the evaluated screens.
- Copy and content: the five-author publication rule, role-gated review labels, Kakao place state, login explanation, and privacy copy match the approved product boundary. Public verification status copy, distance, and nearest-ordering copy are absent.

## Findings

- No actionable P0, P1, or P2 visual differences remain.
- P3: app launcher and splash assets still use the Expo starter artwork. They are outside the evaluated in-app flows and should be replaced when the Rider Voice brand mark is finalized.
- P3: final native-device checks remain for iOS/Android safe areas, system font scaling, keyboard avoidance, and platform-specific antialiasing.

## Comparison history

### Pass 1

- [P2] Typography used the system sans-serif fallback instead of the approved LINE Seed KR family.
- Fix: downloaded the official LINE Seed KR distribution, bundled Regular and Bold TTF files with the OFL 1.1 text, loaded them through `expo-font`, and selected the face by text weight.

### Pass 2

- Evidence: all six `*-normalized-comparison.jpg` files were regenerated from the browser after the font fix at 393 × 852.
- Result: no P0/P1/P2 findings. Visual hierarchy, layout rhythm, colors, icons, copy, and interaction states are acceptable.

## Interaction and runtime checks

- Home search input → search results
- Reviewed restaurant row → restaurant detail
- Review choice enables CTA → next question
- Login, review, and activity direct routes render
- Final browser console check: no errors or warnings
- TypeScript, ESLint, Jest, Expo dependency check, and static web export pass

## Follow-up polish

- Create final launcher, adaptive, splash, and favicon assets after the brand mark is approved.
- Repeat the visual regression pass on one physical iOS device and one physical Android device.

final result: passed
