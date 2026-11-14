import { useEffect, useState } from 'react';
import { Alert, Image, Pressable, StyleSheet, Text, View } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { launchImageLibrary } from 'react-native-image-picker';
import AsyncStorage from '@react-native-async-storage/async-storage';
import AvatarIcon from '../../assets/icons/avatar.svg';
import { ApiError } from '../../api/client';
import * as api from '../../api/endpoints';
import { Button, Field, Screen, Spacer, t } from '../../components/ui';
import { useSession } from '../../session';
import { C } from '../../theme';

// 5-1 프로필(264:615). 이름·아이디는 읽기 전용 표시. 사진은 서버 저장소가 없어 기기에만 보관(AsyncStorage).
export const AVATAR_KEY = 'sleepsync.avatarUri';

export default function ProfileScreen() {
  const nav = useNavigation();
  const { me, setMe, logout } = useSession();
  const [msg, setMsg] = useState('');
  const [avatarUri, setAvatarUri] = useState<string | null>(null);

  useEffect(() => {
    AsyncStorage.getItem(AVATAR_KEY).then(setAvatarUri);
  }, []);

  const pickAvatar = async () => {
    const res = await launchImageLibrary({ mediaType: 'photo', quality: 0.8 });
    const uri = res.assets?.[0]?.uri;
    if (!uri) return;
    setAvatarUri(uri);
    await AsyncStorage.setItem(AVATAR_KEY, uri);
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
        <Pressable onPress={pickAvatar} accessibilityRole="button" accessibilityLabel="프로필 사진 변경">
          {avatarUri ? <Image source={{ uri: avatarUri }} style={st.avatarImg} /> : <AvatarIcon width={80} height={80} />}
        </Pressable>
      </View>
      <Field label="이름" value={me?.name ?? ''} editable={false} />
      <Field label="아이디" value={me?.loginId ?? ''} editable={false} />
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
  avatarImg: { width: 80, height: 80, borderRadius: 40 },
  outline: { borderWidth: 1, borderColor: C.line },
  bottom: { flexDirection: 'row', justifyContent: 'center', gap: 75, paddingVertical: 8 },
  bottomText: { fontSize: 12, color: C.sub },
});
