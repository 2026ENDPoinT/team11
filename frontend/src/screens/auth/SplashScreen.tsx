import { useEffect, useRef, useState } from 'react';
import { Image, StyleSheet, Text, View } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { getAppConfig } from '../../api/endpoints';
import { useSession } from '../../session';
import { Pill, t } from '../../components/ui';
import { C } from '../../theme';
import type { AppConfig } from '../../api/types';

// 서버 feature flag는 앱 시작 때 한 번만 받아서 전역으로 둔다(홈에서 집중/시험 버튼 노출 결정).
export let appConfig: AppConfig['featureFlags'] = { focusMode: false, examMode: false, everytimeImport: true };

// 로딩페이지(258:2) → 랜딩페이지(258:7). Render 콜드 스타트가 ~100초라 /app/config 워밍업이 끝날 때까지 여기서 기다린다.
export default function SplashScreen() {
  const nav = useNavigation();
  const { restore } = useSession();
  const [slow, setSlow] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const attempt = useRef(0);

  const boot = async () => {
    setError(null);
    attempt.current += 1;
    const slowTimer = setTimeout(() => setSlow(true), 8_000);
    try {
      const cfg = await getAppConfig();
      appConfig = cfg.featureFlags;
      const me = await restore();
      clearTimeout(slowTimer);
      if (!me) nav.reset({ index: 0, routes: [{ name: 'Login' }] });
      else if (!me.onboardingDone) nav.reset({ index: 0, routes: [{ name: 'Consent' }] });
      else nav.reset({ index: 0, routes: [{ name: 'Main' }] });
    } catch (e: any) {
      clearTimeout(slowTimer);
      setError(e?.message ?? '서버에 연결할 수 없어요.');
    }
  };

  useEffect(() => {
    boot();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <View style={st.screen}>
      <View style={st.logo}>
        <Image source={require('../../assets/images/logo.png')} style={st.logoImg} />
        <Text style={st.title}>수면 코치</Text>
      </View>
      <View style={st.foot}>
        {error ? (
          <>
            <Text style={t.error}>{error}</Text>
            <Pill title="다시 시도" kind="outline" onPress={boot} />
          </>
        ) : slow ? (
          <Text style={t.hint}>서버를 깨우는 중이에요. 처음엔 1~2분 걸릴 수 있어요.</Text>
        ) : null}
      </View>
    </View>
  );
}

const st = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#F7F8FC', alignItems: 'center', justifyContent: 'center' },
  logo: { alignItems: 'center', gap: 10 },
  logoImg: { width: 96, height: 96, borderRadius: 24 },
  title: { fontSize: 24, lineHeight: 29, fontWeight: '700', color: C.text },
  foot: { position: 'absolute', bottom: 80, alignItems: 'center', gap: 10, paddingHorizontal: 32 },
});
