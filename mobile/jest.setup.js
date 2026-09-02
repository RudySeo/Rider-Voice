if (typeof global.FormData === 'undefined') {
  global.FormData = class FormData {};
}
process.env.EXPO_PUBLIC_USE_RN_FETCH = '1';
if (typeof global.fetch === 'undefined') {
  global.fetch = async () => { throw new Error('fetch is not mocked'); };
}
