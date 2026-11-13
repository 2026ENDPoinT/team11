import { useEffect, useState } from 'react';
import { Image, StyleSheet, Text, View } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { ApiError } from '../../api/client';
import * as api from '../../api/endpoints';
import { useSession } from '../../session';
import { Button, Field, Pill, Screen, t } from '../../components/ui';
import { C } from '../../theme';

const MAX_FAILS = 5;

// 0 로그인(258:13) + 0-1 로그인 실패·잠금(258:36). 서버가 5회 실패 시 5분 잠금(429 RATE_LIMITED).
export default function LoginScreen() {
  const nav = useNavigation();
  const { setMe } = useSession();
  const [id, setId] = useState('');
  const [pw, setPw] = useState('');
  const [fails, setFails] = useState(0);
  const [lockSec, setLockSec] = useState(0);
  const [msg, setMsg] = useState('');
  const [busy, setBusy] = useState(false);

  // 잠금 카운트다운
  useEffect(() => {
    if (lockSec <= 0) return;
    const timer = setInterval(() => setLockSec(s => s - 1), 1000);
    return () => clearInterval(timer);
  }, [lockSec]);

  const login = async () => {
    setBusy(true);
    setMsg('');
    try {
      const d = await api.login({ loginId: id.trim(), password: pw });
      const me = await api.getMe();
      setMe(me);
      nav.reset({ index: 0, routes: [{ name: d.user.onboardingDone ? 'Main' : 'Consent' }] });
    } catch (e) {
      if (e instanceof ApiError && e.code === 'RATE_LIMITED') {
        setLockSec(e.details?.retryAfterSec ?? 300);
        setFails(MAX_FAILS);
      } else if (e instanceof ApiError && e.code === 'AUTH_INVALID_CREDENTIALS') {
        const n = fails + 1;
        setFails(n);
        setMsg(`비밀번호가 틀렸어요. (${MAX_FAILS}회 중 ${n}회 실패)`);
      } else {
        setMsg(e instanceof ApiError ? e.message : '로그인에 실패했어요.');
      }
    } finally {
      setBusy(false);
    }
  };

  const locked = lockSec > 0;
  return (
    <Screen>
      <View style={st.spacer} />
      <View style={st.logo}>
        <Image source={require('../../assets/images/logo.png')} style={st.logoImg} />
        <Text style={st.title}>수면 코치</Text>
      </View>
      <Field placeholder="아이디" value={id} onChangeText={setId} textContentType="username" />
      <Field placeholder="비밀번호" value={pw} onChangeText={setPw} secureTextEntry textContentType="password" onSubmitEditing={login} />
      <Text style={t.error}>
        {locked
          ? `로그인을 ${MAX_FAILS}회 실패해 잠겼어요. ${Math.ceil(lockSec / 60)}분 후 다시 시도해 주세요.`
          : msg || ' '}
      </Text>
      <Button title="로그인" onPress={login} loading={busy} disabled={locked || !id || !pw} />
      <View style={st.links}>
        <Text style={st.linkText}>아직 계정이 없나요?</Text>
        <Pill title="회원가입" onPress={() => nav.navigate('SignUp')} />
      </View>
    </Screen>
  );
}

const st = StyleSheet.create({
  spacer: { height: 40 },
  logo: { alignItems: 'center', gap: 10 },
  logoImg: { width: 96, height: 96, borderRadius: 24 },
  title: { fontSize: 24, fontWeight: '700', color: C.text },
  links: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 4 },
  linkText: { fontSize: 13, color: C.sub },
});
