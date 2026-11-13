import { StyleSheet } from 'react-native';
import Svg, { Defs, LinearGradient, Rect, Stop } from 'react-native-svg';

// 홈 배경 세로 그라데이션. react-native-linear-gradient 대신 이미 깔린 react-native-svg로.
export function Gradient({ from, to }: { from: string; to: string }) {
  return (
    <Svg style={StyleSheet.absoluteFill} width="100%" height="100%" preserveAspectRatio="none" viewBox="0 0 1 1">
      <Defs>
        <LinearGradient id="g" x1="0" y1="0" x2="0" y2="1">
          <Stop offset="0" stopColor={from} />
          <Stop offset="1" stopColor={to} />
        </LinearGradient>
      </Defs>
      <Rect x="0" y="0" width="1" height="1" fill="url(#g)" />
    </Svg>
  );
}
