import { useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import * as api from '../../api/endpoints';
import { Check, Screen, Status } from '../../components/ui';
import { C } from '../../theme';
import { useApi } from '../../useApi';
import { CONSENT_ITEMS } from '../auth/ConsentScreen';

// 5-4 약관 및 동의 관리(264:686): 약관 전문 + 동의 현황. 필수 3종은 철회 불가(서버 CONSENT_NOT_REVOCABLE), 선택(알림)만 토글.
export default function ConsentManageScreen() {
  const c = useApi(() => api.getConsents(), []);
  const [msg, setMsg] = useState('');

  const toggle = async (type: 'NOTIFICATION', agreed: boolean) => {
    try {
      c.setData(await api.updateConsents([{ type, agreed }]));
    } catch (e: any) {
      setMsg(e?.message ?? '변경에 실패했어요.');
    }
  };

  return (
    <Screen title="약관 및 동의 관리" scroll>
      {c.error ? (
        <Status error={c.error} onRetry={c.reload} />
      ) : (
        c.data?.consents.map(x => {
          const item = CONSENT_ITEMS.find(i => i.type === x.type);
          return (
            <Check
              key={x.type}
              label={item?.label ?? x.type}
              checked={x.agreed}
              onPress={() => (x.required ? setMsg('필수 동의는 철회할 수 없어요.') : toggle('NOTIFICATION', !x.agreed))}
            />
          );
        })
      )}
      {!!msg && <Text style={st.err}>{msg}</Text>}

      <View style={st.terms}>
        <Text style={st.h}>1. 서비스 이용약관</Text>
        <Text style={st.p}>제1조 (목적)</Text>
        <Text style={st.b}>• 이 약관은 운영자명이 제공하는 수면 코치 앱(이하 "서비스")의 이용 조건을 정합니다.</Text>
        <Text style={st.p}>제2조 (계정)</Text>
        <Text style={st.b}>• 약관과 개인정보 수집·이용에 동의하고 가입하면 회원이 됩니다.</Text>
        <Text style={st.b}>• 계정 관리 책임은 회원에게 있으며, 다른 사람의 정보로 가입하면 이용이 제한될 수 있습니다.</Text>
        <Text style={st.p}>제3조 (유의사항)</Text>
        <Text style={st.b}>• 수면 안내는 참고 정보이며 의학적 진단이나 치료를 대신하지 않습니다. 수면 장애가 의심되면 의료기관과 상담해주세요.</Text>
        <Text style={st.b}>• 알림은 기기 설정이나 네트워크 상태에 따라 변동이 있을 수 있습니다.</Text>
        <Text style={st.p}>제4조 (면책)</Text>
        <Text style={st.p}> 회원이 잘못 입력한 정보로 생긴 결과, 천재지변이나 통신 장애로 인한 서비스 중단에 대해서는 운영자가 책임지지 않습니다.</Text>
        <Text style={st.p}>부칙 이 약관은 [2026년 11월 13일]부터 시행됩니다.</Text>
        <Text style={st.h}>{'\n'}2. 개인정보 처리방침</Text>
        <Text style={st.p}>이용 목적</Text>
        <Text style={st.b}>• 로그인, 권장 수면 시간 계산과 알림, 수면 리포트, 이벤트 안내(선택 동의 시)</Text>
        <Text style={st.p}>보관과 파기</Text>
        <Text style={st.b}>• 탈퇴하면 바로 파기합니다.</Text>
        <Text style={st.b}>• 1년간 로그인하지 않을 시 미리 알린 후 [파기 / 분리 보관]합니다.</Text>
        <Text style={st.p}>제공과 위탁</Text>
        <Text style={st.p}> 법령상 요구가 없는 한 제3자에게 제공하지 않습니다. 데이터 보관은 [업체명]에 위탁합니다.</Text>
        <Text style={st.p}>이용자의 권리</Text>
        <Text style={st.p}> 설정 화면이나 아래 문의처에서 열람, 수정, 삭제, 처리 정지, 동의 철회를 요청할 수 있습니다.</Text>
        <Text style={st.p}>안전 조치</Text>
        <Text style={st.p}> 비밀번호는 암호화해 저장하고, 데이터는 HTTPS로 주고받습니다.</Text>
        <Text style={st.p}>보호책임자 [이름] / [이메일]</Text>
        <Text style={st.p}>부칙 이 방침은 [2026년 11월 13일]부터 시행합니다.</Text>
      </View>
    </Screen>
  );
}

const st = StyleSheet.create({
  err: { fontSize: 13, fontWeight: '700', color: C.error },
  terms: { marginTop: 8 },
  h: { fontSize: 14, lineHeight: 20, fontWeight: '700', letterSpacing: 0.7, color: C.text },
  p: { fontSize: 12, lineHeight: 20, fontWeight: '700', letterSpacing: 0.6, color: C.text },
  b: { fontSize: 12, lineHeight: 20, fontWeight: '700', letterSpacing: 0.6, color: C.text, paddingLeft: 8 },
});
