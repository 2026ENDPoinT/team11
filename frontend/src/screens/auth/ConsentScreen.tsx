import { useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import type { ConsentType } from '../../api/types';
import { Button, Check, Screen, Spacer, t } from '../../components/ui';
import { C } from '../../theme';
import { setConsents } from './onboarding';

export const CONSENT_ITEMS: { type: ConsentType; label: string; required: boolean }[] = [
  { type: 'MEDICAL_DISCLAIMER', label: '[필수] 의료 서비스가 아님을 확인했어요', required: true },
  { type: 'TERMS', label: '[필수] 이용약관 동의', required: true },
  { type: 'PRIVACY', label: '[필수] 개인정보 수집·이용 동의', required: true },
  { type: 'NOTIFICATION', label: '[선택] 알림 수신 동의', required: false },
];

// 0-3 온보딩 1 · 동의(258:86)
export default function ConsentScreen() {
  const nav = useNavigation();
  const [checked, setChecked] = useState<Partial<Record<ConsentType, boolean>>>({});
  const all = CONSENT_ITEMS.every(i => checked[i.type]);
  const ok = CONSENT_ITEMS.every(i => !i.required || checked[i.type]);
  const toggle = (type: ConsentType) => setChecked(c => ({ ...c, [type]: !c[type] }));
  const toggleAll = () => setChecked(Object.fromEntries(CONSENT_ITEMS.map(i => [i.type, !all])));

  const next = () => {
    setConsents(Object.fromEntries(CONSENT_ITEMS.map(i => [i.type, !!checked[i.type]])) as Record<ConsentType, boolean>);
    nav.navigate('SleepProfile');
  };

  return (
    <Screen>
      <Text style={t.step}>1 / 3</Text>
      <Text style={t.h1}>시작하기 전에 확인해 주세요</Text>
      <View style={st.notice}>
        <Text style={st.noticeText}>
          {'수면 코치는 의료 서비스가 아닌, 생활 습관을 돕는 도구예요.\n수면 문제가 계속된다면 전문가와 상담해주세요.'}
        </Text>
      </View>
      <Check label="전체 동의" checked={all} onPress={toggleAll} />
      {CONSENT_ITEMS.map(i => (
        <Check key={i.type} label={i.label} checked={!!checked[i.type]} onPress={() => toggle(i.type)} />
      ))}
      <Spacer />
      <Button title="다음" disabled={!ok} onPress={next} />
    </Screen>
  );
}

const st = StyleSheet.create({
  notice: { borderWidth: 1, borderStyle: 'dashed', borderColor: C.sub, borderRadius: 12, padding: 14 },
  noticeText: { fontSize: 13, color: C.text },
});
