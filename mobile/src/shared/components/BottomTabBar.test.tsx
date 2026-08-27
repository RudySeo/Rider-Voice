import { fireEvent, render } from '@testing-library/react-native';
import { router } from 'expo-router';

import { BottomTabBar } from '@/shared/components/BottomTabBar';

jest.mock('expo-router', () => ({ router: { replace: jest.fn() } }));
jest.mock('@/shared/auth/AuthProvider', () => ({ useAuth: () => ({ user: { id: 1 } }) }));

describe('bottom tab review entry', () => {
  it('opens review target search without another login for an authenticated user', async () => {
    const view = await render(<BottomTabBar active="home" />);

    fireEvent.press(view.getByText('리뷰 작성'));

    expect(router.replace).toHaveBeenCalledWith({ pathname: '/search', params: { mode: 'review' } });
  });
});
