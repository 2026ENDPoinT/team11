import type { ConsentType, OnboardingReq } from '../../api/types';

// 온보딩 3단계 입력은 메모리에만 모았다가 마지막 '완료'에서 POST /users/me/onboarding 한 번.
// 중간에 나가면 아무것도 저장되지 않는 게 의도된 동작(백엔드 규칙).
export const draft: OnboardingReq = {
  consents: [],
  sleepNeedMin: 6 * 60,
  sleepLatencyMin: 10,
  commuteMin: 50,
  prepMin: 40,
  examPrepDays: 2,
  notificationAllowed: false,
};

export const setConsents = (agreed: Record<ConsentType, boolean>) => {
  draft.consents = (Object.keys(agreed) as ConsentType[]).map(type => ({ type, agreed: agreed[type] }));
};
