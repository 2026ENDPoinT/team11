import { useState } from 'react';
import { Alert, Text } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { ApiError, getTokens } from '../../api/client';
import * as api from '../../api/endpoints';
import { Button, Field, Screen, Spacer, t } from '../../components/ui';

// 5-1-1 기존 확인 → 5-1-2 새 비밀번호 → 5-1-3 완료 알림. 한 화면에서 단계만 바꾼다.
// 기존 비밀번호 검증 API가 따로 없어서 1단계는 입력만 받고 최종 PUT에서 USER_PASSWORD_MISMATCH로 걸러진다.
export default function PasswordChangeScreen() {
  const nav = useNavigation();
  const [step, setStep] = useState<1 | 2>(1);
  const [cur, setCur] = useState('');
  const [pw, setPw] = useState('');
  const [pw2, setPw2] = useState('');
  const [msg, setMsg] = useState('');
  const [busy, setBusy] = useState(false);

  const error =
    pw && !(pw.length >= 8 && /[a-zA-Z]/.test(pw) && /\d/.test(pw))
      ? '비밀번호는 8자 이상, 영문과 숫자를 포함해야 해요.'
      : pw2 && pw !== pw2
      ? '비밀번호가 서로 달라요.'
      : pw && pw === cur
      ? '현재 비밀번호와 달라야 해요.'
      : '';

  const save = async () => {
    setBusy(true);
    setMsg('');
    try {
      // refreshToken을 같이 보내면 이 기기는 로그인 유지
      await api.changePassword({ currentPassword: cur, newPassword: pw, refreshToken: getTokens()?.refreshToken });
      Alert.alert('', '비밀번호가 변경됐어요.', [{ text: '확인', onPress: () => nav.goBack() }]);
    } catch (e) {
      if (e instanceof ApiError && e.code === 'USER_PASSWORD_MISMATCH') {
        setStep(1);
        setMsg('기존 비밀번호가 틀렸어요.');
      } else setMsg(e instanceof ApiError ? e.message : '변경에 실패했어요.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <Screen title="비밀번호 변경">
      {step === 1 ? (
        <>
          <Field label="기존 비밀번호" placeholder="기존 비밀번호" value={cur} onChangeText={setCur} secureTextEntry textContentType="password" />
          {!!msg && <Text style={t.error}>{msg}</Text>}
          <Spacer />
          <Button title="확인" disabled={!cur} onPress={() => { setMsg(''); setStep(2); }} />
        </>
      ) : (
        <>
          <Field label="새 비밀번호" placeholder="비밀번호(8자 이상)" value={pw} onChangeText={setPw} secureTextEntry textContentType="newPassword" />
          <Field label="새 비밀번호 확인" placeholder="비밀번호 재입력" value={pw2} onChangeText={setPw2} secureTextEntry />
          {!!(error || msg) && <Text style={t.error}>{error || msg}</Text>}
          <Spacer />
          <Button title="저장" disabled={!pw || !pw2 || !!error} loading={busy} onPress={save} />
        </>
      )}
    </Screen>
  );
}
