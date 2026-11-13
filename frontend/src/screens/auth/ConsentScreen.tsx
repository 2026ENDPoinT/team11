import { useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { Button, C, Check, Screen, Spacer, t } from '../../components/ui';

const ITEMS = [
  { key: 'disclaimer', label: '[필수] 의료 서비스가 아님을 확인했어요', required: true },
  { key: 'terms', label: '[필수] 이용약관 동의', required: true },
  { key: 'privacy', label: '[필수] 개인정보 수집·이용 동의', required: true },
  { key: 'marketing', label: '[선택] 알림 수신 동의', required: false },
];

// 0-3 온보딩 1 · 동의 (14:85)
export default function ConsentScreen() {
  const navigation = useNavigation();
  const [checked, setChecked] = useState<Record<string, boolean>>({});
  const all = ITEMS.every(i => checked[i.key]);
  const ok = ITEMS.every(i => !i.required || checked[i.key]);
  const toggle = (key: string) => setChecked(c => ({ ...c, [key]: !c[key] }));
  const toggleAll = () => setChecked(Object.fromEntries(ITEMS.map(i => [i.key, !all])));

  return (
    <Screen>
      <Text style={t.step}>1 / 3</Text>
      <Text style={t.h1}>시작하기 전에 확인해 주세요</Text>
      <View style={styles.notice}>
        <Text style={styles.noticeText}>
          {'수면 코치는 의료 서비스가 아닌, 생활 습관을 돕는 도구예요.\n수면 문제가 계속된다면 전문가와 상담해주세요.'}
        </Text>
      </View>
      <Check label="전체 동의" checked={all} onPress={toggleAll} />
      {ITEMS.map(i => (
        <Check
          key={i.key}
          label={i.label}
          checked={!!checked[i.key]}
          onPress={() => toggle(i.key)}
        />
      ))}
      <Spacer />
      <Button title="다음" disabled={!ok} onPress={() => navigation.navigate('SleepProfile')} />
    </Screen>
  );
}

const styles = StyleSheet.create({
  notice: {
    borderWidth: 1,
    borderStyle: 'dashed',
    borderColor: C.sub,
    borderRadius: 12,
    padding: 14,
  },
  noticeText: { fontSize: 13, color: C.text },
});
