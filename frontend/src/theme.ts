// Figma '컴포넌트 (앱 ui.tsx 기준)' 섹션(257:302)에서 뽑은 토큰. variables는 비어 있어서 hex 직접.
export const C = {
  bg: '#FFFFFF',
  text: '#212121',
  sub: '#737373',
  line: '#D1D1D1',
  primary: '#383838',
  disabled: '#D1D1D1',
  error: '#BF3333',
  ink: '#2A2C3E', // 홈·상태바 진한 남색
  inkSoft: '#5A5D75',
  inkFaint: '#727489',
  hairline: 'rgba(42,44,62,0.08)',
  track: '#EFE8E3',
  range: '#5C5F86',
  dim: 'rgba(31,33,48,0.38)',
} as const;

// 화면 공통 여백: Figma body padding 16, 항목 간격 12
export const S = { pad: 16, gap: 12, radius: 10, pill: 16 } as const;
