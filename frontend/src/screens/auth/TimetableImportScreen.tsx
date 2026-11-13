import { useState } from 'react';
import { Image, StyleSheet } from 'react-native';
import { useNavigation, type StaticScreenProps } from '@react-navigation/native';
import { Button, Field, Screen, Spacer } from '../../components/ui';

// 0-6 에브리타임 시간표 가져오기 (150:967): 온보딩 마지막. 안내 이미지 → 링크 → 불러오기 → 나중에 → 홈
// 5-3 에브리타임 시간표 가져오기 (173:1441): 설정·수업 편집에서 들어옴. '나중에' 없고 불러오기는 맨 아래 → 뒤로
export default function TimetableImportScreen({ route }: StaticScreenProps<{ back?: boolean } | undefined>) {
  const navigation = useNavigation();
  const back = !!route.params?.back;
  const [url, setUrl] = useState('');
  const done = () =>
    back ? navigation.goBack() : navigation.reset({ index: 0, routes: [{ name: 'Main' }] });

  return (
    <Screen title="시간표 가져오기" scroll>
      {/* 피그마: 에브리타임 시간표 화면 캡처, 282x568 가운데 */}
      <Image
        source={require('../../assets/images/everytime_guide.png')}
        style={styles.guide}
        resizeMode="cover"
        accessibilityLabel="에브리타임 앱의 시간표 화면 예시"
      />
      <Field
        placeholder="에브리타임 시간표 링크"
        value={url}
        onChangeText={setUrl}
        keyboardType="url"
      />
      {back && <Spacer />}
      {/* ponytail: 에브리타임 파싱 미구현, 서버 붙이면 url 보내기 */}
      <Button title="불러오기" disabled={!url} onPress={done} />
      {!back && <Button title="나중에" ghost onPress={done} />}
    </Screen>
  );
}

const styles = StyleSheet.create({
  guide: { alignSelf: 'center', width: 282, height: 568 },
});
