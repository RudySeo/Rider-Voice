import { render } from '@testing-library/react-native';

import { BottomTabBar } from '@/shared/components/BottomTabBar';
import { useAuth } from '@/shared/auth/AuthProvider';

jest.mock('expo-router', () => ({ router: { replace: jest.fn() } }));
jest.mock('@/shared/auth/AuthProvider', () => ({ useAuth: jest.fn() }));

it('hides review writing for USER and shows it for RIDER', async () => {
  jest.mocked(useAuth).mockReturnValue({ user: { id: 1, status: 'ACTIVE', role: 'USER' } } as ReturnType<typeof useAuth>);
  const userView = await render(<BottomTabBar active="home" />);
  expect(userView.queryByText('리뷰 작성')).toBeNull();

  jest.mocked(useAuth).mockReturnValue({ user: { id: 2, status: 'ACTIVE', role: 'RIDER' } } as ReturnType<typeof useAuth>);
  await userView.rerender(<BottomTabBar active="home" />);
  expect(userView.getByText('리뷰 작성')).toBeTruthy();
});
