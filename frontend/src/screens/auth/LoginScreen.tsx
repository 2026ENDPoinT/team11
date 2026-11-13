import { useState } from 'react';
import { Image, StyleSheet, Text, View } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { Button, C, Field, Pill, Screen, t } from '../../components/ui';
import { setUser, useStore } from '../../store';

const MAX_FAILS = 5;

// 0 로그인 (14:4) + 0-1 로그인 실패·잠금 (14:30)
export default function LoginScreen() {
  const navigation = useNavigation();
  const { user } = useStore();
  const [id, setId] = useState('');
  const [pw, setPw] = useState('');
  const [fails, setFails] = useState(0);
  const locked = fails >= MAX_FAILS;

  const login = () => {
    // ponytail: 서버가 없어서 형식만 검사. 로그인 API 생기면 여기서 호출
    if (id && pw.length >= 8) {
      setUser({ ...user, id, pw });
      navigation.reset({ index: 0, routes: [{ name: 'Main' }] });
    } else {
      setFails(f => f + 1);
    }
  };

  return (
    <Screen>
      <View style={styles.spacer} />
      <View style={styles.logo}>
        <Image source={require('../../assets/images/logo.png')} style={styles.logoImg} />
        <Text style={styles.title}>수면 코치</Text>
      </View>
      <Field placeholder="아이디" value={id} onChangeText={setId} />
      <Field placeholder="비밀번호" value={pw} onChangeText={setPw} secureTextEntry />
      <Text style={t.error}>
        {locked
          ? '로그인을 5회 실패해 잠겼어요. 잠시 후 다시 시도해 주세요.'
          : fails > 0
          ? `비밀번호가 틀렸어요. (${MAX_FAILS}회 중 ${fails}회 실패)`
          : ' '}
      </Text>
      <Button title="로그인" onPress={login} disabled={locked || !id || !pw} />
      <View style={styles.links}>
        <Text style={styles.linkText}>아직 계정이 없나요?</Text>
        <Pill title="회원가입" onPress={() => navigation.navigate('SignUp')} />
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  spacer: { height: 40 },
  logo: { alignItems: 'center', gap: 10 },
  logoImg: { width: 96, height: 96, borderRadius: 24 },
  title: { fontSize: 24, fontWeight: '700', color: C.text },
  links: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 4 },
  linkText: { fontSize: 13, color: C.sub },
});
