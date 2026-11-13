import { StyleSheet, Text, View } from 'react-native';
import { C, Screen } from '../../components/ui';

// 피그마 '약관 및 동의 관리'(160:822) 본문 그대로. [ ] 는 운영자가 채울 자리
// 줄 앞 표시: '# ' 큰 제목(14) · '- ' 글머리 기호(•) 목록 · 빈 줄은 '' · 나머지는 그냥 줄
// ponytail: 앱에 문구를 박아 둠. 약관이 자주 바뀌면 서버/웹페이지로 빼기
const TERMS = [
  '# 1. 서비스 이용약관',
  '',
  '제1조 (목적)',
  '- 이 약관은 운영자명이 제공하는 수면 코치 앱(이하 "서비스")의 이용 조건을 정합니다.',
  '제2조 (계정)',
  '- 약관과 개인정보 수집·이용에 동의하고 가입하면 회원이 됩니다.',
  '- 계정 관리 책임은 회원에게 있으며, 다른 사람의 정보로 가입하면 이용이 제한될 수 있습니다.',
  '제3조 (유의사항)',
  '- 수면 안내는 참고 정보이며 의학적 진단이나 치료를 대신하지 않습니다. 수면 장애가 의심되면 의료기관과 상담해주세요.',
  '- 알림은 기기 설정이나 네트워크 상태에 따라 변동이 있을 수 있습니다.',
  '제4조 (면책)\n 회원이 잘못 입력한 정보로 생긴 결과, 천재지변이나 통신 장애로 인한 서비스 중단에 대해서는 운영자가 책임지지 않습니다.',
  '부칙 이 약관은 [2026년 11월 13일]부터 시행됩니다.',
  '',
  '# 2. 개인정보 처리방침',
  '',
  '이용 목적',
  '- 로그인, 권장 수면 시간 계산과 알림, 수면 리포트, 이벤트 안내(선택 동의 시)',
  '보관과 파기',
  '- 탈퇴하면 바로 파기합니다.',
  '- 1년간 로그인하지 않을 시 미리 알린 후 [파기 / 분리 보관]합니다.',
  '제공과 위탁\n 법령상 요구가 없는 한 제3자에게 제공하지 않습니다. 데이터 보관은 [업체명]에 위탁합니다.',
  '이용자의 권리\n 설정 화면이나 아래 문의처에서 열람, 수정, 삭제, 처리 정지, 동의 철회를 요청할 수 있습니다.',
  '안전 조치\n 비밀번호는 암호화해 저장하고, 데이터는 HTTPS로 주고받습니다.',
  '보호책임자 [이름] / [이메일]',
  '부칙 이 방침은 [2026년 11월 13일]부터 시행합니다.',
];

// 5-5 약관 및 동의 관리 (160:822)
export default function ConsentManageScreen() {
  return (
    <Screen title="약관 및 동의 관리" scroll>
      <View>
        {TERMS.map((line, i) =>
          line.startsWith('- ') ? (
            // 글머리 기호 줄: 줄이 넘어가도 글자 시작 위치에 맞춰 들여쓰기 유지
            <View key={i} style={styles.bullet}>
              <Text style={styles.text}>•</Text>
              <Text style={[styles.text, styles.flex]}>{line.slice(2)}</Text>
            </View>
          ) : (
            <Text key={i} style={[styles.text, line.startsWith('# ') && styles.title]}>
              {line.replace(/^# /, '') || ' '}
            </Text>
          ),
        )}
      </View>
    </Screen>
  );
}

// 피그마: 본문 12 Bold, 줄간격 20, 자간 0.6 / 큰 제목 14, 자간 0.7
const styles = StyleSheet.create({
  flex: { flex: 1 },
  text: { fontSize: 12, lineHeight: 20, fontWeight: '700', letterSpacing: 0.6, color: C.text },
  title: { fontSize: 14, letterSpacing: 0.7 },
  bullet: { flexDirection: 'row', gap: 6, paddingLeft: 8 },
});
