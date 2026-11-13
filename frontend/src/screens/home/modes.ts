import type { PlanMode } from '../../api/types';

// Figma 1-A/1-B/1-C 홈 모드별 색. 그라데이션·강조·시트 색이 모드마다 다르다.
export const MODES: Record<
  PlanMode,
  { label: string; from: string; to: string; accent: string; track: string; sheet: string; done: string; dim: string; band: string }
> = {
  NORMAL: { label: '일상', from: '#F7F3ED', to: '#ECE3D6', accent: '#8E7B66', track: '#E6DCCD', sheet: '#FBF8F3', done: '#5E5044', dim: 'rgba(62,52,43,0.32)', band: '#EFE7DB' },
  FOCUS: { label: '집중', from: '#F3F1FA', to: '#DEDAF0', accent: '#6B5CA5', track: '#E2DEF2', sheet: '#F8F7FC', done: '#463C7A', dim: 'rgba(43,37,80,0.32)', band: '#E9E5F5' },
  EXAM: { label: '시험', from: '#FBF1EF', to: '#F2D8D4', accent: '#B8565A', track: '#EFD9D6', sheet: '#FDF6F5', done: '#7E3A3D', dim: 'rgba(74,35,38,0.32)', band: '#F5E3E1' },
};

export const PENGUIN: Record<PlanMode, number> = {
  NORMAL: require('../../assets/images/penguin_normal.png'),
  FOCUS: require('../../assets/images/penguin_focus.png'),
  EXAM: require('../../assets/images/penguin_exam.png'),
};
