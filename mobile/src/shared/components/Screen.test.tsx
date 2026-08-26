import { render } from '@testing-library/react-native';

import { AppText } from '@/shared/components/AppText';
import { Screen } from '@/shared/components/Screen';

describe('native screen shell', () => {
  it('renders content and footer in the safe area container', async () => {
    const view = await render(
      <Screen footer={<AppText>하단 작업</AppText>}>
        <AppText>화면 내용</AppText>
      </Screen>,
    );

    expect(view.getByText('화면 내용')).toBeTruthy();
    expect(view.getByText('하단 작업')).toBeTruthy();
  });
});
