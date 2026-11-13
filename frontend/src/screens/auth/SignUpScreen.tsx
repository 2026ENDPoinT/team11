import { useState } from 'react';
import { Text } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { ApiError } from '../../api/client';
import * as api from '../../api/endpoints';
import { useSession } from '../../session';
import { Button, Field, Screen, Spacer, t } from '../../components/ui';

// 0-2 회원가입(258:59). 가입 → 바로 로그인 → 온보딩(동의)으로.
// 서버 규칙: loginId ^[a-z0-9]{4,20}$, password 8~64자 영문+숫자, name 1~30자
export default function SignUpScreen() {
  const nav = useNavigation();
  const { setMe } = useSession();
  const [name, setName] = useState('');
  const [id, setId] = useState('');
  const [pw, setPw] = useState('');
  const [pw2, setPw2] = useState('');
  const [serverMsg, setServerMsg] = useState('');
  const [busy, setBusy] = useState(false);

  const error =
    id && !/^[a-z0-9]{4,20}$/.test(id)
      ? '아이디는 영문 소문자·숫자 4~20자예요.'
      : pw && !(pw.length >= 8 && /[a-zA-Z]/.test(pw) && /\d/.test(pw))
      ? '비밀번호는 8자 이상, 영문과 숫자를 포함해야 해요.'
      : pw2 && pw !== pw2
      ? '비밀번호가 서로 달라요.'
      : '';
  const ok = !!(name.trim() && id && pw && pw2 && !error);

  const submit = async () => {
    setBusy(true);
    setServerMsg('');
    try {
      await api.signup({ name: name.trim(), loginId: id, password: pw });
      await api.login({ loginId: id, password: pw });
      setMe(await api.getMe());
      nav.reset({ index: 0, routes: [{ name: 'Consent' }] });
    } catch (e) {
      setServerMsg(e instanceof ApiError ? e.message : '가입에 실패했어요.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <Screen title="회원가입">
      <Field label="이름" placeholder="이름" value={name} onChangeText={setName} maxLength={30} autoCapitalize="words" />
      <Field label="아이디" placeholder="아이디(영문 소문자·숫자 4~20자)" value={id} onChangeText={v => setId(v.toLowerCase())} maxLength={20} />
      <Field label="비밀번호" placeholder="비밀번호(8자 이상, 영문+숫자)" value={pw} onChangeText={setPw} secureTextEntry textContentType="newPassword" />
      <Field label="비밀번호 확인" placeholder="비밀번호 재입력" value={pw2} onChangeText={setPw2} secureTextEntry />
      {!!(error || serverMsg) && <Text style={t.error}>{error || serverMsg}</Text>}
      <Spacer />
      <Button title="가입하기" disabled={!ok} loading={busy} onPress={submit} />
    </Screen>
  );
}
