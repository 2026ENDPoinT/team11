import { Alert, Pressable, StyleSheet, Text, View } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import Avatar from '../../assets/icons/settings/avatar.svg';
import { Button, C, Field, Pill, Screen, Spacer } from '../../components/ui';
import { setUser, useStore } from '../../store';

const soon = () => Alert.alert('준비 중이에요');

// 5-1 프로필 (14:1078)
export default function ProfileScreen() {
  const navigation = useNavigation();
  const { user } = useStore();
  const toLogin = () => navigation.reset({ index: 0, routes: [{ name: 'Login' }] });

  const withdraw = () =>
    Alert.alert('회원탈퇴', '정말 탈퇴할까요? 기록이 모두 삭제돼요.', [
      { text: '취소', style: 'cancel' },
      { text: '탈퇴', style: 'destructive', onPress: toLogin }, // ponytail: 탈퇴 API 연결 필요
    ]);

  return (
    <Screen title="프로필">
      <View style={styles.avatar}>
        <Avatar width={80} height={80} />
        {/* ponytail: 이미지 선택 라이브러리 붙이기 전까지 안내만 */}
        <Pill title="사진 변경" onPress={soon} />
      </View>
      <Field label="이름" value={user.name} onChangeText={name => setUser({ ...user, name })} />
      <Field label="아이디" value={user.id} editable={false} />
      <Button title="비밀번호 변경" style={styles.outline} onPress={() => navigation.navigate('PasswordChange')} ghost />
      <Spacer />
      <View style={styles.bottom}>
        <Pressable onPress={withdraw}>
          <Text style={styles.bottomText}>회원탈퇴</Text>
        </Pressable>
        <Text style={styles.bottomText}>|</Text>
        <Pressable onPress={toLogin}>
          <Text style={styles.bottomText}>로그아웃</Text>
        </Pressable>
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  avatar: { alignItems: 'center', gap: 8 },
  outline: { borderWidth: 1, borderColor: C.line },
  bottom: { flexDirection: 'row', justifyContent: 'center', gap: 75, paddingVertical: 8 },
  bottomText: { fontSize: 12, color: C.sub },
});
