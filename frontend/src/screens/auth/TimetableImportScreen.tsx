import { useState } from 'react';
import { Image, StyleSheet, Text, View } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { ApiError } from '../../api/client';
import * as api from '../../api/endpoints';
import { Button, Field, Screen, t } from '../../components/ui';

// 0-6 에브리타임 시간표 가져오기(258:186) · 5-3 설정에서도 같은 화면(264:669).
// 온보딩 끝에서는 '나중에'가 Main으로, 설정에서는 뒤로.
export default function TimetableImportScreen({ route }: { route?: { params?: { fromSettings?: boolean } } }) {
  const nav = useNavigation();
  const fromSettings = !!route?.params?.fromSettings;
  const [url, setUrl] = useState('');
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState('');
  const [done, setDone] = useState<number | null>(null);

  const finish = () => (fromSettings ? nav.goBack() : nav.reset({ index: 0, routes: [{ name: 'Main' }] }));

  const load = async () => {
    setBusy(true);
    setMsg('');
    try {
      const r = await api.importEverytime(url.trim());
      setDone(r.entries.filter(e => e.source === 'EVERYTIME').length);
    } catch (e) {
      setMsg(e instanceof ApiError ? e.message : '불러오기에 실패했어요.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <Screen title="시간표 가져오기" back={fromSettings}>
      <View style={st.guide}>
        <Image source={require('../../assets/images/everytime_guide.gif')} style={st.guideImg} resizeMode="contain" />
      </View>
      <Text style={t.hint}>에브리타임 앱 → 시간표 → 공유(↗) → 링크 복사. 시간표가 '공개'여야 해요.</Text>
      <Field placeholder="에브리타임 시간표 링크" value={url} onChangeText={setUrl} keyboardType="url" autoCapitalize="none" />
      {done !== null ? (
        <>
          <Text style={t.body}>수업 {done}개를 가져왔어요.</Text>
          <Button title="완료" onPress={finish} />
        </>
      ) : (
        <>
          {!!msg && <Text style={t.error}>{msg}</Text>}
          <Button title="불러오기" onPress={load} loading={busy} disabled={!/^https?:\/\/everytime\.kr\/@/.test(url.trim())} />
          <Button title="나중에" kind="ghost" onPress={finish} />
        </>
      )}
    </Screen>
  );
}

const st = StyleSheet.create({
  guide: { alignItems: 'center' },
  guideImg: { width: 282, height: 568 },
});
