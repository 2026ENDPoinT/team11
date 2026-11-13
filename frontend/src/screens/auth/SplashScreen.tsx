import { useEffect, useState } from 'react';
import { Image, StyleSheet, Text, View } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { C } from '../../components/ui';

// 앱 시작 화면: 로딩페이지(122:867, 로고만) → 랜딩페이지(195:3364, 로고 + 앱 이름) → 0 로그인
// 로고+이름 묶음을 가운데 정렬해서, 이름이 나타나면 로고가 살짝 위로 올라감 (피그마 410 → 390)
const STEP_MS = 1000; // ponytail: 각 화면 보여주는 시간은 임의값. 실제 로딩(자동 로그인 확인 등) 생기면 그걸 기다리기
export default function SplashScreen() {
  const navigation = useNavigation();
  const [landing, setLanding] = useState(false);

  useEffect(() => {
    const a = setTimeout(() => setLanding(true), STEP_MS);
    const b = setTimeout(() => navigation.reset({ index: 0, routes: [{ name: 'Login' }] }), STEP_MS * 2);
    return () => {
      clearTimeout(a);
      clearTimeout(b);
    };
  }, [navigation]);

  return (
    <View style={styles.screen}>
      <Image source={require('../../assets/images/logo.png')} style={styles.logo} accessibilityLabel="수면 코치" />
      {landing && <Text style={styles.name}>수면 코치</Text>}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 10, backgroundColor: '#F7F8FC' },
  logo: { width: 96, height: 96, borderRadius: 24 },
  name: { fontSize: 24, lineHeight: 29, fontWeight: '700', color: C.text },
});
