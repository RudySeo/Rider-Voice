import { Link, NavLink, Outlet } from 'react-router-dom'

import { AuthNavigation } from '@/features/auth/AuthFlow'

import styles from './AppShell.module.css'

export function AppShell() {
  return (
    <div className={styles.shell}>
      <a className={styles.skipLink} href="#main-content">
        본문으로 건너뛰기
      </a>
      <header className={styles.header}>
        <div className={styles.headerInner}>
          <Link
            aria-label="Rider Voice 홈"
            className={styles.brand}
            to="/"
          >
            Rider Voice
          </Link>
          <nav aria-label="주요 메뉴" className={styles.navigation}>
            <NavLink
              className={({ isActive }) =>
                [
                  styles.navigationLink,
                  isActive ? styles.navigationLinkActive : undefined,
                ]
                  .filter(Boolean)
                  .join(' ')
              }
              end
              to="/"
            >
              홈
            </NavLink>
            <AuthNavigation />
          </nav>
        </div>
      </header>
      <main className={styles.main} id="main-content" tabIndex={-1}>
        <Outlet />
      </main>
    </div>
  )
}
