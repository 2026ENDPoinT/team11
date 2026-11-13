import { Image, PermissionsAndroid, Platform, StyleSheet, Text, View } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { Button, C, Screen, Spacer, t } from '../../components/ui';

// 0-5 온보딩 3 · 알림 허용 (14:171)
export default function NotificationPermissionScreen() {
  const navigation = useNavigation();
  const next = () => navigation.navigate('TimetableImport');

  const allow = async () => {
    // Android 13+ 알림 권한 요청. iOS는 알림 라이브러리 붙일 때 요청
    if (Platform.OS === 'android' && Platform.Version >= 33) {
      await PermissionsAndroid.request(PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS);
    }
    next();
  };

  return (
    <Screen>
      <Text style={t.step}>3 / 3</Text>
      <Spacer />
      <View style={styles.illust}>
        <Image source={require('../../assets/images/notification.png')} style={styles.circle} resizeMode="cover" />
        <Text style={t.h1}>취침·기상 알림을 받아 보세요</Text>
        <Text style={styles.desc}>
          {'권장 취침 30분 전과 카페인 마감 시각에\n알려 드려요.'}
        </Text>
      </View>
      <Spacer />
      <Button title="알림 허용하기" onPress={allow} />
      <Button title="나중에" ghost onPress={next} />
    </Screen>
  );
}

const styles = StyleSheet.create({
  illust: { alignItems: 'center', gap: 16 },
  circle: { width: 160, height: 160, borderRadius: 80 },
  desc: { fontSize: 14, color: C.sub, textAlign: 'center' },
});
