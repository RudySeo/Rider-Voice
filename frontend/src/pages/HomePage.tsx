import { RestaurantSearch } from '@/features/restaurants/PublicDiscovery'

import styles from './HomePage.module.css'

export function HomePage() {
  return (
    <div className={styles.page}>
      <section className={styles.hero}>
        <p className={styles.eyebrow}>Local prototype</p>
        <h1 className={styles.title}>Rider Voice</h1>
        <p className={styles.description}>
          음식 배달 픽업 과정에서 관찰한 운영 환경을 구조화된 리뷰로
          살펴봅니다.
        </p>
        <p className={styles.notice}>
          게시되는 정보는 라이더 신분과 실제 방문 여부가 인증되지 않은
          정보입니다.
        </p>
      </section>
      <RestaurantSearch />
    </div>
  )
}
