import { useState } from 'react';
import { Alert, Text } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { Button, Field, Screen, Spacer, t } from '../../components/ui';
import { setUser, useStore } from '../../store';

// 5-1-1 비밀번호 변경: 기존 비밀번호가 맞으면 → 5-1-2 새 비밀번호 · 재입력 → 저장 → '변경됐어요' 알림 후 프로필로
export default function PasswordChangeScreen() {
  const navigation = useNavigation();
  const { user } = useStore();
  const [old, setOld] = useState('');
  const [wrong, setWrong] = useState(false);
  const [checked, setChecked] = useState(false); // 기존 비밀번호 확인 통과
  const [pw, setPw] = useState('');
  const [pw2, setPw2] = useState('');

  const error = !checked
    ? wrong
      ? '기존 비밀번호가 틀렸어요.'
      : ''
    : pw && pw.length < 8
    ? '비밀번호는 8자 이상이어야 해요.'
    : pw2 && pw !== pw2
    ? '비밀번호가 서로 달라요.'
    : '';

  const check = () => (old === user.pw ? setChecked(true) : setWrong(true));
  const save = () => {
    setUser({ ...user, pw });
    Alert.alert('비밀번호가 변경됐어요.', undefined, [{ text: '확인', onPress: () => navigation.goBack() }]);
  };

  return (
    <Screen title="비밀번호 변경">
      {checked ? (
        <>
          <Field label="새 비밀번호" placeholder="비밀번호(8자 이상)" value={pw} onChangeText={setPw} secureTextEntry />
          <Field label="새 비밀번호 확인" placeholder="비밀번호 재입력" value={pw2} onChangeText={setPw2} secureTextEntry />
        </>
      ) : (
        <Field
          label="기존 비밀번호"
          placeholder="기존 비밀번호"
          value={old}
          onChangeText={v => {
            setOld(v);
            setWrong(false);
          }}
          secureTextEntry
        />
      )}
      {!!error && <Text style={t.error}>{error}</Text>}
      <Spacer />
      {checked ? (
        <Button title="저장" disabled={!pw || !pw2 || !!error} onPress={save} />
      ) : (
        <Button title="확인" disabled={!old} onPress={check} />
      )}
    </Screen>
  );
}
