import { useState } from 'react';
import { Text } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { Button, Field, Screen, Spacer, t } from '../../components/ui';

// 0-2 회원가입 (14:55)
export default function SignUpScreen() {
  const navigation = useNavigation();
  const [name, setName] = useState('');
  const [id, setId] = useState('');
  const [pw, setPw] = useState('');
  const [pw2, setPw2] = useState('');

  const error =
    id && !/^[A-Za-z0-9]{1,20}$/.test(id)
      ? '아이디는 영문·숫자 최대 20자예요.'
      : pw && pw.length < 8
      ? '비밀번호는 8자 이상이어야 해요.'
      : pw2 && pw !== pw2
      ? '비밀번호가 서로 달라요.'
      : '';
  const ok = name && id && pw && pw2 && !error;

  return (
    <Screen title="회원가입">
      <Field label="이름" placeholder="이름" value={name} onChangeText={setName} />
      <Field
        label="아이디"
        placeholder="아이디(영문·숫자 최대 20자)"
        value={id}
        onChangeText={setId}
        maxLength={20}
      />
      <Field
        label="비밀번호"
        placeholder="비밀번호(8자 이상)"
        value={pw}
        onChangeText={setPw}
        secureTextEntry
      />
      <Field
        label="비밀번호 확인"
        placeholder="비밀번호 재입력"
        value={pw2}
        onChangeText={setPw2}
        secureTextEntry
      />
      {!!error && <Text style={t.error}>{error}</Text>}
      <Spacer />
      <Button title="가입하기" disabled={!ok} onPress={() => navigation.navigate('Consent')} />
    </Screen>
  );
}
