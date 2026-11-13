import { useRef, useState } from 'react';
import { PanResponder, StyleSheet, View } from 'react-native';

// 온보딩 수면 프로필(258:119) 슬라이더: 트랙 26px, 채움 #2E2836, 흰 노브 40px 테두리 2px.
// 네이티브 Slider 패키지 대신 PanResponder 40줄 — 모양이 Figma와 다르고 의존성 하나 아낀다.
const KNOB = 40;
const TRACK = 26;

export function Slider({
  value,
  min,
  max,
  step,
  onChange,
}: {
  value: number;
  min: number;
  max: number;
  step: number;
  onChange: (v: number) => void;
}) {
  const [width, setWidth] = useState(0);
  const widthRef = useRef(0);
  const snap = (x: number) => {
    const ratio = Math.min(1, Math.max(0, x / widthRef.current));
    const raw = min + ratio * (max - min);
    return Math.min(max, Math.max(min, Math.round(raw / step) * step));
  };
  const pan = useRef(
    PanResponder.create({
      onStartShouldSetPanResponder: () => true,
      onMoveShouldSetPanResponder: () => true,
      onPanResponderGrant: e => onChange(snap(e.nativeEvent.locationX)),
      onPanResponderMove: e => onChange(snap(e.nativeEvent.locationX)),
    }),
  ).current;

  const ratio = (value - min) / (max - min);
  const fill = ratio * width;
  const knobLeft = Math.min(Math.max(0, fill - KNOB / 2), Math.max(0, width - KNOB));
  return (
    <View
      style={st.track}
      onLayout={e => {
        widthRef.current = e.nativeEvent.layout.width;
        setWidth(e.nativeEvent.layout.width);
      }}
      accessibilityRole="adjustable"
      accessibilityValue={{ min, max, now: value }}
      {...pan.panHandlers}
    >
      <View style={[st.fill, { width: fill }]} />
      <View style={[st.knob, { left: knobLeft }]} />
    </View>
  );
}

const st = StyleSheet.create({
  track: { height: TRACK, borderRadius: 4, backgroundColor: 'rgba(46,40,54,0.1)', overflow: 'hidden' },
  fill: { position: 'absolute', left: 0, top: 0, height: TRACK, backgroundColor: '#2E2836' },
  knob: { position: 'absolute', top: 0, width: KNOB, height: TRACK, borderRadius: 4, backgroundColor: '#FFFFFF', borderWidth: 2, borderColor: '#2E2836' },
});

// 집중모드 시트(415:2487): 노브 두 개짜리 범위 슬라이더. 가까운 노브가 따라온다.
export function RangeSlider({
  low,
  high,
  min,
  max,
  step,
  onChange,
}: {
  low: number;
  high: number;
  min: number;
  max: number;
  step: number;
  onChange: (low: number, high: number) => void;
}) {
  const [width, setWidth] = useState(0);
  const widthRef = useRef(0);
  const vals = useRef({ low, high });
  vals.current = { low, high };
  const active = useRef<'low' | 'high'>('low');
  const snap = (x: number) => {
    const ratio = Math.min(1, Math.max(0, x / widthRef.current));
    return Math.min(max, Math.max(min, Math.round((min + ratio * (max - min)) / step) * step));
  };
  const move = (x: number) => {
    const v = snap(x);
    const { low: l, high: h } = vals.current;
    if (active.current === 'low') onChange(Math.min(v, h), h);
    else onChange(l, Math.max(v, l));
  };
  const pan = useRef(
    PanResponder.create({
      onStartShouldSetPanResponder: () => true,
      onMoveShouldSetPanResponder: () => true,
      onPanResponderGrant: e => {
        const v = snap(e.nativeEvent.locationX);
        const { low: l, high: h } = vals.current;
        active.current = Math.abs(v - l) <= Math.abs(v - h) ? 'low' : 'high';
        move(e.nativeEvent.locationX);
      },
      onPanResponderMove: e => move(e.nativeEvent.locationX),
    }),
  ).current;

  const px = (v: number) => ((v - min) / (max - min)) * width;
  const clampKnob = (x: number) => Math.min(Math.max(0, x - KNOB / 2), Math.max(0, width - KNOB));
  return (
    <View
      style={st.track}
      onLayout={e => {
        widthRef.current = e.nativeEvent.layout.width;
        setWidth(e.nativeEvent.layout.width);
      }}
      accessibilityRole="adjustable"
      {...pan.panHandlers}
    >
      <View style={[st.fill, { left: px(low), width: px(high) - px(low) }]} />
      <View style={[st.knob, { left: clampKnob(px(low)) }]} />
      <View style={[st.knob, { left: clampKnob(px(high)) }]} />
    </View>
  );
}
