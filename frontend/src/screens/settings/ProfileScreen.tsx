import { useState } from 'react';
import { Alert, Pressable, StyleSheet, Text, View } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import AvatarIcon from '../../assets/icons/avatar.svg';
import { ApiError } from '../../api/client';
import * as api from '../../api/endpoints';
import { Button, Field, Pill, Screen, Spacer, t } from '../../components/ui';
import { useSession } from '../../session';
import { C } from '../../theme';

// 5-1 프로필(264:615). 이름은 포커스 아웃/저장 시 PUT /users/me. 사진 변경은 1차 제외(서버 저장소 없음).
export default function ProfileScreen() {
  const nav = useNavigation();
  const { me, setMe, logout } = useSession();
  const [name, setName] = useState(me?.name ?? '');
  const [msg, setMsg] = useState('');
  const [busy, setBusy] = useState(false);
  const dirty = name.trim() !== (me?.name ?? '') && !!name.trim();

  const saveName = async () => {
    setBusy(true);
    setMsg('');
    try {
      setMe(await api.updateName(name.trim()));
    } catch (e) {
      setMsg(e instanceof ApiError ? e.message : '저장에 실패했어요.');
    } finally {
      setBusy(false);
    }
  };

  const doLogout = async () => {
    await logout();
    nav.reset({ index: 0, routes: [{ name: 'Login' }] });
  };

  const withdraw = () =>
    Alert.prompt('회원탈퇴', '비밀번호를 입력하면 계정과 모든 기록이 삭제돼요.', [
      { text: '취소', style: 'cancel' },
      {
        text: '탈퇴',
        style: 'destructive',
        onPress: async (pw?: string) => {
          if (!pw) return;
          try {
            await api.deleteMe(pw);
            setMe(null);
            nav.reset({ index: 0, routes: [{ name: 'Login' }] });
          } catch (e) {
            setMsg(e instanceof ApiError ? e.message : '탈퇴에 실패했어요.');
          }
        },
      },
    ], 'secure-text');

  return (
    <Screen title="프로필">
      <View style={st.avatar}>
        <AvatarIcon width={80} height={80} />
        <Pill title="사진 변경" onPress={() => setMsg('사진 변경은 준비 중이에요.')} />
      </View>
      <Field label="이름" value={name} onChangeText={setName} maxLength={30} autoCapitalize="words" />
      <Field label="아이디" value={me?.loginId ?? ''} editable={false} />
      {dirty && <Button title="이름 저장" loading={busy} onPress={saveName} />}
      <Button title="비밀번호 변경" kind="ghost" style={st.outline} onPress={() => nav.navigate('PasswordChange')} />
      {!!msg && <Text style={t.error}>{msg}</Text>}
      <Spacer />
      <View style={st.bottom}>
        <Pressable onPress={withdraw} hitSlop={8} accessibilityRole="button"><Text style={st.bottomText}>회원탈퇴</Text></Pressable>
        <Text style={st.bottomText}>|</Text>
        <Pressable onPress={doLogout} hitSlop={8} accessibilityRole="button"><Text style={st.bottomText}>로그아웃</Text></Pressable>
      </View>
    </Screen>
  );
}

const st = StyleSheet.create({
  avatar: { alignItems: 'center', gap: 8 },
  outline: { borderWidth: 1, borderColor: C.line },
  bottom: { flexDirection: 'row', justifyContent: 'center', gap: 75, paddingVertical: 8 },
  bottomText: { fontSize: 12, color: C.sub },
});
